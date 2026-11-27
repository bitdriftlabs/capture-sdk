// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

use crate::mach::{
  _dyld_get_image_header,
  _dyld_image_count,
  Suspended,
  Threads,
  Timebase,
  getpid,
  mach_absolute_time,
  mach_timebase_info,
  metadata,
  registers,
  stack_region,
  thread_resume,
};
use crate::{CancellationToken, CaptureError, Snapshot, ThreadSnapshot, ThreadStatus};
use bd_unwind::aarch64::{Frame, FrameKind, strip_instruction_pointer, walk_frame_pointers};
use bd_unwind::apple::TaskMemory;
use bd_unwind::image::{ImageError, ImageInfo};
use bd_unwind::memory::{MemoryReader, StackMemory};
use std::collections::BTreeMap;
use std::mem::size_of;

#[cfg(test)]
#[path = "./apple_test.rs"]
mod tests;

// These capture limits bound copy time and retained frame storage independently of the command's
// budget. The scratch buffer is reused across threads; longer stacks retain their partial trace.
const MAX_STACK_COPY_BYTES: usize = 64 * 1024;
const MAX_FRAMES_PER_THREAD: usize = 256;

pub(super) fn capture(
  budget: usize,
  cancellation: &CancellationToken,
) -> Result<Snapshot, CaptureError> {
  cancellation.check()?;
  let threads = Threads::new()?;
  let frame_capacity =
    (budget / 2 / size_of::<Frame>() / threads.ports().len().max(1)).min(MAX_FRAMES_PER_THREAD);
  if frame_capacity == 0 {
    return Err(CaptureError::InvalidBufferSize);
  }
  let mut collector = Collector::new(budget / 2, threads.current)?;
  let mut captured_threads = Vec::new();
  captured_threads
    .try_reserve_exact(threads.ports().len())
    .map_err(|_| CaptureError::AllocationFailed)?;
  let mut snapshot = Snapshot {
    pid: unsafe { getpid() }.unsigned_abs(),
    threads: captured_threads,
    images: Vec::new(),
    image_errors: BTreeMap::new(),
  };
  for port in threads.ports() {
    cancellation.check()?;
    let (native_id, name) = metadata(*port);
    let mut frames = if *port == threads.current {
      Vec::new()
    } else {
      filled_buffer(frame_capacity, Frame::default())?
    };
    let result = if *port == threads.current {
      ThreadCapture::skipped()
    } else {
      collector.capture_thread(*port, cancellation, &mut frames)?
    };
    frames.truncate(result.frame_count);
    snapshot.threads.push(ThreadSnapshot {
      native_id,
      name,
      frames,
      status: result.status,
      stack_truncated: result.stack_truncated,
      copied_stack_bytes: result.copied_stack_bytes,
      timestamp_ns: result.timestamp_ns,
      suspension_ns: result.suspension_ns,
    });
  }
  // Survey images after all target threads are resumed; retain only owned image metadata.
  survey_images(cancellation, |image| snapshot.record_image(image))?;
  log::debug!(
    "stack snapshot image survey: {} accepted, rejected by reason: {:?}",
    snapshot.images.len(),
    snapshot.image_errors
  );
  snapshot.ensure_useful()?;
  Ok(snapshot)
}

fn filled_buffer<T: Clone>(length: usize, value: T) -> Result<Vec<T>, CaptureError> {
  let mut buffer = Vec::new();
  buffer
    .try_reserve_exact(length)
    .map_err(|_| CaptureError::AllocationFailed)?;
  buffer.resize(length, value);
  Ok(buffer)
}

fn survey_images(
  cancellation: &CancellationToken,
  mut visit: impl FnMut(Result<ImageInfo, ImageError>),
) -> Result<(), CaptureError> {
  for index in 0 .. unsafe { _dyld_image_count() } {
    cancellation.check()?;
    let address = unsafe { _dyld_get_image_header(index) } as u64;
    visit(ImageInfo::read(&TaskMemory, address));
  }
  cancellation.check()
}

//
// Collector
//

/// Owns preflighted scratch storage and the capture clock. Walking runs only after resume; no
/// reference to a target's live stack escapes its suspension.
struct Collector {
  scratch: Vec<u8>,
  timebase: Timebase,
  origin: u64,
}

impl Collector {
  fn new(stack_bytes: usize, current_thread: u32) -> Result<Self, CaptureError> {
    if stack_bytes == 0 {
      return Err(CaptureError::InvalidBufferSize);
    }
    let scratch = filled_buffer(stack_bytes.min(MAX_STACK_COPY_BYTES), 0)?;
    // Resolve lazy Mach bindings while all target threads are running.
    let mut byte = [0];
    TaskMemory.read(scratch.as_ptr() as u64, &mut byte);
    let _ = registers(current_thread);
    let _ = stack_region(scratch.as_ptr() as u64);
    // An invalid port resolves the resume path without changing any thread's suspend count.
    unsafe {
      thread_resume(0);
    }
    let mut timebase = Timebase {
      numerator: 1,
      denominator: 1,
    };
    let status = unsafe { mach_timebase_info(&raw mut timebase) };
    if status != 0 || timebase.denominator == 0 {
      return Err(CaptureError::ThreadEnumerationFailed);
    }
    Ok(Self {
      scratch,
      timebase,
      origin: unsafe { mach_absolute_time() },
    })
  }

  fn capture_thread(
    &mut self,
    port: u32,
    cancellation: &CancellationToken,
    frames: &mut [Frame],
  ) -> Result<ThreadCapture, CaptureError> {
    let mut result = ThreadCapture::skipped();
    let Some(suspended) = Suspended::new(port) else {
      result.status = ThreadStatus::SuspendFailed;
      result.timestamp_ns = self
        .timebase
        .nanos(unsafe { mach_absolute_time() } - self.origin);
      return Ok(result);
    };
    let start = unsafe { mach_absolute_time() };
    // Only Mach calls and existing buffer writes occur until resume. Cancellation, walking,
    // metadata, logging and allocation all run with the target resumed.
    let state = registers(port);
    let region = state.as_ref().and_then(|state| stack_region(state.sp));
    let copied = if let (Some(state), Some(region)) = (&state, region) {
      let length = usize::try_from(region.start() + region.size() - state.sp)
        .unwrap_or(usize::MAX)
        .min(self.scratch.len());
      if TaskMemory.read(state.sp, &mut self.scratch[.. length]) {
        length
      } else {
        0
      }
    } else {
      0
    };
    suspended.resume()?;
    let end = unsafe { mach_absolute_time() };
    cancellation.check()?;
    result.timestamp_ns = self.timebase.nanos(start - self.origin);
    result.suspension_ns = self.timebase.nanos(end - start);
    result.copied_stack_bytes = copied;
    match (state, region) {
      (Some(state), Some(region)) if copied != 0 => {
        result.stack_truncated = (copied as u64) < region.start() + region.size() - state.sp;
        let trace = walk_frame_pointers(
          &StackMemory {
            address: state.sp,
            bytes: &self.scratch[.. copied],
          },
          &state,
          region,
          frames,
        );
        result.frame_count = trace.frame_count;
        result.status = ThreadStatus::Captured(trace.stop_reason);
      },
      (None, _) => {
        result.status = ThreadStatus::RegistersUnavailable;
      },
      (Some(state), _) => {
        result.status = ThreadStatus::StackUnavailable;
        frames[0] = Frame {
          address: strip_instruction_pointer(state.pc),
          kind: FrameKind::ProgramCounter,
        };
        result.frame_count = 1;
      },
    }
    Ok(result)
  }
}

//
// ThreadCapture
//

struct ThreadCapture {
  status: ThreadStatus,
  frame_count: usize,
  stack_truncated: bool,
  copied_stack_bytes: usize,
  timestamp_ns: u64,
  suspension_ns: u64,
}

impl ThreadCapture {
  const fn skipped() -> Self {
    Self {
      status: ThreadStatus::CaptureThread,
      frame_count: 0,
      stack_truncated: false,
      copied_stack_bytes: 0,
      timestamp_ns: 0,
      suspension_ns: 0,
    }
  }
}
