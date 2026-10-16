// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

use std::process::{Command, Stdio};

// CommandResult.
#[derive(Debug)]
pub(crate) struct CommandResult {
  pub(crate) success: bool,
  pub(crate) stdout: String,
  pub(crate) stderr: String,
}

// CommandRunner.
pub(crate) trait CommandRunner {
  fn run(&self, program: &str, arguments: &[&str]) -> CommandResult;

  fn run_streaming(&self, program: &str, arguments: &[&str]) -> CommandResult {
    self.run(program, arguments)
  }
}

// SystemCommandRunner.
pub(crate) struct SystemCommandRunner;

impl CommandRunner for SystemCommandRunner {
  fn run(&self, program: &str, arguments: &[&str]) -> CommandResult {
    match Command::new(program)
      .args(arguments)
      .stdin(Stdio::null())
      .output()
    {
      Ok(output) => CommandResult {
        success: output.status.success(),
        stdout: String::from_utf8_lossy(&output.stdout).into_owned(),
        stderr: String::from_utf8_lossy(&output.stderr).into_owned(),
      },
      Err(error) => CommandResult {
        success: false,
        stdout: String::new(),
        stderr: error.to_string(),
      },
    }
  }

  fn run_streaming(&self, program: &str, arguments: &[&str]) -> CommandResult {
    match Command::new(program)
      .args(arguments)
      .stdin(Stdio::null())
      .stdout(Stdio::inherit())
      .stderr(Stdio::inherit())
      .status()
    {
      Ok(status) => CommandResult {
        success: status.success(),
        stdout: String::new(),
        stderr: String::new(),
      },
      Err(error) => CommandResult {
        success: false,
        stdout: String::new(),
        stderr: error.to_string(),
      },
    }
  }
}
