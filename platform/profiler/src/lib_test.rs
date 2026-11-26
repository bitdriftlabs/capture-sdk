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
  Image,
  Profiler,
  Snapshot,
  ThreadSnapshot,
  ThreadStatus,
  write_snapshot,
};
use bd_proto::protos::profiling::thread_snapshot::ThreadSnapshot as ProtoSnapshot;
use bd_unwind::aarch64::{Frame, FrameKind, StopReason};
use bd_unwind::image::{ImageError, ImageInfo};
use bd_unwind::memory::AddressRange;
use protobuf::Message;
use std::collections::BTreeMap;
use std::sync::Mutex;

// Cargo nextest isolates tests in processes; Bazel also runs this binary in parallel threads.
// Serialize only tests that intentionally acquire the process-wide capture slot.
pub(crate) static CAPTURE_TEST_LOCK: Mutex<()> = Mutex::new(());

#[test]
fn only_one_profiler_can_run_and_dropping_it_releases_the_slot() {
  let _capture = CAPTURE_TEST_LOCK.lock().unwrap();
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
  write_snapshot(
    &sample_snapshot(),
    &mut output,
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
fn exports_original_leaf_to_root_stacks_and_large_addresses() {
  let snapshot = sample_snapshot();
  let mut bytes = Vec::new();
  write_snapshot(&snapshot, &mut bytes, &CancellationToken::default()).unwrap();
  let decoded = ProtoSnapshot::parse_from_bytes(&bytes).unwrap();
  let thread = &decoded.thread_details.as_ref().unwrap().threads[0];
  assert_eq!(thread.name, snapshot.threads[0].name);
  assert_eq!(thread.frames[0].frame_address, Some(u64::MAX));
  assert_eq!(thread.frames[1].frame_address, Some(0x1000));
}

#[test]
fn skipped_threads_have_no_fabricated_samples() {
  let mut snapshot = sample_snapshot();
  snapshot.threads[0].status = ThreadStatus::CaptureThread;
  snapshot.threads[0].frames.clear();
  let mut bytes = Vec::new();
  write_snapshot(&snapshot, &mut bytes, &CancellationToken::default()).unwrap();
  let decoded = ProtoSnapshot::parse_from_bytes(&bytes).unwrap();
  let details = decoded.thread_details.as_ref().unwrap();
  assert_eq!(details.total_threads, 1);
  assert_eq!(details.threads.len(), 0);
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
  assert_ne!(thread.frames.len(), 0);
  assert!(thread.copied_stack_bytes > 0);
  assert!(
    snapshot
      .threads
      .iter()
      .any(|thread| thread.status == ThreadStatus::CaptureThread)
  );
  assert_ne!(snapshot.images.len(), 0);
  if let Ok(path) = std::env::var("BD_SNAPSHOT_TEST_OUTPUT") {
    write_snapshot(
      &snapshot,
      &mut std::fs::File::create(path).unwrap(),
      &CancellationToken::default(),
    )
    .unwrap();
  }
}

pub(crate) fn sample_snapshot() -> Snapshot {
  Snapshot {
    main_thread_id: None,
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
  let mut no_context = sample_snapshot().threads.remove(0);
  no_context.frames.clear();
  no_context.status = ThreadStatus::NoUserContext;
  snapshot.threads.extend([failed, skipped, no_context]);
  let summary = snapshot.summary();
  assert_eq!(summary.captured_threads, 1);
  assert_eq!(summary.truncated_threads, 1);
  assert_eq!(summary.failed_threads, 1);
  assert_eq!(summary.skipped_threads, 1);
  assert_eq!(summary.no_user_context_threads, 1);
  assert!(snapshot.ensure_useful().is_ok());
  snapshot.threads.remove(0);
  assert_eq!(snapshot.ensure_useful(), Err(CaptureError::NoUsefulStacks));
}

#[test]
fn snapshot_exports_absent_user_context_without_fabricating_frames() {
  let mut snapshot = sample_snapshot();
  snapshot.threads[0].status = ThreadStatus::NoUserContext;
  snapshot.threads[0].frames.clear();
  let mut bytes = Vec::new();
  write_snapshot(&snapshot, &mut bytes, &CancellationToken::default()).unwrap();
  let decoded = ProtoSnapshot::parse_from_bytes(&bytes).unwrap();
  assert_eq!(
    decoded.thread_details.as_ref().unwrap().threads[0]
      .frames
      .len(),
    0
  );
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
  let image = Image {
    info: ImageInfo {
      uuid: Some([7; 16]),
      text: AddressRange::new(0x1000, 0x1000).unwrap(),
    },
    name: "test-image".into(),
  };
  snapshot.record_image(Ok(image.clone()));
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
    ImageError::MissingTextSegment,
    ImageError::AddressOverflow,
  ];
  for error in errors {
    snapshot.record_image(Err(error));
  }
  assert_eq!(snapshot.images, [image]);
  assert_eq!(snapshot.image_errors.values().sum::<usize>(), 9);
  assert_eq!(
    snapshot.image_errors[&ImageError::NonzeroTextFileOffset {
      in_shared_cache: true
    }],
    2
  );
}

#[test]
fn snapshot_names_main_by_native_identity_not_thread_order_or_name() {
  let mut snapshot = sample_snapshot();
  let mut main = sample_snapshot().threads.remove(0);
  main.native_id = 29;
  snapshot.threads.push(main);
  for main_thread_id in [None, Some(29)] {
    snapshot.main_thread_id = main_thread_id;
    let mut bytes = Vec::new();
    write_snapshot(&snapshot, &mut bytes, &CancellationToken::default()).unwrap();
    let decoded = ProtoSnapshot::parse_from_bytes(&bytes).unwrap();
    let threads = &decoded.thread_details.as_ref().unwrap().threads;
    assert_eq!(threads[0].name, snapshot.threads[0].name);
    assert_eq!(
      threads[1].name,
      if main_thread_id.is_some() {
        "Main Thread"
      } else {
        &snapshot.threads[1].name
      }
    );
  }
}
