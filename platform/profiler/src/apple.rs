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
use bd_unwind::aarch64::{Frame, FrameKind, strip_instruction_pointer};
use bd_unwind::apple::TaskMemory;
use bd_unwind::cursor;
use bd_unwind::image::ImageInfo;
use bd_unwind::memory::{MemoryReader, StackMemory};
use std::collections::BTreeMap;
use std::mem::size_of;

pub(super) fn capture(
  budget: usize,
  cancellation: &CancellationToken,
) -> Result<Snapshot, CaptureError> {
  cancellation.check()?;
  let threads = Threads::new()?;
  let count = threads.ports().len();
  let frame_capacity = budget / 2 / size_of::<Frame>() / count.max(1);
  if frame_capacity == 0 {
    return Err(CaptureError::InvalidBufferSize);
  }
  let mut scratch = vec![0; budget / 2];
  let mut snapshots = Vec::with_capacity(count);
  for port in threads.ports() {
    cancellation.check()?;
    let (native_id, name) = metadata(*port);
    snapshots.push(ThreadSnapshot {
      native_id,
      name,
      status: ThreadStatus::CaptureThread,
      frames: vec![Frame::default(); frame_capacity],
      stack_truncated: false,
      copied_stack_bytes: 0,
      timestamp_ns: 0,
      suspension_ns: 0,
    });
  }
  // Preflight the Mach reader before stopping any thread, including lazy symbol binding.
  let mut byte = [0];
  TaskMemory.read(scratch.as_ptr() as u64, &mut byte);
  let _ = registers(threads.current);
  let _ = stack_region(scratch.as_ptr() as u64);
  // Resolve the resume path with an invalid port before another thread is stopped.
  unsafe {
    thread_resume(0);
  }
  let mut timebase = Timebase {
    numerator: 1,
    denominator: 1,
  };
  unsafe {
    mach_timebase_info(&raw mut timebase);
  }
  if timebase.denominator == 0 {
    return Err(CaptureError::ThreadEnumerationFailed);
  }
  // Survey owned image metadata during normal execution, before suspending any target. Table
  // lookups also run after resume and use fault-tolerant Mach reads, never raw mapped pointers.
  let mut snapshot = Snapshot {
    pid: unsafe { getpid() }.unsigned_abs(),
    threads: Vec::new(),
    images: Vec::new(),
    image_errors: BTreeMap::new(),
  };
  for index in 0 .. unsafe { _dyld_image_count() } {
    cancellation.check()?;
    let address = unsafe { _dyld_get_image_header(index) } as u64;
    snapshot.record_image(ImageInfo::read(&TaskMemory, address));
  }
  let origin = unsafe { mach_absolute_time() };
  for (port, thread) in threads.ports().iter().zip(&mut snapshots) {
    cancellation.check()?;
    if *port == threads.current {
      thread.frames.clear();
      continue;
    }
    let Some(suspended) = Suspended::new(*port) else {
      thread.status = ThreadStatus::SuspendFailed;
      thread.frames.clear();
      continue;
    };
    let start = unsafe { mach_absolute_time() };
    // Only Mach calls and operations on existing stack/buffer storage occur before resume.
    let state = registers(*port);
    let region = state.as_ref().and_then(|state| stack_region(state.sp));
    let copied = if let (Some(state), Some(region)) = (&state, region) {
      let available = region.start() + region.size() - state.sp;
      let length = usize::try_from(available)
        .unwrap_or(usize::MAX)
        .min(scratch.len());
      if TaskMemory.read(state.sp, &mut scratch[.. length]) {
        length
      } else {
        0
      }
    } else {
      0
    };
    suspended.resume()?;
    // Cancellation cannot bypass resume or run inside the suspended-thread section above.
    cancellation.check()?;
    let end = unsafe { mach_absolute_time() };
    thread.timestamp_ns = timebase.nanos(start - origin);
    thread.suspension_ns = timebase.nanos(end - start);
    thread.copied_stack_bytes = copied;
    match (state, region) {
      (Some(state), Some(region)) if copied != 0 => {
        thread.stack_truncated = (copied as u64) < region.start() + region.size() - state.sp;
        let trace = cursor::walk(
          &StackMemory {
            address: state.sp,
            bytes: &scratch[.. copied],
          },
          &TaskMemory,
          &snapshot.images,
          &state,
          region,
          &mut thread.frames,
        );
        thread.frames.truncate(trace.frame_count);
        thread.status = ThreadStatus::Captured(trace.stop_reason);
      },
      (None, _) => {
        thread.status = ThreadStatus::RegistersUnavailable;
        thread.frames.clear();
      },
      (Some(state), _) => {
        thread.status = ThreadStatus::StackUnavailable;
        thread.frames[0] = Frame {
          address: strip_instruction_pointer(state.pc),
          kind: FrameKind::ProgramCounter,
        };
        thread.frames.truncate(1);
      },
    }
  }
  snapshot.threads = snapshots;
  cancellation.check()?;
  log::debug!(
    "stack snapshot image survey: {} accepted, rejected by reason: {:?}",
    snapshot.images.len(),
    snapshot.image_errors
  );
  snapshot.ensure_useful()?;
  Ok(snapshot)
}
