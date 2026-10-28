use super::{builtin_commands, swift_command_name};
use std::collections::BTreeMap;

#[test]
fn builtin_metadata_preserves_wire_ids_and_keys() {
  let commands = builtin_commands()
    .into_iter()
    .map(|(name, value, key)| (name, (value, key)))
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
}

#[test]
fn swift_command_cases_use_camel_case() {
  for (name, expected) in [
    ("DUMP_DEVICE_BUFFER", "dumpDeviceBuffer"),
    ("TAKE_SCREENSHOT", "takeScreenshot"),
    ("STACK_SAMPLING_TRACE", "stackSamplingTrace"),
    ("INT64_DATA", "int64Data"),
    ("TAKE__SCREENSHOT", "takeScreenshot"),
  ] {
    assert_eq!(swift_command_name(name), expected);
  }
}
