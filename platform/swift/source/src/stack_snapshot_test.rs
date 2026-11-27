// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt
#![allow(clippy::unwrap_used)]

use super::{
  capture_stack_set_main_thread_id,
  capture_stack_snapshot,
  capture_stack_snapshot_bridge_cancel,
  capture_stack_snapshot_bridge_create,
  capture_stack_snapshot_bridge_release,
  main_thread_id,
};
use crate::stack_profile::capture_stack_profile;
use profiler::{CancellationToken, CaptureError, ProfileSummary, Profiler, Summary};
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
    let mut profile_summary = ProfileSummary::default();
    // SAFETY: path, output and handle remain live throughout these synchronous calls. No capture
    // can enter the suspended section because the other request owns the process-wide slot.
    unsafe {
      assert_eq!(
        capture_stack_snapshot(256, native_path.as_ptr(), handle, &raw mut summary),
        2
      );
      assert_eq!(
        capture_stack_profile(
          20,
          500,
          256,
          native_path.as_ptr(),
          handle,
          &raw mut profile_summary
        ),
        2
      );
      capture_stack_snapshot_bridge_cancel(handle);
      assert_eq!(
        capture_stack_snapshot(256, native_path.as_ptr(), handle, &raw mut summary),
        7
      );
      assert_eq!(
        capture_stack_profile(
          20,
          500,
          256,
          native_path.as_ptr(),
          handle,
          &raw mut profile_summary
        ),
        7
      );
      capture_stack_snapshot_bridge_release(handle);
    }
    assert_eq!(summary, Summary::default());
    assert_eq!(profile_summary, ProfileSummary::default());
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

#[test]
fn main_thread_registration_after_bridge_creation_is_visible_across_threads() {
  capture_stack_set_main_thread_id(0);
  let handle = capture_stack_snapshot_bridge_create();
  assert_eq!(main_thread_id(), None);
  std::thread::spawn(|| capture_stack_set_main_thread_id(u64::MAX))
    .join()
    .unwrap();
  assert_eq!(main_thread_id(), Some(u64::MAX));
  // SAFETY: the owned handle is live and no capture or cancellation call uses it.
  unsafe { capture_stack_snapshot_bridge_release(handle) };
  assert_eq!(main_thread_id(), Some(u64::MAX));
  capture_stack_set_main_thread_id(0);
}
