// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

use super::{
  DeviceKind,
  physical_devices_from_json,
  simulators_from_json,
  unavailable_physical_devices_from_json,
};

#[test]
fn simulators_are_parsed_with_their_runtime_and_udid() {
  let simulators = simulators_from_json(
    r#"{
      "devices": {
        "com.apple.CoreSimulator.SimRuntime.iOS-18-0": [
          {
            "name": "iPhone 16",
            "udid": "SIMULATOR-UDID",
            "state": "Booted",
            "isAvailable": true
          }
        ]
      }
    }"#,
  );

  assert_eq!(simulators.len(), 1);
  assert_eq!(simulators[0].name, "iPhone 16");
  assert_eq!(simulators[0].identifier, "SIMULATOR-UDID");
  assert_eq!(simulators[0].runtime.as_deref(), Some("iOS 18 0"));
}

#[test]
fn physical_devices_exclude_macs_and_simulators() {
  let physical_devices = physical_devices_from_json(
    r#"[
      {
        "name": "My iPhone",
        "identifier": "PHONE-UDID",
        "platform": "com.apple.platform.iphoneos",
        "operatingSystemVersion": "18.0",
        "simulator": false,
        "available": true
      },
      {
        "name": "My Mac",
        "identifier": "MAC-UDID",
        "platform": "com.apple.platform.macosx",
        "simulator": false,
        "available": true
      }
    ]"#,
  );

  assert_eq!(physical_devices.len(), 1);
  assert_eq!(physical_devices[0].name, "My iPhone");
  assert_eq!(physical_devices[0].identifier, "PHONE-UDID");
}

#[test]
fn explicit_destination_selection_requires_the_right_kind() {
  let simulators = simulators_from_json(
    r#"{
      "devices": {
        "com.apple.CoreSimulator.SimRuntime.iOS-18-0": [
          { "name": "iPhone 16", "udid": "SIMULATOR-UDID", "isAvailable": true }
        ]
      }
    }"#,
  );
  let physical_devices = physical_devices_from_json(
    r#"[
      {
        "name": "My iPhone",
        "identifier": "PHONE-UDID",
        "platform": "com.apple.platform.iphoneos",
        "simulator": false,
        "available": true
      }
    ]"#,
  );
  let report = super::DeviceReport {
    simulators,
    physical_devices,
    unavailable_physical_devices: Vec::new(),
    issues: Vec::new(),
  };

  assert!(
    report
      .resolve_destination(Some("SIMULATOR-UDID"), None, true)
      .is_ok_and(|simulator| simulator.kind() == DeviceKind::Simulator)
  );
  assert!(
    report
      .resolve_destination(Some("PHONE-UDID"), None, true)
      .is_err()
  );
  assert!(report.resolve_destination(None, None, true).is_err());
}

#[test]
fn unavailable_physical_devices_include_xcodes_recovery_step() {
  let devices = unavailable_physical_devices_from_json(
    r#"[
      {
        "name": "Ariel's iPhone",
        "identifier": "PHONE-UDID",
        "platform": "com.apple.platform.iphoneos",
        "simulator": false,
        "available": false,
        "error": {
          "description": "Browsing on the local area network for Ariel's iPhone",
          "recoverySuggestion": "Unlock the device.\nConnect it with a cable."
        }
      }
    ]"#,
  );

  assert_eq!(devices.len(), 1);
  assert_eq!(devices[0].name, "Ariel's iPhone");
  assert_eq!(
    devices[0].reason,
    "Browsing on the local area network for Ariel's iPhone"
  );
  assert_eq!(
    devices[0].next_step,
    "Unlock the device. Connect it with a cable."
  );
}
