// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

use crate::image_cache::ImageSession;
use crate::mach::{
  Suspended,
  Threads,
  Timebase,
  mach_absolute_time,
  mach_timebase_info,
  metadata,
  registers,
  stack_region,
  thread_resume,
};
use crate::{
  CancellationToken,
  CaptureError,
  Observation,
  Profile,
  ProfileConfig,
  ProfileSample,
  Snapshot,
  ThreadSnapshot,
  ThreadStatus,
};
use bd_unwind::aarch64::{
  Frame,
  FrameKind,
  Registers,
  strip_instruction_pointer,
  walk_frame_pointers,
};
use bd_unwind::apple::TaskMemory;
use bd_unwind::image::ImageError;
use bd_unwind::memory::{MemoryReader, StackMemory};
use std::collections::BTreeMap;
use std::mem::size_of;
use std::time::{Duration, Instant};

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
    main_thread_id: None,
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

pub(super) fn profile(
  budget: usize,
  config: ProfileConfig,
  cancellation: &CancellationToken,
) -> Result<Profile, CaptureError> {
  let scratch_budget = budget / 4;
  let frame_budget = budget / 2 - scratch_budget;
  let mut collector = {
    let threads = Threads::new()?;
    Collector::new(scratch_budget, threads.current)?
  };
  let mut frames = filled_buffer(
    (frame_budget / size_of::<Frame>()).min(MAX_FRAMES_PER_THREAD),
    Frame::default(),
  )?;
  let mut profile = Profile::new(config, budget - budget / 2)?;
  // Build/subscribe before starting the clock. Loader callbacks update an owned bounded history;
  // image enumeration, parsing and cache locks are absent from the periodic sampling loop.
  let image_start = unsafe { mach_absolute_time() };
  let images = ImageSession::start(profile.image_capacity)?;
  profile.timing.image_preparation_ns = collector
    .timebase
    .nanos(unsafe { mach_absolute_time() } - image_start);
  cancellation.check()?;
  let start = Instant::now();
  collector.origin = unsafe { mach_absolute_time() };
  let mut tick = 0;
  log::debug!(
    "starting stack profile: {:?} interval, {:?} duration",
    config.interval,
    config.duration
  );
  while tick < config.tick_count() {
    let elapsed = start.elapsed();
    if elapsed >= config.duration {
      break;
    }
    // Compute in u128: a long request can contain more than u32::MAX sampling ticks.
    let scheduled = Duration::from_nanos(
      u64::try_from(config.interval.as_nanos() * u128::from(tick)).unwrap_or(u64::MAX),
    );
    cancellation.wait(start + scheduled)?;
    if start.elapsed() >= config.duration {
      break;
    }
    let round_start = unsafe { mach_absolute_time() };
    let unwind_start = collector.unwind_ns;
    collector.sample(cancellation, &mut frames, |thread| {
      profile.record(ProfileSample::new(&thread), thread.frames);
    })?;
    let sample_end = unsafe { mach_absolute_time() };
    let unwind_ns = collector.unwind_ns - unwind_start;
    profile.timing.unwind.record(unwind_ns);
    profile.timing.capture.record(
      collector
        .timebase
        .nanos(sample_end - round_start)
        .saturating_sub(unwind_ns),
    );
    profile.summary.capture_rounds += 1;
    let round_end = unsafe { mach_absolute_time() };
    profile
      .timing
      .round_work
      .record(collector.timebase.nanos(round_end - round_start));
    tick = config.next_tick(start.elapsed());
  }
  cancellation.check()?;
  let image_end = unsafe { mach_absolute_time() };
  let images = images.finish();
  profile.images = images.images;
  profile.summary.failed_image_reads = images.errors;
  profile.summary.dropped_images = images.dropped;
  profile.timing.image_finalization_ns = collector
    .timebase
    .nanos(unsafe { mach_absolute_time() } - image_end);
  profile.summary.missed_ticks = config
    .tick_count()
    .saturating_sub(profile.summary.capture_rounds);
  profile.ensure_useful()?;
  Ok(profile)
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
  mut visit: impl FnMut(Result<crate::Image, ImageError>),
) -> Result<(), CaptureError> {
  cancellation.check()?;
  ImageSession::survey(|image| {
    if !cancellation.is_cancelled() {
      visit(image);
    }
  });
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
  unwind_ns: u64,
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
      unwind_ns: 0,
    })
  }

  fn sample(
    &mut self,
    cancellation: &CancellationToken,
    frames: &mut [Frame],
    visit: impl FnMut(Observation<'_>),
  ) -> Result<(), CaptureError> {
    cancellation.check()?;
    let threads = Threads::new()?;
    self.sample_threads(
      threads.ports(),
      threads.current,
      cancellation,
      frames,
      visit,
    )
  }

  fn sample_threads(
    &mut self,
    ports: &[u32],
    current: u32,
    cancellation: &CancellationToken,
    frames: &mut [Frame],
    mut visit: impl FnMut(Observation<'_>),
  ) -> Result<(), CaptureError> {
    if frames.is_empty() {
      return Err(CaptureError::InvalidBufferSize);
    }
    for port in ports {
      cancellation.check()?;
      let (native_id, name) = metadata(*port);
      let result = if *port == current {
        ThreadCapture::skipped()
      } else {
        // The visitor copies this stack into the retained ring before the next capture, so every
        // thread can use the whole frame buffer regardless of how many threads were enumerated.
        self.capture_thread(*port, cancellation, frames)?
      };
      visit(Observation {
        native_id,
        name: &name,
        frames: &frames[.. result.frame_count],
        status: result.status,
        stack_truncated: result.stack_truncated,
        timestamp_ns: result.timestamp_ns,
      });
    }
    cancellation.check()
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
        // Measure walking after resume, outside the suspended-thread section.
        let unwind_start = unsafe { mach_absolute_time() };
        let trace = walk_frame_pointers(
          &StackMemory {
            address: state.sp,
            bytes: &self.scratch[.. copied],
          },
          &state,
          region,
          frames,
        );
        self.unwind_ns += self
          .timebase
          .nanos(unsafe { mach_absolute_time() } - unwind_start);
        result.frame_count = trace.frame_count;
        result.status = ThreadStatus::Captured(trace.stop_reason);
      },
      (state, _) => result.set_unavailable(state.as_ref(), frames),
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
  // Classify only the returned context, not the thread's scheduler state: blocked threads with
  // valid registers still have useful stacks. This runs after resume, like the normal walk.
  fn set_unavailable(&mut self, state: Option<&Registers>, frames: &mut [Frame]) {
    self.status = match state {
      None => ThreadStatus::RegistersUnavailable,
      Some(state) => {
        let pc = strip_instruction_pointer(state.pc);
        if pc == 0 && state.sp == 0 {
          ThreadStatus::NoUserContext
        } else {
          // Preserve a usable sampled PC even when its stack cannot be read. Zero is not a frame.
          if pc != 0 {
            frames[0] = Frame {
              address: pc,
              kind: FrameKind::ProgramCounter,
            };
            self.frame_count = 1;
          }
          ThreadStatus::StackUnavailable
        }
      },
    };
  }

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
