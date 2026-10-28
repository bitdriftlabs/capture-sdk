// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

use crate::command::{CommandResult, CommandRunner};
use crate::devices::{DeviceKind, DevicePlatform, SelectableDevice, run_devices};
use clap::ValueEnum;
use std::fs;
use std::path::{Path, PathBuf};
use tempfile::TempDir;

const HELLO_WORLD_BUNDLE_ID: &str = "io.bitdrift.example.swiftapp.helloworld";
// This target links Capture from source and is the intended local development loop.
// The separate `ios_app` target validates importing the released XCFramework.
const HELLO_WORLD_TARGET: &str = "//examples/swift/hello_world:hello_world_app";
// rules_apple's default transition uses --cpu, not top-level --platforms.
// This is also the device selector used by the iOS CI workflow.
const IOS_DEVICE_CPU: &str = "--cpu=ios_arm64";

// RunPlatform.
#[derive(Clone, Copy, Debug, ValueEnum)]
pub(crate) enum RunPlatform {
  Ios,
}

// RunOptions.
#[derive(Debug)]
pub(crate) struct RunOptions {
  pub(crate) simulator: Option<String>,
  pub(crate) device: Option<String>,
  pub(crate) non_interactive: bool,
}

// UnpackedApp keeps the temporary IPA extraction alive until installation completes.
struct UnpackedApp {
  _directory: TempDir,
  path: PathBuf,
}

pub(crate) fn run(
  runner: &dyn CommandRunner,
  platform: RunPlatform,
  options: &RunOptions,
) -> Result<(), String> {
  match platform {
    RunPlatform::Ios => run_ios(runner, options),
  }
}

fn run_ios(runner: &dyn CommandRunner, options: &RunOptions) -> Result<(), String> {
  let destinations = run_devices(runner, DevicePlatform::Ios);
  let destination = match destinations.resolve_destination(
    options.simulator.as_deref(),
    options.device.as_deref(),
    options.non_interactive,
  ) {
    Ok(destination) => destination,
    Err(error) => {
      destinations.print_human();
      return Err(error);
    },
  };
  println!(
    "[1/4] Selected {}: {}",
    destination_kind(&destination),
    destination.selector()
  );
  let ipa = build_hello_world(runner, destination.kind())?;
  let app = unpack_app(runner, &ipa)?;
  install_and_launch(runner, &destination, &app, !options.non_interactive)
}

fn build_hello_world(runner: &dyn CommandRunner, kind: DeviceKind) -> Result<PathBuf, String> {
  let mut arguments = vec!["build", "--show_progress"];
  if kind == DeviceKind::Physical {
    arguments.push(IOS_DEVICE_CPU);
  }
  arguments.push(HELLO_WORLD_TARGET);
  println!("[2/4] Building the iOS sample app...");
  run_required_streaming(
    runner,
    "./bazelw",
    &arguments,
    "Could not build the iOS sample app.",
  )?;

  let mut query_arguments = vec!["cquery"];
  if kind == DeviceKind::Physical {
    query_arguments.push(IOS_DEVICE_CPU);
  }
  query_arguments.extend([HELLO_WORLD_TARGET, "--output=files"]);
  println!("[3/4] Preparing the app for installation...");
  let output = run_required(
    runner,
    "./bazelw",
    &query_arguments,
    "Could not locate the built IPA.",
  )?;
  output
    .stdout
    .lines()
    .map(PathBuf::from)
    .find(|path| path.extension().is_some_and(|extension| extension == "ipa"))
    .ok_or_else(|| "Bazel completed but did not report an IPA output.".to_owned())
}

fn unpack_app(runner: &dyn CommandRunner, ipa: &Path) -> Result<UnpackedApp, String> {
  let directory =
    TempDir::new().map_err(|error| format!("Could not create a temporary directory: {error}"))?;
  let destination = directory.path();
  let ipa = ipa.to_string_lossy();
  let destination_string = destination.to_string_lossy();
  run_required(
    runner,
    "unzip",
    &["-q", &ipa, "-d", &destination_string],
    "Could not unpack the IPA.",
  )?;
  let payload = destination.join("Payload");
  let path = fs::read_dir(&payload)
    .map_err(|error| format!("Could not inspect the IPA payload: {error}"))?
    .filter_map(Result::ok)
    .map(|entry| entry.path())
    .find(|path| path.extension().is_some_and(|extension| extension == "app"))
    .ok_or_else(|| "The IPA does not contain an app bundle.".to_owned())?;
  Ok(UnpackedApp {
    _directory: directory,
    path,
  })
}

fn install_and_launch(
  runner: &dyn CommandRunner,
  destination: &SelectableDevice,
  app: &UnpackedApp,
  open_simulator: bool,
) -> Result<(), String> {
  let selector = destination.selector();
  let app = app.path.to_string_lossy();
  println!("[4/4] Installing and launching the sample app...");
  match destination.kind() {
    DeviceKind::Simulator => {
      run_required(
        runner,
        "xcrun",
        &["simctl", "bootstatus", selector, "-b"],
        "Simulator did not finish booting.",
      )?;
      if open_simulator {
        open_simulator_window(runner, selector);
      }
      run_required(
        runner,
        "xcrun",
        &["simctl", "install", selector, &app],
        "Could not install the sample app.",
      )?;
      let launch = run_required(
        runner,
        "xcrun",
        &["simctl", "launch", selector, HELLO_WORLD_BUNDLE_ID],
        "Could not launch the sample app.",
      )?;
      let process_id = launch.stdout.trim();
      if process_id.is_empty() {
        println!("Launched the sample app in Simulator.");
      } else {
        println!("Launched the sample app in Simulator (PID {process_id}).");
      }
    },
    DeviceKind::Physical => {
      run_required(
        runner,
        "xcrun",
        &[
          "devicectl",
          "device",
          "install",
          "app",
          "--device",
          selector,
          &app,
        ],
        "Could not install the sample app on the device.",
      )?;
      run_required(
        runner,
        "xcrun",
        &[
          "devicectl",
          "device",
          "process",
          "launch",
          "--device",
          selector,
          "--terminate-existing",
          HELLO_WORLD_BUNDLE_ID,
        ],
        "Could not launch the sample app on the device.",
      )?;
      println!("Launched the sample app on the physical device.");
    },
  }
  Ok(())
}

fn open_simulator_window(runner: &dyn CommandRunner, selector: &str) {
  let result = runner.run(
    "open",
    &["-a", "Simulator", "--args", "-CurrentDeviceUDID", selector],
  );
  if !result.success {
    let detail = result.stderr.trim();
    if detail.is_empty() {
      println!("[WARN] The app launched, but Simulator could not be opened automatically.");
    } else {
      println!(
        "[WARN] The app launched, but Simulator could not be opened automatically: {detail}"
      );
    }
  }
}

fn run_required(
  runner: &dyn CommandRunner,
  program: &str,
  arguments: &[&str],
  message: &str,
) -> Result<CommandResult, String> {
  let result = runner.run(program, arguments);
  if result.success {
    Ok(result)
  } else {
    let detail = result.stderr.trim();
    if detail.is_empty() {
      Err(message.to_owned())
    } else {
      Err(format!("{message} {detail}"))
    }
  }
}

fn run_required_streaming(
  runner: &dyn CommandRunner,
  program: &str,
  arguments: &[&str],
  message: &str,
) -> Result<CommandResult, String> {
  let result = runner.run_streaming(program, arguments);
  if result.success {
    Ok(result)
  } else {
    let detail = result.stderr.trim();
    if detail.is_empty() {
      Err(message.to_owned())
    } else {
      Err(format!("{message} {detail}"))
    }
  }
}

const fn destination_kind(destination: &SelectableDevice) -> &'static str {
  match destination.kind() {
    DeviceKind::Simulator => "simulator",
    DeviceKind::Physical => "device",
  }
}
