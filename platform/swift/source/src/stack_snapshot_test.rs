// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt
#![allow(clippy::unwrap_used)]

use super::{
  capture_stack_snapshot,
  capture_stack_snapshot_bridge_cancel,
  capture_stack_snapshot_bridge_create,
  capture_stack_snapshot_bridge_release,
};
use profiler::{CancellationToken, CaptureError, Profiler, Summary};
use std::ffi::CString;

#[test]
fn bridge_handles_share_the_request_slot_and_rejected_cancellation_keeps_it_busy() {
  let directory = tempfile::tempdir().unwrap();
  let path = directory.path().join("snapshot.json");
  let native_path = CString::new(path.to_str().unwrap()).unwrap();
  let session = Profiler::new(256, &CancellationToken::default()).unwrap();
  std::thread::spawn(move || {
    let handle = capture_stack_snapshot_bridge_create();
    let mut summary = Summary::default();
    // SAFETY: path, output and handle remain live throughout these synchronous calls. No capture
    // can enter the suspended section because the other request owns the process-wide slot.
    unsafe {
      assert_eq!(
        capture_stack_snapshot(256, native_path.as_ptr(), handle, &raw mut summary),
        2
      );
      capture_stack_snapshot_bridge_cancel(handle);
      assert_eq!(
        capture_stack_snapshot(256, native_path.as_ptr(), handle, &raw mut summary),
        7
      );
      capture_stack_snapshot_bridge_release(handle);
    }
    assert_eq!(summary, Summary::default());
  })
  .join()
  .unwrap();
  assert!(!path.exists());
  assert!(matches!(
    Profiler::new(256, &CancellationToken::default()),
    Err(CaptureError::AlreadyCapturing)
  ));
  drop(session);
  assert!(Profiler::new(256, &CancellationToken::default()).is_ok());
}
