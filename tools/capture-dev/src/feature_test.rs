// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

#![allow(clippy::unwrap_used)]

use super::load_feature;
use std::fs;

#[test]
fn parses_the_ios_crash_recovery_contract() {
  let directory = tempfile::tempdir().unwrap();
  let feature = directory.path().join("crash_recovery.feature");
  fs::write(
    &feature,
    r#"Feature: Crash recovery
  Scenario: A native crash becomes an uploadable Capture report
    Given the iOS sample app is installed on a physical device
    And I snapshot Capture's queued reports
    When I run the Maestro flow "ios-crash-now"
    Then the assertion "ios.crash_cache_exists" passes
    When I relaunch the iOS sample app
    Then the assertion "ios.new_crash_report" passes
"#,
  )
  .unwrap();

  let plan = load_feature(&feature).unwrap();

  assert_eq!(plan.name, "Crash recovery");
  assert_eq!(plan.scenarios.len(), 1);
  assert_eq!(plan.scenarios[0].actions.len(), 6);
}

#[test]
fn reports_the_feature_line_for_an_unsupported_step() {
  let directory = tempfile::tempdir().unwrap();
  let feature = directory.path().join("unsupported.feature");
  fs::write(
    &feature,
    r"Feature: Unsupported step
  Scenario: No implementation
    Given the Android sample app is installed
",
  )
  .unwrap();

  let error = load_feature(&feature).unwrap_err();

  assert!(error.contains("unsupported.feature:3"));
  assert!(error.contains("Unsupported step"));
}

#[test]
fn resolves_a_unique_builtin_feature_by_file_name() {
  let plan = load_feature(std::path::Path::new("crash_recovery.feature")).unwrap();

  assert_eq!(plan.name, "Crash recovery");
}
