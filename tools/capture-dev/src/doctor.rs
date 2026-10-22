// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

use crate::command::CommandRunner;
use anstyle::{AnsiColor, Color, Style};
use serde::Serialize;
use std::ffi::OsStr;
use std::io::IsTerminal;
use std::path::{Path, PathBuf};
use std::{env, fs};
use tempfile::NamedTempFile;

const SAMPLE_APP_BUNDLE_ID: &str = "io.bitdrift.example.swiftapp.helloworld";

// DoctorTarget.
#[derive(Clone, Copy, Debug)]
pub(crate) enum DoctorTarget {
  All,
  Android,
  Ios,
}

// DoctorOptions.
#[derive(Clone, Copy, Debug)]
pub(crate) struct DoctorOptions {
  pub(crate) target: DoctorTarget,
  pub(crate) for_device_install: bool,
}

// CheckState.
#[derive(Clone, Copy, Debug, Eq, PartialEq, Serialize)]
#[serde(rename_all = "snake_case")]
enum CheckState {
  Pass,
  Warning,
  Blocked,
}

impl CheckState {
  const fn label(self) -> &'static str {
    match self {
      Self::Pass => "PASS",
      Self::Warning => "WARN",
      Self::Blocked => "BLOCKED",
    }
  }

  fn display(self, color_enabled: bool) -> String {
    let label = self.label();
    if !color_enabled {
      return format!("[{label}]");
    }

    let style = match self {
      Self::Pass => Style::new().fg_color(Some(Color::Ansi(AnsiColor::Green))),
      Self::Warning => Style::new().fg_color(Some(Color::Ansi(AnsiColor::Yellow))),
      Self::Blocked => Style::new().fg_color(Some(Color::Ansi(AnsiColor::Red))),
    };
    format!("{style}[{label}]{style:#}")
  }
}

// Check.
#[derive(Debug, Serialize)]
struct Check {
  id: &'static str,
  state: CheckState,
  summary: String,
  next_step: Option<String>,
}

// DoctorReport.
#[derive(Debug, Serialize)]
pub(crate) struct DoctorReport {
  checks: Vec<Check>,
}

impl DoctorReport {
  pub(crate) fn is_ready(&self) -> bool {
    !self
      .checks
      .iter()
      .any(|check| check.state == CheckState::Blocked)
  }

  pub(crate) fn print_human(&self) {
    let color_enabled = std::io::stdout().is_terminal() && env::var_os("NO_COLOR").is_none();
    println!("capture-dev doctor");
    for check in &self.checks {
      println!(
        "{} {}: {}",
        check.state.display(color_enabled),
        check.id,
        check.summary
      );
      if let Some(next_step) = &check.next_step {
        println!("       Next: {next_step}");
      }
    }
  }
}

pub(crate) fn run_doctor(runner: &dyn CommandRunner, options: DoctorOptions) -> DoctorReport {
  let mut checks = Vec::new();
  match options.target {
    DoctorTarget::All => {
      checks.extend(android_checks());
      checks.extend(ios_checks(runner, options.for_device_install));
    },
    DoctorTarget::Android => checks.extend(android_checks()),
    DoctorTarget::Ios => checks.extend(ios_checks(runner, options.for_device_install)),
  }
  DoctorReport { checks }
}

fn android_checks() -> Vec<Check> {
  // TODO(ademarco): Add Android tool, SDK, emulator, and physical-device checks.
  vec![Check {
    id: "android.pending",
    state: CheckState::Warning,
    summary: "Android environment checks have not been implemented yet.".to_owned(),
    next_step: Some("Android doctor support is intentionally deferred.".to_owned()),
  }]
}

fn ios_checks(runner: &dyn CommandRunner, for_device_install: bool) -> Vec<Check> {
  if env::consts::OS != "macos" {
    return vec![blocked(
      "ios.host",
      "iOS device installation requires macOS.",
      "Run this command on a Mac with Xcode installed.",
    )];
  }

  let mut checks = Vec::new();
  let developer_directory = runner.run("xcode-select", &["-p"]);
  if !developer_directory.success {
    checks.push(blocked(
      "ios.xcode",
      "The active developer directory is not configured.",
      "Install Xcode, open it once to accept its license, then run `sudo xcode-select --switch \
       /Applications/Xcode.app`.",
    ));
    return checks;
  }
  checks.push(passed(
    "ios.xcode",
    format!(
      "Xcode developer directory is active at {}.",
      developer_directory.stdout.trim()
    ),
  ));

  let devicectl = runner.run("xcrun", &["--find", "devicectl"]);
  if !devicectl.success {
    checks.push(blocked(
      "ios.devicectl",
      "Xcode's device management tool (devicectl) is not available.",
      "Install a current Xcode release and select it with xcode-select.",
    ));
    return checks;
  }
  checks.push(passed(
    "ios.devicectl",
    "Xcode device management tooling is available.",
  ));

  checks.push(signing_identity_check(runner));
  checks.push(provisioning_profile_check(runner, for_device_install));
  checks.push(ios_device_check(runner, for_device_install));
  checks
}

fn signing_identity_check(runner: &dyn CommandRunner) -> Check {
  let identities = runner.run("security", &["find-identity", "-v", "-p", "codesigning"]);
  let count = identities
    .stdout
    .lines()
    .find_map(|line| line.trim().strip_suffix(" valid identities found"))
    .and_then(|count| count.trim().parse::<usize>().ok())
    .unwrap_or(0);
  if identities.success && count > 0 {
    passed(
      "ios.signing_identity",
      format!("{count} local code-signing identity or identities available."),
    )
  } else {
    blocked(
      "ios.signing_identity",
      "No usable local code-signing identity was found.",
      "Run the future `capture-dev signing sync` command, or create an Apple Development \
       certificate in Xcode Settings > Accounts.",
    )
  }
}

fn provisioning_profile_check(runner: &dyn CommandRunner, for_device_install: bool) -> Check {
  let Some(home) = env::var_os("HOME") else {
    return Check {
      id: "ios.provisioning_profiles",
      state: CheckState::Warning,
      summary: "Could not locate the home directory to inspect provisioning profiles.".to_owned(),
      next_step: Some(
        "After Xcode signs an app once, rerun doctor to confirm profile availability.".to_owned(),
      ),
    };
  };
  let profile_directories = provisioning_profile_directories(Path::new(&home));
  let profiles = profile_directories
    .iter()
    .flat_map(|directory| provisioning_profiles(directory))
    .collect::<Vec<_>>();
  if profiles.is_empty() {
    Check {
      id: "ios.provisioning_profiles",
      state: CheckState::Warning,
      summary: "No local provisioning profiles were found.".to_owned(),
      next_step: Some(
        "Run `capture-dev setup ios` to install signing assets from the secret vault.".to_owned(),
      ),
    }
  } else {
    let connected_devices = if for_device_install {
      connected_device_identifiers(runner)
    } else {
      Vec::new()
    };
    let mut metadata = profiles
      .iter()
      .filter_map(|profile| provisioning_profile_metadata(runner, profile, &connected_devices))
      .collect::<Vec<_>>();
    let exact_profiles = metadata
      .iter()
      .filter(|profile| profile.application_id.ends_with(SAMPLE_APP_BUNDLE_ID))
      .cloned()
      .collect::<Vec<_>>();
    if exact_profiles.is_empty() {
      metadata.retain(|profile| profile.application_id.ends_with(".*"));
    } else {
      metadata = exact_profiles;
    }
    let details = metadata
      .iter()
      .map(ProvisioningProfileMetadata::summary)
      .collect::<Vec<_>>()
      .join("; ");
    if details.is_empty() {
      Check {
        id: "ios.provisioning_profiles",
        state: CheckState::Warning,
        summary: format!(
          "{} local provisioning profile(s) found, but none could be decoded.",
          profiles.len()
        ),
        next_step: Some("Re-run `capture-dev setup ios` to refresh the profiles.".to_owned()),
      }
    } else {
      passed("ios.provisioning_profiles", details)
    }
  }
}

fn provisioning_profile_directories(home: &Path) -> [PathBuf; 2] {
  [
    home
      .join("Library")
      .join("Developer")
      .join("Xcode")
      .join("UserData")
      .join("Provisioning Profiles"),
    home
      .join("Library")
      .join("MobileDevice")
      .join("Provisioning Profiles"),
  ]
}

fn provisioning_profiles(profile_directory: &Path) -> Vec<PathBuf> {
  fs::read_dir(profile_directory)
    .ok()
    .into_iter()
    .flatten()
    .filter_map(Result::ok)
    .map(|entry| entry.path())
    .filter(|path| path.extension() == Some(OsStr::new("mobileprovision")))
    .collect()
}

#[derive(Clone)]
struct ProvisioningProfileMetadata {
  name: String,
  team_id: String,
  application_id: String,
  expiration: String,
  device_count: usize,
  covered_connected_devices: Option<usize>,
}

impl ProvisioningProfileMetadata {
  fn summary(&self) -> String {
    let coverage = self
      .covered_connected_devices
      .map_or_else(String::new, |covered| {
        format!(", covers {covered} connected device(s)")
      });
    format!(
      "{}: Team {}, App {}, expires {}, {} registered device(s){}",
      self.name, self.team_id, self.application_id, self.expiration, self.device_count, coverage
    )
  }
}

fn provisioning_profile_metadata(
  runner: &dyn CommandRunner,
  profile: &Path,
  connected_devices: &[String],
) -> Option<ProvisioningProfileMetadata> {
  let profile = profile.to_string_lossy();
  let decoded = runner.run("security", &["cms", "-D", "-i", &profile]);
  if !decoded.success {
    return None;
  }
  let temporary_file = NamedTempFile::new().ok()?;
  fs::write(temporary_file.path(), decoded.stdout).ok()?;
  let plist = temporary_file.path().to_string_lossy();
  let team_id = profile_array(runner, &plist, "TeamIdentifier")?
    .into_iter()
    .next()?;
  let entitlements = profile_json_property(runner, &plist, "Entitlements")?;
  let application_id = entitlements
    .get("application-identifier")
    .and_then(serde_json::Value::as_str)?
    .to_owned();
  let expiration = profile_raw_property(runner, &plist, "ExpirationDate")?;
  let provisioned_devices = profile_array(runner, &plist, "ProvisionedDevices").unwrap_or_default();
  Some(ProvisioningProfileMetadata {
    name: Path::new(&*profile)
      .file_name()?
      .to_string_lossy()
      .into_owned(),
    team_id,
    application_id,
    expiration,
    device_count: provisioned_devices.len(),
    covered_connected_devices: (!connected_devices.is_empty()).then(|| {
      connected_devices
        .iter()
        .filter(|device| provisioned_devices.contains(device))
        .count()
    }),
  })
}

fn connected_device_identifiers(runner: &dyn CommandRunner) -> Vec<String> {
  let devices = runner.run("xcrun", &["xcdevice", "list"]);
  if !devices.success {
    return Vec::new();
  }
  serde_json::from_str::<serde_json::Value>(&devices.stdout)
    .ok()
    .and_then(|devices| devices.as_array().cloned())
    .unwrap_or_default()
    .iter()
    .filter(|device| {
      device.get("platform").and_then(serde_json::Value::as_str)
        == Some("com.apple.platform.iphoneos")
    })
    .filter(|device| device.get("available").and_then(serde_json::Value::as_bool) != Some(false))
    .filter_map(|device| device.get("identifier").and_then(serde_json::Value::as_str))
    .map(str::to_owned)
    .collect()
}

fn profile_json_property(
  runner: &dyn CommandRunner,
  plist: &str,
  key: &str,
) -> Option<serde_json::Value> {
  let result = runner.run("plutil", &["-extract", key, "json", "-o", "-", plist]);
  result
    .success
    .then(|| serde_json::from_str(&result.stdout).ok())
    .flatten()
}

fn profile_array(runner: &dyn CommandRunner, plist: &str, key: &str) -> Option<Vec<String>> {
  profile_json_property(runner, plist, key).and_then(|value| {
    value.as_array().map(|items| {
      items
        .iter()
        .filter_map(serde_json::Value::as_str)
        .map(str::to_owned)
        .collect()
    })
  })
}

fn profile_raw_property(runner: &dyn CommandRunner, plist: &str, key: &str) -> Option<String> {
  let result = runner.run("plutil", &["-extract", key, "raw", "-o", "-", plist]);
  result.success.then_some(result.stdout.trim().to_owned())
}

fn ios_device_check(runner: &dyn CommandRunner, for_device_install: bool) -> Check {
  let devices = runner.run(
    "xcrun",
    &[
      "devicectl",
      "list",
      "devices",
      "--timeout",
      "15",
      "--json-output",
      "-",
    ],
  );
  if !devices.success {
    return Check {
      id: "ios.device",
      state: if for_device_install {
        CheckState::Blocked
      } else {
        CheckState::Warning
      },
      summary: "Could not query connected Apple devices.".to_owned(),
      next_step: Some(
        "Unlock and trust the device, reconnect it, then rerun `capture-dev doctor ios \
         --for-device-install`."
          .to_owned(),
      ),
    };
  }
  let device_count =
    serde_json::from_str::<serde_json::Value>(&devices.stdout).map_or(0, |value| {
      value
        .pointer("/result/devices")
        .and_then(serde_json::Value::as_array)
        .map_or(0, Vec::len)
    });
  if device_count == 0 {
    Check {
      id: "ios.device",
      state: if for_device_install {
        CheckState::Blocked
      } else {
        CheckState::Warning
      },
      summary: "No usable Apple device was detected.".to_owned(),
      next_step: Some(
        "Connect, unlock, and trust an iPhone or iPad, then rerun `capture-dev doctor ios \
         --for-device-install`."
          .to_owned(),
      ),
    }
  } else {
    passed(
      "ios.device",
      format!("{device_count} Apple device(s) detected by Xcode."),
    )
  }
}

fn passed(id: &'static str, summary: impl Into<String>) -> Check {
  Check {
    id,
    state: CheckState::Pass,
    summary: summary.into(),
    next_step: None,
  }
}

fn blocked(id: &'static str, summary: impl Into<String>, next_step: impl Into<String>) -> Check {
  Check {
    id,
    state: CheckState::Blocked,
    summary: summary.into(),
    next_step: Some(next_step.into()),
  }
}

#[cfg(test)]
#[path = "./doctor_test.rs"]
mod tests;
