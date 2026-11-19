// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

#[cfg(test)]
#[path = "./command_error_test.rs"]
mod tests;

use bd_logger::CommandError;

/// Converts the stable error code sent by a platform command implementation into the logger's
/// typed error.
#[must_use]
pub fn from_command_error_wire(code: Option<&str>, message: Option<String>) -> CommandError {
  match code {
    Some("command_unknown") => CommandError::CommandUnknown,
    Some("command_already_executing") => CommandError::AlreadyExecuting,
    Some("max_command_concurrency") => CommandError::MaxCommandConcurrency,
    Some("invalid_arguments") => {
      CommandError::InvalidArguments(message.unwrap_or_else(|| "invalid command arguments".into()))
    },
    Some("handler_failed") => {
      CommandError::HandlerFailed(message.unwrap_or_else(|| "command handler failed".into()))
    },
    Some("timeout") => CommandError::Timeout,
    Some(code) => {
      CommandError::Other(message.unwrap_or_else(|| format!("unknown command error code: {code}")))
    },
    None => CommandError::Other(message.unwrap_or_else(|| "command failed".into())),
  }
}
