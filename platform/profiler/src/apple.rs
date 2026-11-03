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
use crate::{CaptureError, Snapshot, ThreadSnapshot, ThreadStatus};
use bd_unwind::aarch64::{Frame, FrameKind, strip_instruction_pointer, walk_frame_pointers};
use bd_unwind::apple::TaskMemory;
use bd_unwind::image::ImageInfo;
use bd_unwind::memory::{MemoryReader, StackMemory};
use std::mem::size_of;

pub(super) fn capture(budget: usize) -> Result<Snapshot, CaptureError> {
  let threads = Threads::new()?;
  let count = threads.ports().len();
  let frame_capacity = budget / 2 / size_of::<Frame>() / count.max(1);
  if frame_capacity == 0 {
    return Err(CaptureError::InvalidBufferSize);
  }
  let mut scratch = vec![0; budget / 2];
  let mut snapshots = Vec::with_capacity(count);
  for port in threads.ports() {
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
  let origin = unsafe { mach_absolute_time() };
  for (port, snapshot) in threads.ports().iter().zip(&mut snapshots) {
    if *port == threads.current {
      snapshot.frames.clear();
      continue;
    }
    let Some(suspended) = Suspended::new(*port) else {
      snapshot.status = ThreadStatus::SuspendFailed;
      snapshot.frames.clear();
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
    let end = unsafe { mach_absolute_time() };
    snapshot.timestamp_ns = timebase.nanos(start - origin);
    snapshot.suspension_ns = timebase.nanos(end - start);
    snapshot.copied_stack_bytes = copied;
    match (state, region) {
      (Some(state), Some(region)) if copied != 0 => {
        snapshot.stack_truncated = (copied as u64) < region.start() + region.size() - state.sp;
        let trace = walk_frame_pointers(
          &StackMemory {
            address: state.sp,
            bytes: &scratch[.. copied],
          },
          &state,
          region,
          &mut snapshot.frames,
        );
        snapshot.frames.truncate(trace.frame_count);
        snapshot.status = ThreadStatus::Captured(trace.stop_reason);
      },
      (None, _) => {
        snapshot.status = ThreadStatus::RegistersUnavailable;
        snapshot.frames.clear();
      },
      (Some(state), _) => {
        snapshot.status = ThreadStatus::StackUnavailable;
        snapshot.frames[0] = Frame {
          address: strip_instruction_pointer(state.pc),
          kind: FrameKind::ProgramCounter,
        };
        snapshot.frames.truncate(1);
      },
    }
  }
  // Read image metadata after all threads have resumed. Fault-tolerant reads handle images that
  // disappear during enumeration; no borrowed dyld strings or image pointers enter the snapshot.
  let images = (0 .. unsafe { _dyld_image_count() })
    .filter_map(|index| {
      let address = unsafe { _dyld_get_image_header(index) } as u64;
      ImageInfo::read(&TaskMemory, address).ok()
    })
    .collect();
  Ok(Snapshot {
    pid: unsafe { getpid() }.unsigned_abs(),
    threads: snapshots,
    images,
  })
}
