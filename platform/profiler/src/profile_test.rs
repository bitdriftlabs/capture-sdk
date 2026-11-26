// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

#![allow(clippy::unwrap_used)]
use super::{Profile, ProfileConfig, ProfileSample};
use crate::{CancellationToken, CaptureError, Observation, ThreadStatus, write_profile};
use bd_proto::protos::profiling::stack_profile::StackProfile;
use bd_unwind::aarch64::{Frame, FrameKind, StopReason};
use protobuf::Message;
use std::time::{Duration, Instant};

fn record(profile: &mut Profile, tid: u64, time: u64, count: usize) {
  let mut frames = vec![
    Frame {
      address: time + 100,
      kind: FrameKind::ReturnAddress
    };
    count
  ];
  if let Some(first) = frames.first_mut() {
    first.kind = FrameKind::ProgramCounter;
  }
  let observation = Observation {
    native_id: tid,
    name: "worker",
    frames: &frames,
    status: ThreadStatus::Captured(StopReason::EndOfStack),
    stack_truncated: false,
    timestamp_ns: time,
  };
  profile.record(ProfileSample::new(&observation), &frames);
}

#[test]
fn validates_frequency_and_duration_without_clamping() {
  for (frequency, duration) in [(0, 100), (10, 0), (1_000_000_001, 1)] {
    assert_eq!(
      ProfileConfig::new(frequency, duration).unwrap_err(),
      CaptureError::InvalidProfileConfig
    );
  }
  assert!(ProfileConfig::new(1, u32::MAX).is_ok());
}

#[test]
fn slow_captures_skip_expired_ticks_and_do_not_drift() {
  let config = ProfileConfig::new(100, 51).unwrap();
  assert_eq!(config.tick_count(), 6);
  assert_eq!(config.next_tick(Duration::from_millis(4)), 1);
  assert_eq!(config.next_tick(Duration::from_millis(10)), 2);
  assert_eq!(config.next_tick(Duration::from_millis(37)), 4);
  assert_eq!(config.next_tick(Duration::from_secs(100)), 6);
}

#[test]
fn storage_evicts_whole_old_samples_and_stays_within_capacity() {
  let mut profile = Profile::new(ProfileConfig::new(100, 100).unwrap(), 4096).unwrap();
  for i in 0 .. 100 {
    record(&mut profile, 7, i, 3);
  }
  assert!(profile.samples.len() <= profile.sample_capacity);
  assert!(profile.frames.len() <= profile.frame_capacity);
  assert_eq!(
    profile.frames.len(),
    profile.samples.iter().map(|s| s.frame_count).sum::<usize>()
  );
  assert_eq!(profile.samples.back().unwrap().timestamp_ns, 99);
  assert_eq!(
    profile.summary.retained_samples + profile.summary.dropped_samples,
    100
  );
}

#[test]
fn export_preserves_thread_identity_sample_times_and_independent_stacks() {
  let mut profile = Profile::new(ProfileConfig::new(100, 100).unwrap(), 8192).unwrap();
  record(&mut profile, 17, 1_000_000, 2);
  record(&mut profile, 29, 2_000_000, 1);
  record(&mut profile, 17, 11_000_000, 3);
  let mut bytes = Vec::new();
  write_profile(&profile, &mut bytes, &CancellationToken::default()).unwrap();
  let decoded = StackProfile::parse_from_bytes(&bytes).unwrap();
  assert_eq!(decoded.interval_ns, 10_000_000);
  let first = decoded.threads[0].samples.as_ref().unwrap();
  assert_eq!(first.timestamp_ns, [1_000_000, 11_000_000]);
  assert_eq!(
    decoded.stacks[first.stack_indices[0] as usize]
      .frame_indices
      .len(),
    2
  );
  assert_eq!(
    decoded.stacks[first.stack_indices[1] as usize]
      .frame_indices
      .len(),
    3
  );
  assert_eq!(
    decoded.threads[1].samples.as_ref().unwrap().timestamp_ns,
    [2_000_000]
  );
}

#[test]
fn cancellation_wakes_a_parked_worker_without_waiting_for_the_tick() {
  let token = std::sync::Arc::new(CancellationToken::default());
  let worker_token = token.clone();
  let (tx, rx) = std::sync::mpsc::channel();
  let worker = std::thread::spawn(move || {
    tx.send(()).unwrap();
    worker_token.wait(Instant::now() + Duration::from_secs(30))
  });
  rx.recv().unwrap();
  token.cancel();
  assert_eq!(worker.join().unwrap(), Err(CaptureError::Cancelled));
}

#[test]
fn absent_user_context_is_retained_without_frames_and_counted_separately() {
  let mut profile = Profile::new(ProfileConfig::new(100, 100).unwrap(), 4096).unwrap();
  record(&mut profile, 7, 1_000_000, 1);
  for (tid, status) in [
    (7, ThreadStatus::NoUserContext),
    (9, ThreadStatus::StackUnavailable),
  ] {
    let observation = Observation {
      native_id: tid,
      name: "waiting",
      status,
      frames: &[],
      stack_truncated: false,
      timestamp_ns: 2_000_000,
    };
    profile.record(ProfileSample::new(&observation), &[]);
  }
  assert_eq!(profile.summary.captured_samples, 1);
  assert_eq!(profile.summary.failed_samples, 1);
  assert_eq!(profile.summary.no_user_context_samples, 1);
  assert_eq!(profile.summary.skipped_samples, 0);
  assert_eq!(profile.summary.retained_samples, 3);
  assert_eq!(profile.frames.len(), 1);
  let mut output = Vec::new();
  write_profile(&profile, &mut output, &CancellationToken::default()).unwrap();
  let decoded = StackProfile::parse_from_bytes(&output).unwrap();
  assert_eq!(decoded.stacks[0].frame_indices.len(), 0);
  assert_eq!(
    decoded.threads[0].samples.as_ref().unwrap().stack_indices,
    [1, 0]
  );
  assert_eq!(
    decoded.threads[1].samples.as_ref().unwrap().stack_indices,
    [0]
  );
  // Evict zero-frame observations and regular stacks together without desynchronizing the rings.
  for i in 0 .. 100 {
    record(&mut profile, 7, i + 3_000_000, 1);
  }
  assert_eq!(profile.summary.no_user_context_samples, 1);
  assert_eq!(
    profile.frames.len(),
    profile.samples.iter().map(|s| s.frame_count).sum::<usize>()
  );
  assert_eq!(
    profile.summary.retained_samples + profile.summary.dropped_samples,
    103
  );
}

#[cfg(all(target_vendor = "apple", target_arch = "aarch64"))]
#[test]
fn profiles_real_threads_with_reused_scratch_and_one_time_origin() {
  let _capture = crate::tests::CAPTURE_TEST_LOCK.lock().unwrap();
  let cancellation = CancellationToken::default();
  let mut profiler = crate::Profiler::new(256, &cancellation).unwrap();
  let profile = profiler
    .profile(ProfileConfig::new(20, 500).unwrap(), &cancellation)
    .unwrap();
  assert!(profile.summary.capture_rounds > 1);
  assert!(profile.summary.captured_samples > 0);
  assert!(
    profile
      .samples
      .iter()
      .zip(profile.samples.iter().skip(1))
      .all(|(first, second)| first.timestamp_ns <= second.timestamp_ns)
  );
  assert_eq!(
    profile.summary.capture_rounds + profile.summary.missed_ticks,
    10
  );
  let mut output = Vec::new();
  write_profile(&profile, &mut output, &cancellation).unwrap();
  let decoded = StackProfile::parse_from_bytes(&output).unwrap();
  let round_work = profile.timing.round_work.total_ns;
  assert!(round_work > 0);
  let mut stage_total = 0;
  for timing in [&profile.timing.capture, &profile.timing.unwind] {
    assert!(timing.total_ns > 0 && timing.max_ns > 0 && timing.max_ns <= timing.total_ns);
    stage_total += timing.total_ns;
  }
  assert!(stage_total <= round_work);
  assert!(profile.timing.image_preparation_ns > 0);
  assert_ne!(profile.images.len(), 0);
  assert!(
    decoded
      .threads
      .iter()
      .any(|thread| thread.samples.as_ref().unwrap().timestamp_ns.len() > 1)
  );
  if let Some(path) = std::env::var_os("BD_PROFILE_TEST_OUTPUT") {
    std::fs::write(path, output).unwrap();
  }
}

#[test]
fn oversized_observation_does_not_evict_the_retained_window() {
  let mut profile = Profile::new(ProfileConfig::new(10, 500).unwrap(), 4096).unwrap();
  record(&mut profile, 1, 10, 3);
  let capacity = profile.frame_capacity;
  record(&mut profile, 2, 20, capacity + 1);
  assert_eq!(profile.samples.len(), 1);
  assert_eq!(profile.samples.front().unwrap().native_id, 1);
  assert_eq!(profile.summary.retained_samples, 1);
  assert_eq!(profile.summary.dropped_samples, 1);
}

#[test]
fn profile_names_main_by_native_identity_not_thread_order_or_name() {
  let mut profile = Profile::new(ProfileConfig::new(100, 100).unwrap(), 8192).unwrap();
  record(&mut profile, 17, 1_000_000, 2);
  record(&mut profile, 29, 2_000_000, 1);
  for main_thread_id in [None, Some(29)] {
    profile.main_thread_id = main_thread_id;
    let mut bytes = Vec::new();
    write_profile(&profile, &mut bytes, &CancellationToken::default()).unwrap();
    let decoded = StackProfile::parse_from_bytes(&bytes).unwrap();
    assert_eq!(decoded.threads[0].thread.as_ref().unwrap().name, "worker");
    assert_eq!(
      decoded.threads[1].thread.as_ref().unwrap().name,
      if main_thread_id.is_some() {
        "Main Thread"
      } else {
        "worker"
      }
    );
  }
}
