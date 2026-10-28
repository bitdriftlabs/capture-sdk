// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

use crate::command::{CommandResult, CommandRunner};
use crate::devices::{DevicePlatform, SelectableDevice, run_devices};
use crate::feature::{FeatureAction, FeatureExecutor, FeaturePlan};
use crate::maestro_setup::maestro_binary_path;
use crate::run::{
  hello_world_bundle_id,
  install_hello_world_on_physical_device,
  relaunch_hello_world_on_physical_device,
};
use std::path::{Path, PathBuf};
use std::thread;
use std::time::Duration;

const CRASH_DIRECTORY: &str = "Library/Application Support/bitdrift_capture/reports/kscrash";
const REPORT_UPLOADS_DIRECTORY: &str =
  "Library/Application Support/bitdrift_capture/report_uploads";
const REPORT_UPLOAD_RETRIES: usize = 12;

//
// IosIntegrationOptions
//

pub(crate) struct IosIntegrationOptions {
  pub(crate) device: Option<String>,
  pub(crate) non_interactive: bool,
}

//
// IosFeatureExecutor
//

/// Executes the first physical-device iOS integration feature. Device inspection is intentionally
/// kept here, behind the Gherkin actions, so later assertions can move into external scripts
/// without changing how a feature is run.
pub(crate) struct IosFeatureExecutor<'a> {
  runner: &'a dyn CommandRunner,
  device: SelectableDevice,
  queued_reports_before: Option<String>,
}

impl<'a> IosFeatureExecutor<'a> {
  pub(crate) fn new(
    runner: &'a dyn CommandRunner,
    options: &IosIntegrationOptions,
  ) -> Result<Self, String> {
    let destinations = run_devices(runner, DevicePlatform::Ios);
    let device = destinations
      .resolve_physical_destination(options.device.as_deref(), options.non_interactive)?;
    println!("Selected physical device: {}", device.selector());
    Ok(Self {
      runner,
      device,
      queued_reports_before: None,
    })
  }

  fn snapshot_reports(&self, subdirectory: &str) -> Result<String, String> {
    let result = self.runner.run(
      "xcrun",
      &[
        "devicectl",
        "device",
        "info",
        "files",
        "--device",
        self.device.selector(),
        "--domain-type",
        "appDataContainer",
        "--domain-identifier",
        hello_world_bundle_id(),
        "--subdirectory",
        subdirectory,
        "--recurse",
      ],
    );
    if result.success {
      Ok(result.stdout)
    } else {
      Err(device_access_error(&result))
    }
  }

  fn run_maestro_flow(&self, flow: &str) -> Result<(), String> {
    let maestro = maestro_binary_path()?;
    if !maestro.is_file() {
      return Err(format!(
        "Bitdrift Maestro is not installed at {}. Run `capture-dev setup maestro`, then rerun \
         this test.",
        maestro.display()
      ));
    }
    let flow = maestro_flow_path(flow)?;
    let maestro = maestro.to_string_lossy();
    let flow = flow.to_string_lossy();
    run_required_streaming(
      self.runner,
      &maestro,
      &["--device", self.device.selector(), "test", &flow],
      "Maestro could not trigger the requested sample-app flow.",
    )
  }

  fn assert_crash_cache_exists(&self) -> Result<(), String> {
    let crash_files = self.snapshot_reports(CRASH_DIRECTORY)?;
    if crash_files.contains("lastCrash.bjn") {
      println!("[PASS] Capture persisted the crash artifact.");
      Ok(())
    } else {
      Err(
        "Capture did not persist `lastCrash.bjn` after the intentional crash. The sample app may \
         not have reached the crash action."
          .to_owned(),
      )
    }
  }

  fn assert_new_crash_report(&self) -> Result<(), String> {
    let before = self.queued_reports_before.as_deref().ok_or_else(|| {
      "The test did not snapshot Capture's queued reports before the crash.".to_owned()
    })?;
    for attempt in 1 ..= REPORT_UPLOAD_RETRIES {
      let after = self.snapshot_reports(REPORT_UPLOADS_DIRECTORY)?;
      if after != before && !after.trim().is_empty() {
        println!("[PASS] Capture recovered the crash into a queued report.");
        return Ok(());
      }
      if attempt < REPORT_UPLOAD_RETRIES {
        thread::sleep(Duration::from_secs(1));
      }
    }
    Err(
      "Capture did not create a new queued report after relaunching. Keep the device unlocked and \
       connected over USB, then rerun the test."
        .to_owned(),
    )
  }
}

impl FeatureExecutor for IosFeatureExecutor<'_> {
  fn begin_scenario(&mut self, feature: &str, scenario: &str) -> Result<(), String> {
    println!("Feature: {feature}");
    println!("Scenario: {scenario}");
    Ok(())
  }

  fn execute_action(
    &mut self,
    index: usize,
    total: usize,
    action: &FeatureAction,
  ) -> Result<(), String> {
    println!("[{index}/{total}] {}...", action.description());
    match action {
      FeatureAction::InstallIosSample => {
        install_hello_world_on_physical_device(self.runner, &self.device)
      },
      FeatureAction::SnapshotQueuedReports => {
        self.queued_reports_before = Some(self.snapshot_reports(REPORT_UPLOADS_DIRECTORY)?);
        Ok(())
      },
      FeatureAction::RunMaestroFlow { flow } => self.run_maestro_flow(flow),
      FeatureAction::RelaunchIosSample => {
        relaunch_hello_world_on_physical_device(self.runner, &self.device)
      },
      FeatureAction::RunAssertion { assertion } => match assertion.as_str() {
        "ios.crash_cache_exists" => self.assert_crash_cache_exists(),
        "ios.new_crash_report" => self.assert_new_crash_report(),
        _ => Err(format!("No iOS assertion is registered for `{assertion}`.")),
      },
    }
  }
}

pub(crate) fn run_ios_feature(
  runner: &dyn CommandRunner,
  options: &IosIntegrationOptions,
  plan: &FeaturePlan,
) -> Result<(), String> {
  let mut executor = IosFeatureExecutor::new(runner, options)?;
  plan.execute(&mut executor)
}

fn maestro_flow_path(flow: &str) -> Result<PathBuf, String> {
  if !flow
    .chars()
    .all(|character| character.is_ascii_lowercase() || character == '-')
  {
    return Err(format!("Invalid Maestro flow name `{flow}`."));
  }
  let path = Path::new(env!("CARGO_MANIFEST_DIR"))
    .join("maestro")
    .join(format!("{flow}.yaml"));
  if path.is_file() {
    Ok(path)
  } else {
    Err(format!(
      "The bundled Maestro flow `{flow}` was not found at {}.",
      path.display()
    ))
  }
}

fn run_required_streaming(
  runner: &dyn CommandRunner,
  program: &str,
  arguments: &[&str],
  message: &str,
) -> Result<(), String> {
  let result = runner.run_streaming(program, arguments);
  if result.success {
    Ok(())
  } else if result.stderr.trim().is_empty() {
    Err(message.to_owned())
  } else {
    Err(format!("{message} {}", result.stderr.trim()))
  }
}

fn device_access_error(result: &CommandResult) -> String {
  let detail = result.stderr.trim();
  if detail.is_empty() {
    "Could not inspect the sample app's data container on the device. Unlock and trust the device, \
     connect it over USB, then rerun this scenario."
      .to_owned()
  } else {
    format!(
      "Could not inspect the sample app's data container on the device. Unlock and trust the \
       device, connect it over USB, then rerun this scenario. {detail}"
    )
  }
}
