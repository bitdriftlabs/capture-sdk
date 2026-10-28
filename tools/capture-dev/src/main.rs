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

mod command;
mod devices;
mod doctor;
mod feature;
mod ios_integration;
mod maestro_setup;
mod run;
mod setup;

use clap::{Parser, Subcommand, ValueEnum};
use command::SystemCommandRunner;
use devices::{DevicePlatform, run_devices};
use doctor::{DoctorOptions, DoctorTarget, run_doctor};
use feature::load_feature;
use ios_integration::{IosIntegrationOptions, run_ios_feature};
use maestro_setup::setup_maestro;
use run::{RunOptions, RunPlatform, run};
use setup::{SetupOptions, SetupPlatform, run_setup};
use std::process::ExitCode;

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

#[derive(Debug, Subcommand)]
enum Command {
  /// Inspect whether the environment can build and install capture-sdk sample apps.
  Doctor {
    /// Limit checks to one platform. The default checks every supported platform.
    #[arg(value_enum, default_value_t = Platform::All)]
    platform: Platform,
    /// Require a currently usable physical device.
    #[arg(long)]
    for_device_install: bool,
    /// Return result in json format.
    #[arg(long)]
    json: bool,
  },
  /// List selectable iOS simulators and physical devices.
  Devices {
    #[arg(value_enum)]
    platform: DevicePlatform,
    /// Open a fuzzy picker and print the selected destination selector.
    #[arg(long, conflicts_with = "json")]
    select: bool,
    /// Return result in json format.
    #[arg(long)]
    json: bool,
  },
  /// Build, install, and launch the iOS sample app.
  Run {
    #[arg(value_enum)]
    platform: RunPlatform,
    /// Simulator UDID, as shown by `capture-dev devices ios`.
    #[arg(long, conflicts_with = "device")]
    simulator: Option<String>,
    /// Physical-device UDID, as shown by `capture-dev devices ios`.
    #[arg(long, conflicts_with = "simulator")]
    device: Option<String>,
    /// Do not prompt; require an explicit simulator or device selector.
    #[arg(long)]
    non_interactive: bool,
  },
  /// Set up a supported development dependency.
  Setup {
    #[arg(value_enum)]
    platform: SetupPlatform,
    /// iOS only: secret-vault item ID or unique name. Defaults to
    /// `CAPTURE_DEV_IOS_SECRET_VAULT_ITEM`.
    #[arg(long, env = "CAPTURE_DEV_IOS_SECRET_VAULT_ITEM")]
    secret_vault_item: Option<String>,
    /// iOS only: do not prompt; require a secret-vault item selector.
    #[arg(long)]
    non_interactive: bool,
  },
  /// Run an integration feature on a selected platform.
  Test {
    /// Path to a Gherkin .feature file.
    feature: std::path::PathBuf,
    /// Platform that executes the feature.
    #[arg(long, value_enum)]
    platform: TestPlatform,
    /// Physical iOS device UDID, as shown by `capture-dev devices ios`.
    #[arg(long)]
    device: Option<String>,
    /// Do not prompt; require an explicit device selector.
    #[arg(long)]
    non_interactive: bool,
  },
}

#[derive(Clone, Copy, Debug, Default, ValueEnum)]
enum Platform {
  #[default]
  All,
  Android,
  Ios,
}

#[derive(Clone, Copy, Debug, ValueEnum)]
enum TestPlatform {
  Ios,
  Android,
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
  match cli.command.unwrap_or(Command::Doctor {
    platform: Platform::All,
    for_device_install: false,
    json: false,
  }) {
    Command::Doctor {
      platform,
      for_device_install,
      json,
    } => {
      let report = run_doctor(
        &SystemCommandRunner,
        DoctorOptions {
          target: platform.into(),
          for_device_install,
        },
      );
      if json {
        print_json(&report);
      } else {
        report.print_human();
      }
      if report.is_ready() {
        ExitCode::SUCCESS
      } else {
        ExitCode::from(1)
      }
    },
    Command::Devices {
      platform,
      select,
      json,
    } => {
      let report = run_devices(&SystemCommandRunner, platform);
      if json {
        print_json(&report);
        return ExitCode::SUCCESS;
      }
      report.print_human();
      if select {
        return match report.select_interactively() {
          Ok(Some(destination)) => {
            println!("\nSelected selector: {}", destination.selector());
            ExitCode::SUCCESS
          },
          Ok(None) => ExitCode::from(1),
          Err(message) => {
            eprintln!("\n[BLOCKED] {message}");
            ExitCode::from(1)
          },
        };
      }
      ExitCode::SUCCESS
    },
    Command::Run {
      platform,
      simulator,
      device,
      non_interactive,
    } => {
      let options = RunOptions {
        simulator,
        device,
        non_interactive,
      };
      match run(&SystemCommandRunner, platform, &options) {
        Ok(()) => ExitCode::SUCCESS,
        Err(message) => {
          eprintln!("[BLOCKED] {message}");
          ExitCode::from(1)
        },
      }
    },
    Command::Setup {
      platform,
      secret_vault_item,
      non_interactive,
    } => match platform {
      SetupPlatform::Ios => run_setup(
        &SystemCommandRunner,
        platform,
        &SetupOptions {
          secret_vault_item,
          non_interactive,
        },
      ),
      SetupPlatform::Maestro => setup_maestro(&SystemCommandRunner),
    }
    .map_or_else(
      |message| {
        eprintln!("[BLOCKED] {message}");
        ExitCode::from(1)
      },
      |()| ExitCode::SUCCESS,
    ),
    Command::Test {
      feature,
      platform,
      device,
      non_interactive,
    } => match load_feature(&feature).and_then(|plan| match platform {
      TestPlatform::Ios => run_ios_feature(
        &SystemCommandRunner,
        &IosIntegrationOptions {
          device,
          non_interactive,
        },
        &plan,
      ),
      TestPlatform::Android => {
        Err("TODO(ademarco): Android integration commands will be added later.".to_owned())
      },
    }) {
      Ok(()) => ExitCode::SUCCESS,
      Err(message) => {
        eprintln!("[BLOCKED] {message}");
        ExitCode::from(1)
      },
    },
  }
}

fn print_json(report: &impl serde::Serialize) {
  println!(
    "{}",
    serde_json::to_string_pretty(report).unwrap_or_else(|error| {
      format!(r#"{{\"error\":\"failed to serialize report: {error}\"}}"#)
    })
  );
}
