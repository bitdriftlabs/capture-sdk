// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

use super::{CheckState, android_checks, provisioning_profile_directories};
use std::path::Path;

#[test]
fn android_checks_are_explicitly_pending() {
  let checks = android_checks();

  assert_eq!(checks.len(), 1);
  assert_eq!(checks[0].id, "android.pending");
  assert_eq!(checks[0].state, CheckState::Warning);
}

#[test]
fn human_status_uses_color_when_enabled() {
  assert_eq!(CheckState::Warning.display(false), "[WARN]");
  assert!(CheckState::Warning.display(true).contains("\u{1b}["));
}

#[test]
fn provisioning_profiles_include_the_xcode_user_data_location() {
  let directories = provisioning_profile_directories(Path::new("/tmp/capture-dev-home"));

  assert_eq!(
    directories[0],
    Path::new("/tmp/capture-dev-home")
      .join("Library")
      .join("Developer")
      .join("Xcode")
      .join("UserData")
      .join("Provisioning Profiles")
  );
}
