// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt
#![allow(clippy::unwrap_used)]

use super::{
  CancellationToken,
  CaptureError,
  Profiler,
  Snapshot,
  ThreadSnapshot,
  ThreadStatus,
  write_json,
};
use bd_unwind::aarch64::{Frame, FrameKind, StopReason};
use bd_unwind::image::{ImageError, ImageInfo};
use bd_unwind::memory::AddressRange;
use serde_json::Value;
use std::collections::BTreeMap;

#[test]
fn only_one_profiler_can_run_and_dropping_it_releases_the_slot() {
  let guard = Profiler::new(256, &CancellationToken::default()).unwrap();
  assert!(matches!(
    Profiler::new(256, &CancellationToken::default()),
    Err(CaptureError::AlreadyCapturing)
  ));
  let rejected = std::thread::spawn(|| {
    matches!(
      Profiler::new(256, &CancellationToken::default()),
      Err(CaptureError::AlreadyCapturing)
    )
  })
  .join()
  .unwrap();
  assert!(rejected);
  let mut output = Vec::new();
  write_json(
    &sample_snapshot(),
    &mut output,
    usize::MAX,
    &CancellationToken::default(),
  )
  .unwrap();
  assert!(matches!(
    Profiler::new(256, &CancellationToken::default()),
    Err(CaptureError::AlreadyCapturing)
  ));
  drop(guard);
  assert!(Profiler::new(256, &CancellationToken::default()).is_ok());
  // Validate accepted budgets without allocating their buffers.
  for size in [1, 15, 16385, u32::MAX] {
    assert!(Profiler::new(size, &CancellationToken::default()).is_ok());
  }
  #[cfg(all(target_vendor = "apple", target_arch = "aarch64"))]
  captures_native_worker_and_resumes_it();
}

#[test]
fn rejects_invalid_capture_budgets() {
  assert!(matches!(
    capture(0, &CancellationToken::default()),
    Err(CaptureError::InvalidBufferSize)
  ));
}

#[test]
fn exports_root_to_leaf_stacks_without_losing_raw_pc_kinds_or_large_addresses() {
  let snapshot = sample_snapshot();
  let mut bytes = Vec::new();
  write_json(
    &snapshot,
    &mut bytes,
    usize::MAX,
    &CancellationToken::default(),
  )
  .unwrap();
  let value: Value = serde_json::from_slice(&bytes).unwrap();
  let thread = &value["threads"][0];
  assert_eq!(thread["name"], snapshot.threads[0].name);
  assert_eq!(thread["stringTable"][0], "0x1000");
  assert_eq!(thread["stringTable"][1], "0xffffffffffffffff");
  assert_eq!(
    thread["stackTable"]["data"],
    serde_json::json!([[null, 0], [0, 1]])
  );
  assert_eq!(
    thread["samples"]["data"],
    serde_json::json!([[1, 1.234_567]])
  );
  assert_eq!(thread["bitdrift"]["frames"][0]["kind"], "pc");
  assert_eq!(thread["bitdrift"]["nativeThreadId"], u64::MAX.to_string());
}

#[test]
fn skipped_threads_have_no_fabricated_samples() {
  let snapshot = Snapshot {
    pid: 1,
    images: vec![],
    image_errors: BTreeMap::new(),
    threads: vec![ThreadSnapshot {
      native_id: 1,
      name: String::new(),
      status: ThreadStatus::CaptureThread,
      frames: vec![],
      stack_truncated: false,
      copied_stack_bytes: 0,
      timestamp_ns: 0,
      suspension_ns: 0,
    }],
  };
  let mut bytes = Vec::new();
  write_json(
    &snapshot,
    &mut bytes,
    usize::MAX,
    &CancellationToken::default(),
  )
  .unwrap();
  let value: Value = serde_json::from_slice(&bytes).unwrap();
  assert_eq!(
    value["threads"][0]["samples"]["data"],
    serde_json::json!([])
  );
}

#[cfg(all(target_vendor = "apple", target_arch = "aarch64"))]
fn captures_native_worker_and_resumes_it() {
  use std::sync::Arc;
  use std::sync::atomic::{AtomicBool, Ordering};
  use std::time::{Duration, Instant};

  let running = Arc::new(AtomicBool::new(true));
  let progressed = Arc::new(AtomicBool::new(false));
  let worker_running = running.clone();
  let worker_progressed = progressed.clone();
  let worker = std::thread::Builder::new()
    .name("snapshot-worker".into())
    .spawn(move || {
      while worker_running.load(Ordering::Relaxed) {
        worker_progressed.store(true, Ordering::Release);
        std::hint::spin_loop();
      }
    })
    .unwrap();
  while !progressed.load(Ordering::Acquire) {
    std::thread::yield_now();
  }
  let result = capture(256, &CancellationToken::default());
  progressed.store(false, Ordering::Release);
  let deadline = Instant::now() + Duration::from_secs(2);
  while !progressed.load(Ordering::Acquire) && Instant::now() < deadline {
    std::thread::yield_now();
  }
  running.store(false, Ordering::Relaxed);
  assert!(
    progressed.load(Ordering::Acquire),
    "worker must run after capture"
  );
  worker.join().unwrap();
  let snapshot = result.unwrap();
  let thread = snapshot
    .threads
    .iter()
    .find(|thread| thread.name == "snapshot-worker")
    .unwrap();
  assert!(matches!(thread.status, ThreadStatus::Captured(_)));
  assert!(!thread.frames.is_empty());
  assert!(thread.copied_stack_bytes > 0);
  assert!(
    snapshot
      .threads
      .iter()
      .any(|thread| thread.status == ThreadStatus::CaptureThread)
  );
  assert!(!snapshot.images.is_empty());
  if let Ok(path) = std::env::var("BD_SNAPSHOT_TEST_OUTPUT") {
    write_json(
      &snapshot,
      &mut std::fs::File::create(path).unwrap(),
      usize::MAX,
      &CancellationToken::default(),
    )
    .unwrap();
  }
}

pub(crate) fn sample_snapshot() -> Snapshot {
  Snapshot {
    pid: 42,
    images: vec![],
    image_errors: BTreeMap::new(),
    threads: vec![ThreadSnapshot {
      native_id: u64::MAX,
      name: "worker\"\\\n\u{0}🦀".into(),
      status: ThreadStatus::Captured(StopReason::OutputFull),
      frames: vec![
        Frame {
          address: u64::MAX,
          kind: FrameKind::ProgramCounter,
        },
        Frame {
          address: 0x1000,
          kind: FrameKind::ReturnAddress,
        },
      ],
      stack_truncated: true,
      copied_stack_bytes: 8192,
      timestamp_ns: 1_234_567,
      suspension_ns: 900,
    }],
  }
}

#[test]
fn cancellation_before_capture_never_acquires_the_capture_guard() {
  let cancellation = CancellationToken::default();
  cancellation.cancel();
  assert!(matches!(
    capture(256, &cancellation),
    Err(CaptureError::Cancelled)
  ));
}

#[test]
fn summary_keeps_partial_stacks_and_distinguishes_failed_and_skipped_threads() {
  let mut snapshot = sample_snapshot();
  let mut failed = sample_snapshot().threads.remove(0);
  failed.frames.clear();
  failed.status = ThreadStatus::SuspendFailed;
  let mut skipped = sample_snapshot().threads.remove(0);
  skipped.frames.clear();
  skipped.status = ThreadStatus::CaptureThread;
  snapshot.threads.extend([failed, skipped]);
  let summary = snapshot.summary();
  assert_eq!(summary.captured_threads, 1);
  assert_eq!(summary.truncated_threads, 1);
  assert_eq!(summary.failed_threads, 1);
  assert_eq!(summary.skipped_threads, 1);
  assert!(snapshot.ensure_useful().is_ok());
  snapshot.threads.remove(0);
  assert_eq!(snapshot.ensure_useful(), Err(CaptureError::NoUsefulStacks));
}

fn capture(
  buffer_size_kb: u32,
  cancellation: &CancellationToken,
) -> Result<Snapshot, CaptureError> {
  Profiler::new(buffer_size_kb, cancellation)?.capture(cancellation)
}

#[test]
fn image_survey_preserves_valid_images_and_groups_rejection_reasons() {
  let mut snapshot = sample_snapshot();
  let image = ImageInfo {
    header_address: 0x1000,
    uuid: Some([7; 16]),
    text: AddressRange::new(0x1000, 0x1000).unwrap(),
    unwind_info: None,
    eh_frame: None,
  };
  snapshot.record_image(Ok(image));
  let errors = [
    ImageError::UnreadableMemory,
    ImageError::InvalidHeader,
    ImageError::UnsupportedArchitecture,
    ImageError::InvalidLoadCommand,
    ImageError::NonzeroTextFileOffset {
      in_shared_cache: false,
    },
    ImageError::NonzeroTextFileOffset {
      in_shared_cache: true,
    },
    ImageError::NonzeroTextFileOffset {
      in_shared_cache: true,
    },
    ImageError::InvalidSection,
    ImageError::MissingTextSegment,
    ImageError::AddressOverflow,
  ];
  for error in errors {
    snapshot.record_image(Err(error));
  }
  assert_eq!(snapshot.images, [image]);
  let mut bytes = Vec::new();
  write_json(
    &snapshot,
    &mut bytes,
    usize::MAX,
    &CancellationToken::default(),
  )
  .unwrap();
  let value: Value = serde_json::from_slice(&bytes).unwrap();
  let survey = &value["bitdrift"]["imageSurvey"];
  assert_eq!(
    survey,
    &serde_json::json!({
      "enumerated": 11, "accepted": 1, "rejected": 10,
      "errors": {
        "unreadable_memory": 1, "invalid_header": 1, "unsupported_architecture": 1,
        "invalid_load_command": 1, "nonzero_text_file_offset": 1,
        "nonzero_text_file_offset_in_shared_cache": 2, "invalid_section": 1,
        "missing_text_segment": 1, "address_overflow": 1
      }
    })
  );
  assert_eq!(value["bitdrift"]["images"].as_array().unwrap().len(), 1);
}
