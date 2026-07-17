// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

#![allow(clippy::unwrap_used)]

use super::Coordinator;
use crate::previous::{PreviousCrashDetails, PreviousCrashState};
use crate::test_support::test_crash_record_guard;
use anyhow::Result;
use std::ffi::CString;

#[test]
fn coordinator_defaults_to_no_previous_crash() -> Result<()> {
  let _guard = test_crash_record_guard();
  let tempdir = tempfile::tempdir()?;
  let path = CString::new(
    tempdir
      .path()
      .join("state.bin")
      .to_string_lossy()
      .as_bytes(),
  )?;

  let coordinator = Coordinator::new(&path)?;

  assert_eq!(
    *coordinator.previous_crash_state(),
    PreviousCrashState::default()
  );
  Ok(())
}

#[test]
fn default_previous_crash_state_has_no_details() {
  let previous_state = PreviousCrashState::default();

  assert!(matches!(previous_state.details, PreviousCrashDetails::None));
}

fn make_coordinator() -> Result<(Coordinator, tempfile::TempDir)> {
  let tempdir = tempfile::tempdir()?;
  let path = CString::new(
    tempdir
      .path()
      .join("state.bin")
      .to_string_lossy()
      .as_bytes(),
  )?;
  let coordinator = Coordinator::new(&path)?;
  Ok((coordinator, tempdir))
}

#[test]
fn stop_without_start_is_safe() -> Result<()> {
  let _guard = test_crash_record_guard();
  let (coordinator, _dir) = make_coordinator()?;

  coordinator.stop();

  Ok(())
}

#[test]
fn second_start_is_idempotent() -> Result<()> {
  let _guard = test_crash_record_guard();
  let (coordinator, _dir) = make_coordinator()?;

  coordinator.start();
  let result = coordinator.start();
  coordinator.stop();

  assert!(result);
  Ok(())
}

#[test]
fn start_stop_start_succeeds() -> Result<()> {
  let _guard = test_crash_record_guard();
  let (coordinator, _dir) = make_coordinator()?;

  coordinator.start();
  coordinator.stop();
  coordinator.start();
  coordinator.stop();

  Ok(())
}
