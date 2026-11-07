// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/licenses/strict/1.0.0

use super::{builtin_commands, camel_case};
use std::collections::BTreeMap;

#[test]
fn builtin_metadata_preserves_wire_ids_and_keys() {
  let commands = builtin_commands()
    .into_iter()
    .map(|(name, value, key, _)| (name, (value, key)))
    .collect::<BTreeMap<_, _>>();
  assert_eq!(commands.get("TYPE_UNSPECIFIED"), None);
  assert_eq!(
    commands["DUMP_DEVICE_BUFFER"],
    (1, "dump_device_buffer".to_owned())
  );
  assert_eq!(
    commands["TAKE_SCREENSHOT"],
    (2, "take_screenshot".to_owned())
  );
  assert_eq!(
    commands["STACK_SAMPLING_TRACE"],
    (3, "stack_sampling_trace".to_owned())
  );
  assert_eq!(
    commands["HEAP_PROFILE_TRACE"],
    (4, "heap_profile_trace".to_owned())
  );
}

#[test]
fn command_names_use_camel_case() {
  for (name, expected) in [
    ("DUMP_DEVICE_BUFFER", "dumpDeviceBuffer"),
    ("TAKE_SCREENSHOT", "takeScreenshot"),
    ("STACK_SAMPLING_TRACE", "stackSamplingTrace"),
    ("HEAP_PROFILE_TRACE", "heapProfileTrace"),
    ("INT64_DATA", "int64Data"),
    ("TAKE__SCREENSHOT", "takeScreenshot"),
  ] {
    assert_eq!(camel_case(name), expected);
  }
}

#[test]
fn builtin_arguments_carry_their_proto_names_and_kotlin_types() {
  let arguments = builtin_commands()
    .into_iter()
    .map(|(name, _, _, arguments)| (name, arguments))
    .collect::<BTreeMap<_, _>>();
  let owned = |arguments: &[(&str, &'static str)]| {
    arguments
      .iter()
      .map(|(name, kotlin_type)| ((*name).to_owned(), *kotlin_type))
      .collect::<Vec<_>>()
  };
  assert!(arguments["TAKE_SCREENSHOT"].is_empty());
  assert_eq!(
    arguments["STACK_SAMPLING_TRACE"],
    owned(&[
      ("duration_ms", "Long"),
      ("frequency_hz", "Long"),
      ("buffer_size_kb", "Long"),
    ])
  );
  assert_eq!(
    arguments["HEAP_PROFILE_TRACE"],
    owned(&[
      ("duration_ms", "Long"),
      ("buffer_size_kb", "Long"),
      ("sampling_interval_bytes", "Long"),
      ("track_java_allocations", "Boolean"),
    ])
  );
}
