// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

#[cfg(test)]
#[path = "./profile_test.rs"]
mod tests;

#[cfg(any(test, all(target_vendor = "apple", target_arch = "aarch64")))]
use crate::Observation;
use crate::{CancellationToken, CaptureError, Profiler, ThreadStatus};
use bd_unwind::aarch64::Frame;
#[cfg(any(test, all(target_vendor = "apple", target_arch = "aarch64")))]
use bd_unwind::aarch64::StopReason;
use bd_unwind::image::ImageInfo;
use std::collections::VecDeque;
#[cfg(any(test, all(target_vendor = "apple", target_arch = "aarch64")))]
use std::mem::size_of;
use std::time::Duration;

//
// ProfileConfig
//

#[derive(Clone, Copy, Debug)]
pub struct ProfileConfig {
  pub(crate) interval: Duration,
  pub(crate) duration: Duration,
}

impl ProfileConfig {
  pub fn new(frequency_hz: u32, duration_ms: u32) -> Result<Self, CaptureError> {
    if frequency_hz == 0 || frequency_hz > 1_000_000_000 || duration_ms == 0 {
      return Err(CaptureError::InvalidProfileConfig);
    }
    Ok(Self {
      interval: Duration::from_nanos(1_000_000_000 / u64::from(frequency_hz)),
      duration: Duration::from_millis(u64::from(duration_ms)),
    })
  }

  #[cfg(any(test, all(target_vendor = "apple", target_arch = "aarch64")))]
  pub(crate) fn tick_count(self) -> u64 {
    u64::try_from((self.duration.as_nanos() - 1) / self.interval.as_nanos() + 1).unwrap_or(u64::MAX)
  }

  // The next tick is strictly after the completed capture. Never catch up with a burst.
  #[cfg(any(test, all(target_vendor = "apple", target_arch = "aarch64")))]
  pub(crate) fn next_tick(self, elapsed: Duration) -> u64 {
    u64::try_from(elapsed.as_nanos() / self.interval.as_nanos() + 1)
      .unwrap_or(u64::MAX)
      .min(self.tick_count())
  }
}

//
// ProfileSample
//

/// Names use the Mach thread-name capacity and are copied only on UTF-8 boundaries. Retained
/// samples own no per-sample allocations; their frame counts partition Profile's frame ring.
#[derive(Clone)]
pub(crate) struct ProfileSample {
  pub native_id: u64,
  name: [u8; 64],
  name_len: usize,
  pub status: ThreadStatus,
  pub stack_truncated: bool,
  pub copied_stack_bytes: usize,
  pub timestamp_ns: u64,
  pub suspension_ns: u64,
  pub frame_count: usize,
}

impl ProfileSample {
  #[cfg(any(test, all(target_vendor = "apple", target_arch = "aarch64")))]
  pub(crate) fn new(thread: &Observation<'_>) -> Self {
    let mut name = [0; 64];
    let mut name_len = thread.name.len().min(name.len());
    while !thread.name.is_char_boundary(name_len) {
      name_len -= 1;
    }
    name[.. name_len].copy_from_slice(&thread.name.as_bytes()[.. name_len]);
    Self {
      native_id: thread.native_id,
      name,
      name_len,
      status: thread.status,
      stack_truncated: thread.stack_truncated,
      copied_stack_bytes: thread.copied_stack_bytes,
      timestamp_ns: thread.timestamp_ns,
      suspension_ns: thread.suspension_ns,
      frame_count: thread.frames.len(),
    }
  }

  pub(crate) fn name(&self) -> &str {
    // new copies a prefix of a valid UTF-8 name at a character boundary.
    std::str::from_utf8(&self.name[.. self.name_len]).unwrap_or("")
  }
}

//
// ProfileSummary
//

/// Counts include observations later evicted. The retained count describes the exported window;
/// dropped samples count ring evictions, and missed ticks count unattempted rounds.
/// Absent user contexts are counted separately from captured, failed and skipped observations.
#[repr(C)]
#[derive(Clone, Copy, Debug, Default, Eq, PartialEq)]
pub struct ProfileSummary {
  pub capture_rounds: u64,
  pub captured_samples: u64,
  pub failed_samples: u64,
  pub truncated_samples: u64,
  pub skipped_samples: u64,
  pub retained_samples: u64,
  pub dropped_samples: u64,
  pub missed_ticks: u64,
  pub dropped_images: u64,
  pub failed_image_reads: u64,
  pub no_user_context_samples: u64,
}

//
// StageTiming
//

#[derive(Default)]
pub(crate) struct StageTiming {
  pub(crate) total_ns: u64,
  pub(crate) max_ns: u64,
}

impl StageTiming {
  #[cfg(all(target_vendor = "apple", target_arch = "aarch64"))]
  pub(crate) fn record(&mut self, nanos: u64) {
    self.total_ns = self.total_ns.saturating_add(nanos);
    self.max_ns = self.max_ns.max(nanos);
  }
}

//
// ProfileTiming
//

/// Wall-clock work across all completed rounds, including observations evicted from the ring.
/// Capture includes thread enumeration/metadata, suspension/copy/resume and recording samples,
/// excluding unwinding. Round work excludes scheduler waits, preparation and JSON export.
#[derive(Default)]
pub(crate) struct ProfileTiming {
  pub(crate) capture: StageTiming,
  pub(crate) unwind: StageTiming,
  pub(crate) round_work: StageTiming,
  /// Image cache initialization and request history copy, outside the sampling interval. Later
  /// load callbacks run on the loader's thread, not the sampling worker, and are not timed here.
  pub(crate) image_preparation_ns: u64,
  /// Detaching the request history after sampling, outside round work.
  pub(crate) image_finalization_ns: u64,
}

//
// Profile
//

pub struct Profile {
  pub(crate) config: ProfileConfig,
  pub(crate) pid: u32,
  pub(crate) samples: VecDeque<ProfileSample>,
  pub(crate) frames: VecDeque<Frame>,
  pub(crate) images: Vec<ImageInfo>,
  pub(crate) summary: ProfileSummary,
  pub(crate) timing: ProfileTiming,
  #[cfg(any(test, all(target_vendor = "apple", target_arch = "aarch64")))]
  sample_capacity: usize,
  #[cfg(any(test, all(target_vendor = "apple", target_arch = "aarch64")))]
  frame_capacity: usize,
  #[cfg(all(target_vendor = "apple", target_arch = "aarch64"))]
  pub(crate) image_capacity: usize,
}

impl Profile {
  #[cfg(any(test, all(target_vendor = "apple", target_arch = "aarch64")))]
  pub(crate) fn new(config: ProfileConfig, pid: u32, budget: usize) -> Result<Self, CaptureError> {
    // The collector receives half the command buffer. This half bounds retained observations
    // (including names), frames and image identities, independently of duration and frequency.
    let sample_capacity = budget / 4 / size_of::<ProfileSample>();
    let image_capacity = budget / 4 / size_of::<ImageInfo>();
    let frame_capacity = (budget
      - sample_capacity * size_of::<ProfileSample>()
      - image_capacity * size_of::<ImageInfo>())
      / size_of::<Frame>();
    if sample_capacity == 0 || image_capacity == 0 || frame_capacity == 0 {
      return Err(CaptureError::InvalidBufferSize);
    }
    Ok(Self {
      config,
      pid,
      samples: VecDeque::with_capacity(sample_capacity),
      frames: VecDeque::with_capacity(frame_capacity),
      // The image session allocates this budget and transfers its vector here after capture.
      images: Vec::new(),
      summary: ProfileSummary::default(),
      timing: ProfileTiming::default(),
      sample_capacity,
      frame_capacity,
      #[cfg(all(target_vendor = "apple", target_arch = "aarch64"))]
      image_capacity,
    })
  }

  #[cfg(any(test, all(target_vendor = "apple", target_arch = "aarch64")))]
  pub(crate) fn record(&mut self, sample: ProfileSample, frames: &[Frame]) {
    if sample.status == ThreadStatus::CaptureThread {
      self.summary.skipped_samples += 1;
      return;
    }
    if sample.status == ThreadStatus::NoUserContext {
      self.summary.no_user_context_samples += 1;
    } else if frames.iter().any(|frame| frame.address != 0) {
      self.summary.captured_samples += 1;
      if sample.stack_truncated || sample.status != ThreadStatus::Captured(StopReason::EndOfStack) {
        self.summary.truncated_samples += 1;
      }
    } else {
      self.summary.failed_samples += 1;
    }
    if frames.len() > self.frame_capacity {
      self.summary.dropped_samples += 1;
      return;
    }
    while self.samples.len() >= self.sample_capacity
      || self.frames.len() + frames.len() > self.frame_capacity
    {
      let Some(evicted) = self.samples.pop_front() else {
        self.summary.dropped_samples += 1;
        return;
      };
      self.frames.drain(.. evicted.frame_count);
      self.summary.dropped_samples += 1;
    }
    self.frames.extend(frames.iter().copied());
    self.samples.push_back(sample);
    self.summary.retained_samples = self.samples.len() as u64;
  }

  #[must_use]
  pub const fn summary(&self) -> ProfileSummary {
    self.summary
  }

  #[cfg(all(target_vendor = "apple", target_arch = "aarch64"))]
  pub(crate) fn ensure_useful(&self) -> Result<(), CaptureError> {
    if self.frames.iter().any(|frame| frame.address != 0) {
      Ok(())
    } else {
      Err(CaptureError::NoUsefulStacks)
    }
  }
}

impl Profiler {
  /// Samples all threads, including blocked threads: this measures wall time, not CPU usage.
  /// Sampling uses one collector and one monotonic origin. The command's buffer
  /// bounds reusable stack/frame scratch plus the retained rings. Temporary thread enumeration,
  /// metadata and encoded output are outside that budget, as for snapshots.
  pub fn profile(
    &mut self,
    config: ProfileConfig,
    cancellation: &CancellationToken,
  ) -> Result<Profile, CaptureError> {
    cancellation.check()?;
    #[cfg(all(target_vendor = "apple", target_arch = "aarch64"))]
    {
      crate::apple::profile(self.buffer_size_kb as usize * 1024, config, cancellation)
    }
    #[cfg(not(all(target_vendor = "apple", target_arch = "aarch64")))]
    {
      let _ = config;
      Err(CaptureError::UnsupportedArchitecture)
    }
  }
}
