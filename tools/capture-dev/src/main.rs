// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

#![deny(
  clippy::expect_used,
  clippy::panic,
  clippy::todo,
  clippy::unimplemented,
  clippy::unreachable,
  clippy::unwrap_used
)]

mod doctor;

use clap::{Parser, Subcommand, ValueEnum};
use doctor::{DoctorOptions, DoctorTarget, SystemCommandRunner, run_doctor};
use std::process::ExitCode;

// CaptureDev.
#[derive(Debug, Parser)]
#[command(
  name = "capture-dev",
  about = "Developer environment tooling for capture-sdk",
  version
)]
struct CaptureDev {
  #[command(subcommand)]
  command: Option<Command>,
}

// Command.
#[derive(Debug, Subcommand)]
enum Command {
  /// Inspect whether this machine can build and install capture-sdk sample apps.
  Doctor {
    /// Limit checks to one platform. The default checks every supported platform.
    #[arg(value_enum, default_value_t = Platform::All)]
    platform: Platform,
    /// Require a currently usable physical device instead of only checking tool readiness.
    #[arg(long)]
    for_device_install: bool,
    /// Emit a stable machine-readable report.
    #[arg(long)]
    json: bool,
  },
}

// Platform.
#[derive(Clone, Copy, Debug, Default, ValueEnum)]
enum Platform {
  #[default]
  All,
  Android,
  Ios,
}

impl From<Platform> for DoctorTarget {
  fn from(value: Platform) -> Self {
    match value {
      Platform::All => Self::All,
      Platform::Android => Self::Android,
      Platform::Ios => Self::Ios,
    }
  }
}

fn main() -> ExitCode {
  env_logger::init();
  let cli = CaptureDev::parse();
  let Command::Doctor {
    platform,
    for_device_install,
    json,
  } = cli.command.unwrap_or(Command::Doctor {
    platform: Platform::All,
    for_device_install: false,
    json: false,
  });

  let report = run_doctor(
    &SystemCommandRunner,
    DoctorOptions {
      target: platform.into(),
      for_device_install,
    },
  );
  if json {
    println!(
      "{}",
      serde_json::to_string_pretty(&report).unwrap_or_else(|error| {
        format!(r#"{{\"error\":\"failed to serialize doctor report: {error}\"}}"#)
      })
    );
  } else {
    report.print_human();
  }

  if report.is_ready() {
    ExitCode::SUCCESS
  } else {
    ExitCode::from(1)
  }
}
