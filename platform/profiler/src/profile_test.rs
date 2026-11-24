// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

#![allow(clippy::unwrap_used)]
use super::{Profile, ProfileConfig, ProfileSample};
use crate::{CancellationToken, CaptureError, Observation, ThreadStatus, write_profile_json};
use bd_unwind::aarch64::{Frame, FrameKind, StopReason};
use serde_json::Value;
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
    copied_stack_bytes: 32,
    timestamp_ns: time,
    suspension_ns: 1,
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
  let mut profile = Profile::new(ProfileConfig::new(100, 100).unwrap(), 1, 4096).unwrap();
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
  let mut profile = Profile::new(ProfileConfig::new(100, 100).unwrap(), 1, 8192).unwrap();
  record(&mut profile, 17, 1_000_000, 2);
  record(&mut profile, 29, 2_000_000, 1);
  record(&mut profile, 17, 11_000_000, 3);
  let mut bytes = Vec::new();
  write_profile_json(
    &profile,
    &mut bytes,
    usize::MAX,
    &CancellationToken::default(),
  )
  .unwrap();
  let value: Value = serde_json::from_slice(&bytes).unwrap();
  let thread = &value["threads"][0];
  assert_eq!(thread["bitdrift"]["nativeThreadId"], "17");
  assert_eq!(
    thread["samples"]["data"],
    serde_json::json!([[1, 1.0], [4, 11.0]])
  );
  assert_eq!(
    thread["stackTable"]["data"][2],
    serde_json::json!([null, 2])
  );
  assert_eq!(
    value["threads"][1]["samples"]["data"],
    serde_json::json!([[0, 2.0]])
  );
  assert_eq!(value["bitdrift"]["capture"], "profile");
  assert_eq!(
    thread["bitdrift"]["observations"][0]["frames"][0]["kind"],
    "pc"
  );
  let mut exact = Vec::new();
  write_profile_json(
    &profile,
    &mut exact,
    bytes.len(),
    &CancellationToken::default(),
  )
  .unwrap();
  assert!(
    write_profile_json(
      &profile,
      &mut Vec::new(),
      bytes.len() - 1,
      &CancellationToken::default()
    )
    .is_err()
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
  let mut profile = Profile::new(ProfileConfig::new(100, 100).unwrap(), 1, 4096).unwrap();
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
      copied_stack_bytes: 0,
      timestamp_ns: 2_000_000,
      suspension_ns: 1,
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
  write_profile_json(
    &profile,
    &mut output,
    usize::MAX,
    &CancellationToken::default(),
  )
  .unwrap();
  let json: Value = serde_json::from_slice(&output).unwrap();
  assert_eq!(json["bitdrift"]["noUserContextSamples"], 1);
  assert_eq!(json["bitdrift"]["failedSamples"], 1);
  assert_eq!(
    json["threads"][0]["bitdrift"]["observations"][1]["status"],
    "no_user_context"
  );
  assert_eq!(
    json["threads"][0]["bitdrift"]["observations"][1]["frames"],
    serde_json::json!([])
  );
  assert_eq!(
    json["threads"][0]["samples"]["data"],
    serde_json::json!([[0, 1.0]])
  );
  assert_eq!(json["threads"][1]["samples"]["data"], serde_json::json!([]));
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
  write_profile_json(&profile, &mut output, 5 * 1024 * 1024, &cancellation).unwrap();
  let json: Value = serde_json::from_slice(&output).unwrap();
  let timing = &json["bitdrift"]["timing"];
  let round_work = timing["roundWork"]["totalNs"].as_u64().unwrap();
  assert!(round_work > 0);
  let mut stage_total = 0;
  for stage in ["capture", "unwind"] {
    let total = timing[stage]["totalNs"].as_u64().unwrap();
    let max = timing[stage]["maxNs"].as_u64().unwrap();
    assert!(total > 0 && max > 0 && max <= total);
    stage_total += total;
  }
  assert!(stage_total <= round_work);
  assert!(json["bitdrift"]["imagePreparationNs"].as_u64().unwrap() > 0);
  assert_ne!(profile.images.len(), 0);
  assert!(
    json["threads"]
      .as_array()
      .unwrap()
      .iter()
      .any(|thread| thread["samples"]["data"].as_array().unwrap().len() > 1)
  );
  if let Some(path) = std::env::var_os("BD_PROFILE_TEST_OUTPUT") {
    std::fs::write(path, output).unwrap();
  }
}

#[test]
fn oversized_observation_does_not_evict_the_retained_window() {
  let mut profile = Profile::new(ProfileConfig::new(10, 500).unwrap(), 1, 4096).unwrap();
  record(&mut profile, 1, 10, 3);
  let capacity = profile.frame_capacity;
  record(&mut profile, 2, 20, capacity + 1);
  assert_eq!(profile.samples.len(), 1);
  assert_eq!(profile.samples.front().unwrap().native_id, 1);
  assert_eq!(profile.summary.retained_samples, 1);
  assert_eq!(profile.summary.dropped_samples, 1);
}
