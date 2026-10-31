// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/licenses/strict/1.0.0

#![allow(clippy::assertions_on_constants)]

use super::*;

#[test]
fn maps_known_wire_codes_to_typed_errors() {
  for (code, message, expected) in [
    (Some("command_unknown"), None, CommandError::CommandUnknown),
    (
      Some("max_command_concurrency"),
      None,
      CommandError::MaxCommandConcurrency,
    ),
    (
      Some("command_already_executing"),
      None,
      CommandError::AlreadyExecuting,
    ),
    (
      Some("invalid_arguments"),
      Some("argument 'verbose' has an invalid value"),
      CommandError::InvalidArguments("argument 'verbose' has an invalid value".into()),
    ),
    (
      Some("handler_failed"),
      Some("capture failed"),
      CommandError::HandlerFailed("capture failed".into()),
    ),
    (Some("timeout"), None, CommandError::Timeout),
  ] {
    assert_eq!(
      from_command_error_wire(code, message.map(str::to_string)),
      expected,
    );
  }
}

#[test]
fn maps_an_unknown_code_to_other() {
  assert_eq!(
    from_command_error_wire(
      Some("unsupported_command"),
      Some("Unsupported command".into())
    ),
    CommandError::Other("Unsupported command".into()),
  );
}
