// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt
#![allow(clippy::unwrap_used)]

use super::{CaptureError, CaptureGuard, Snapshot, ThreadSnapshot, ThreadStatus, capture};
use bd_unwind::aarch64::{Frame, FrameKind, StopReason};
use serde_json::Value;

#[test]
fn only_one_capture_can_own_the_guard_and_it_is_released_on_drop() {
  let guard = CaptureGuard::acquire().unwrap();
  assert!(matches!(
    CaptureGuard::acquire(),
    Err(CaptureError::AlreadyCapturing)
  ));
  drop(guard);
  assert!(CaptureGuard::acquire().is_ok());
  #[cfg(all(target_vendor = "apple", target_arch = "aarch64"))]
  captures_native_worker_and_resumes_it();
}

#[test]
fn rejects_invalid_capture_budgets() {
  for size in [0, 15, 16385, u32::MAX] {
    assert!(matches!(
      capture(size),
      Err(CaptureError::InvalidBufferSize)
    ));
  }
}

#[test]
fn exports_root_to_leaf_stacks_without_losing_raw_pc_kinds_or_large_addresses() {
  let snapshot = Snapshot {
    pid: 42,
    images: vec![],
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
  };
  let mut bytes = Vec::new();
  snapshot.write_json(&mut bytes).unwrap();
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
  snapshot.write_json(&mut bytes).unwrap();
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
  let result = capture(256);
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
    snapshot
      .write_json(&mut std::fs::File::create(path).unwrap())
      .unwrap();
  }
}
