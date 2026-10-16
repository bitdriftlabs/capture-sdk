// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

use crate::command::{CommandResult, CommandRunner};
use clap::ValueEnum;
use dialoguer::FuzzySelect;
use dialoguer::theme::ColorfulTheme;
use serde::Serialize;
use serde_json::Value;
use std::io::IsTerminal;

#[derive(Clone, Copy, Debug, ValueEnum)]
pub(crate) enum DevicePlatform {
  Ios,
}

#[derive(Clone, Copy, Debug, Eq, PartialEq, Serialize)]
#[serde(rename_all = "snake_case")]
pub(crate) enum DeviceKind {
  Simulator,
  Physical,
}

#[derive(Clone, Debug, Serialize)]
pub(crate) struct SelectableDevice {
  kind: DeviceKind,
  name: String,
  identifier: String,
  state: String,
  runtime: Option<String>,
}

// UnavailableDevice is known to Xcode but cannot receive an app yet.
#[derive(Debug, Serialize)]
struct UnavailableDevice {
  name: String,
  identifier: String,
  reason: String,
  next_step: String,
}

#[derive(Debug, Serialize)]
struct DeviceIssue {
  source: &'static str,
  summary: &'static str,
  next_step: &'static str,
}

#[derive(Debug, Serialize)]
pub(crate) struct DeviceReport {
  simulators: Vec<SelectableDevice>,
  physical_devices: Vec<SelectableDevice>,
  unavailable_physical_devices: Vec<UnavailableDevice>,
  issues: Vec<DeviceIssue>,
}

impl DeviceReport {
  pub(crate) fn print_human(&self) {
    print_devices("Simulators", &self.simulators);
    print_devices("Physical devices", &self.physical_devices);
    print_unavailable_devices(&self.unavailable_physical_devices);
    if !self.issues.is_empty() {
      println!("\nDiagnostics");
      for issue in &self.issues {
        println!("[WARN] {}: {}", issue.source, issue.summary);
        println!("       Next: {}", issue.next_step);
      }
    }
  }

  pub(crate) fn select_interactively(&self) -> Result<Option<&SelectableDevice>, String> {
    if !std::io::stdin().is_terminal() || !std::io::stderr().is_terminal() {
      return Err(
        "No interactive terminal is available. Run `capture-dev devices ios` and pass the \
         selector explicitly."
          .to_owned(),
      );
    }
    let devices = self
      .simulators
      .iter()
      .chain(&self.physical_devices)
      .collect::<Vec<_>>();
    if devices.is_empty() {
      return Err(
        "No selectable iOS destination was found. Resolve the diagnostics above, then rerun this \
         command."
          .to_owned(),
      );
    }
    let labels = devices
      .iter()
      .map(|device| device.label())
      .collect::<Vec<_>>();
    let selected = FuzzySelect::with_theme(&ColorfulTheme::default())
      .with_prompt("Choose an iOS destination")
      .items(&labels)
      .interact_opt()
      .map_err(|error| format!("Could not select an iOS destination: {error}"))?;
    Ok(selected.map(|index| devices[index]))
  }

  pub(crate) fn resolve_destination(
    &self,
    simulator: Option<&str>,
    device: Option<&str>,
    non_interactive: bool,
  ) -> Result<SelectableDevice, String> {
    if let Some(selector) = simulator {
      return self.find(DeviceKind::Simulator, selector);
    }
    if let Some(selector) = device {
      return self.find(DeviceKind::Physical, selector);
    }
    if non_interactive {
      return Err(
        "`--non-interactive` requires `--simulator <selector>` or `--device <selector>`."
          .to_owned(),
      );
    }
    self
      .select_interactively()?
      .cloned()
      .ok_or_else(|| "No iOS destination was selected.".to_owned())
  }

  fn find(&self, kind: DeviceKind, selector: &str) -> Result<SelectableDevice, String> {
    self
      .simulators
      .iter()
      .chain(&self.physical_devices)
      .find(|destination| destination.kind == kind && destination.identifier == selector)
      .cloned()
      .ok_or_else(|| {
        format!("No {kind:?} destination matches `{selector}`. Run `capture-dev devices ios`.")
      })
  }
}

impl SelectableDevice {
  pub(crate) fn selector(&self) -> &str {
    &self.identifier
  }

  pub(crate) const fn kind(&self) -> DeviceKind {
    self.kind
  }

  fn label(&self) -> String {
    let kind = match self.kind {
      DeviceKind::Simulator => "Simulator",
      DeviceKind::Physical => "Device",
    };
    let runtime = self
      .runtime
      .as_deref()
      .map_or_else(String::new, |runtime| format!(" · {runtime}"));
    format!("{kind} · {} · {}{}", self.name, self.state, runtime)
  }
}

pub(crate) fn run_devices(runner: &dyn CommandRunner, platform: DevicePlatform) -> DeviceReport {
  match platform {
    DevicePlatform::Ios => ios_devices(runner),
  }
}

fn ios_devices(runner: &dyn CommandRunner) -> DeviceReport {
  let simulator_result = runner.run(
    "xcrun",
    &["simctl", "list", "devices", "available", "--json"],
  );
  let physical_result = runner.run("xcrun", &["xcdevice", "list"]);
  let mut issues = Vec::new();
  let simulators = if simulator_result.success {
    simulators_from_json(&simulator_result.stdout)
  } else {
    issues.push(simulator_issue(&simulator_result));
    Vec::new()
  };
  let physical_devices = if physical_result.success {
    physical_devices_from_json(&physical_result.stdout)
  } else {
    issues.push(physical_device_issue());
    Vec::new()
  };
  let unavailable_physical_devices = if physical_result.success {
    unavailable_physical_devices_from_json(&physical_result.stdout)
  } else {
    Vec::new()
  };
  DeviceReport {
    simulators,
    physical_devices,
    unavailable_physical_devices,
    issues,
  }
}

fn simulators_from_json(output: &str) -> Vec<SelectableDevice> {
  let Ok(value) = serde_json::from_str::<Value>(output) else {
    return Vec::new();
  };
  let Some(runtimes) = value.get("devices").and_then(Value::as_object) else {
    return Vec::new();
  };
  let mut simulators = Vec::new();
  for (runtime, devices) in runtimes {
    let Some(devices) = devices.as_array() else {
      continue;
    };
    for device in devices {
      if !is_available(device) {
        continue;
      }
      let Some(name) = string_value(device, "name") else {
        continue;
      };
      let Some(identifier) = string_value(device, "udid") else {
        continue;
      };
      simulators.push(SelectableDevice {
        kind: DeviceKind::Simulator,
        name: name.to_owned(),
        identifier: identifier.to_owned(),
        state: string_value(device, "state")
          .unwrap_or("Unknown")
          .to_owned(),
        runtime: Some(display_runtime(runtime)),
      });
    }
  }
  sort_devices(&mut simulators);
  simulators
}

fn physical_devices_from_json(output: &str) -> Vec<SelectableDevice> {
  let Ok(devices) = serde_json::from_str::<Vec<Value>>(output) else {
    return Vec::new();
  };
  let mut physical_devices = devices
    .iter()
    .filter(|device| is_available(device))
    .filter(|device| string_value(device, "platform") == Some("com.apple.platform.iphoneos"))
    .filter(|device| device.get("simulator").and_then(Value::as_bool) != Some(true))
    .filter_map(|device| {
      Some(SelectableDevice {
        kind: DeviceKind::Physical,
        name: string_value(device, "name")?.to_owned(),
        identifier: string_value(device, "identifier")?.to_owned(),
        state: "Connected".to_owned(),
        runtime: string_value(device, "operatingSystemVersion").map(str::to_owned),
      })
    })
    .collect::<Vec<_>>();
  sort_devices(&mut physical_devices);
  physical_devices
}

fn unavailable_physical_devices_from_json(output: &str) -> Vec<UnavailableDevice> {
  let Ok(devices) = serde_json::from_str::<Vec<Value>>(output) else {
    return Vec::new();
  };
  devices
    .iter()
    .filter(|device| !is_available(device))
    .filter(|device| string_value(device, "platform") == Some("com.apple.platform.iphoneos"))
    .filter(|device| device.get("simulator").and_then(Value::as_bool) != Some(true))
    .filter_map(|device| {
      Some(UnavailableDevice {
        name: string_value(device, "name")?.to_owned(),
        identifier: string_value(device, "identifier")?.to_owned(),
        reason: device
          .pointer("/error/description")
          .and_then(Value::as_str)
          .unwrap_or("Xcode cannot currently reach this device.")
          .to_owned(),
        next_step: device
          .pointer("/error/recoverySuggestion")
          .and_then(Value::as_str)
          .unwrap_or("Unlock and reconnect the device, then rerun this command.")
          .replace('\n', " "),
      })
    })
    .collect()
}

fn is_available(device: &Value) -> bool {
  device
    .get("isAvailable")
    .or_else(|| device.get("available"))
    .and_then(Value::as_bool)
    .unwrap_or(true)
}

fn string_value<'a>(value: &'a Value, key: &str) -> Option<&'a str> {
  value.get(key).and_then(Value::as_str)
}

fn display_runtime(runtime: &str) -> String {
  runtime
    .strip_prefix("com.apple.CoreSimulator.SimRuntime.")
    .unwrap_or(runtime)
    .replace('-', " ")
}

fn sort_devices(devices: &mut [SelectableDevice]) {
  devices.sort_by(|left, right| {
    left
      .name
      .cmp(&right.name)
      .then_with(|| left.identifier.cmp(&right.identifier))
  });
}

fn simulator_issue(result: &CommandResult) -> DeviceIssue {
  let summary = if result
    .stderr
    .contains("CoreSimulatorService connection became invalid")
  {
    "CoreSimulatorService is unavailable."
  } else {
    "Could not query available simulators."
  };
  DeviceIssue {
    source: "ios.simulators",
    summary,
    next_step: "Open Simulator or Xcode, wait for simulator services to start, then rerun this \
                command.",
  }
}

const fn physical_device_issue() -> DeviceIssue {
  DeviceIssue {
    source: "ios.physical_devices",
    summary: "Could not query connected physical devices.",
    next_step: "Unlock and trust the device, reconnect it, then rerun this command.",
  }
}

fn print_devices(title: &str, devices: &[SelectableDevice]) {
  println!("\n{title}");
  if devices.is_empty() {
    println!("  None found.");
    return;
  }
  for device in devices {
    let runtime = device
      .runtime
      .as_deref()
      .map_or_else(String::new, |runtime| format!(" · {runtime}"));
    println!(
      "  {} · {}{}\n    selector: {}",
      device.name, device.state, runtime, device.identifier
    );
  }
}

fn print_unavailable_devices(devices: &[UnavailableDevice]) {
  if devices.is_empty() {
    return;
  }
  println!("\nUnavailable physical devices");
  for device in devices {
    println!("  {} ({})", device.name, device.identifier);
    println!("    [WARN] {}", device.reason);
    println!("    Next: {}", device.next_step);
  }
}

#[cfg(test)]
#[path = "./devices_test.rs"]
mod tests;
