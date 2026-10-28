// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

use anstyle::{AnsiColor, Color, Style};
use serde::Serialize;
use std::ffi::OsStr;
use std::io::IsTerminal;
use std::path::{Path, PathBuf};
use std::process::{Command, Stdio};
use std::{env, fs};

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

// CommandResult.
#[derive(Debug)]
pub(crate) struct CommandResult {
  success: bool,
  stdout: String,
}

// CommandRunner.
pub(crate) trait CommandRunner {
  fn run(&self, program: &str, arguments: &[&str]) -> CommandResult;
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
      },
      Err(_) => CommandResult {
        success: false,
        stdout: String::new(),
      },
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
  checks.push(provisioning_profile_check());
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

fn provisioning_profile_check() -> Check {
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
  let profile_count = profile_directories
    .iter()
    .map(|directory| provisioning_profile_count(directory))
    .sum::<usize>();
  if profile_count == 0 {
    Check {
      id: "ios.provisioning_profiles",
      state: CheckState::Warning,
      summary: "No local provisioning profiles were found.".to_owned(),
      next_step: Some(
        "This can be resolved by Xcode automatic signing; explicit profile synchronization will \
         be added to capture-dev."
          .to_owned(),
      ),
    }
  } else {
    passed(
      "ios.provisioning_profiles",
      format!("{profile_count} local provisioning profile(s) found."),
    )
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

fn provisioning_profile_count(profile_directory: &Path) -> usize {
  fs::read_dir(profile_directory)
    .ok()
    .into_iter()
    .flatten()
    .filter_map(Result::ok)
    .filter(|entry| entry.path().extension() == Some(OsStr::new("mobileprovision")))
    .count()
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
