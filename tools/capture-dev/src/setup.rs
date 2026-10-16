// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

#[cfg(test)]
#[path = "./setup_test.rs"]
mod tests;

use crate::command::CommandRunner;
use clap::ValueEnum;
use serde::Deserialize;
use std::fs;
use std::path::{Path, PathBuf};
use tempfile::TempDir;

const P12_PASSWORD_FIELD: &str = "p12_password";

#[derive(Clone, Copy, Debug, ValueEnum)]
pub(crate) enum SetupPlatform {
  Ios,
}

#[derive(Debug)]
pub(crate) struct SetupOptions {
  pub(crate) bitwarden_item: Option<String>,
}

#[derive(Debug, Deserialize)]
struct BitwardenItem {
  id: String,
  attachments: Option<Vec<BitwardenAttachment>>,
  fields: Option<Vec<BitwardenField>>,
}

#[derive(Debug, Deserialize)]
#[serde(rename_all = "camelCase")]
struct BitwardenAttachment {
  file_name: String,
}

#[derive(Debug, Deserialize)]
struct BitwardenField {
  name: String,
  value: Option<String>,
}

#[derive(Debug)]
struct SigningAssets {
  certificate: String,
  profiles: Vec<String>,
  password: String,
}

pub(crate) fn run_setup(
  runner: &dyn CommandRunner,
  platform: SetupPlatform,
  options: &SetupOptions,
) -> Result<(), String> {
  match platform {
    SetupPlatform::Ios => setup_ios(runner, options),
  }
}

fn setup_ios(runner: &dyn CommandRunner, options: &SetupOptions) -> Result<(), String> {
  if std::env::consts::OS != "macos" {
    return Err("iOS signing assets can only be installed on macOS.".to_owned());
  }
  let item_selector = options.bitwarden_item.as_deref().ok_or_else(|| {
    "Choose the signing item with `--bitwarden-item <id-or-name>` or set \
     CAPTURE_DEV_IOS_SIGNING_ITEM. Run `bw list items` to find its ID."
      .to_owned()
  })?;
  ensure_unlocked_bitwarden(runner)?;
  let item = bitwarden_item(runner, item_selector)?;
  let assets = SigningAssets::from_item(&item)?;
  let profiles_directory = provisioning_profiles_directory()?;
  fs::create_dir_all(&profiles_directory)
    .map_err(|error| format!("Could not create {}: {error}", profiles_directory.display()))?;

  let temporary_directory = TempDir::new()
    .map_err(|error| format!("Could not create a temporary signing directory: {error}"))?;
  println!("[1/3] Downloading signing assets from Bitwarden...");
  let certificate =
    download_attachment(runner, &item.id, &assets.certificate, &temporary_directory)?;
  let profiles = assets
    .profiles
    .iter()
    .map(|profile| download_attachment(runner, &item.id, profile, &temporary_directory))
    .collect::<Result<Vec<_>, _>>()?;

  println!("[2/3] Importing the Apple Development certificate...");
  import_certificate(runner, &certificate, &assets.password)?;
  println!(
    "[3/3] Installing {} provisioning profile(s)...",
    profiles.len()
  );
  for profile in profiles {
    let destination =
      profiles_directory.join(profile.file_name().ok_or_else(|| {
        "Bitwarden returned a provisioning profile without a filename.".to_owned()
      })?);
    fs::copy(&profile, &destination)
      .map_err(|error| format!("Could not install {}: {error}", destination.display()))?;
  }
  println!(
    "iOS signing assets are ready. Run `capture-dev doctor ios --for-device-install` to verify."
  );
  Ok(())
}

impl SigningAssets {
  fn from_item(item: &BitwardenItem) -> Result<Self, String> {
    let attachments = item.attachments.as_deref().unwrap_or_default();
    let certificates = attachments
      .iter()
      .filter(|attachment| has_extension(&attachment.file_name, "p12"))
      .map(|attachment| attachment.file_name.clone())
      .collect::<Vec<_>>();
    if certificates.len() != 1 {
      return Err(
        "The Bitwarden item must contain exactly one `.p12` certificate attachment.".to_owned(),
      );
    }
    let profiles = attachments
      .iter()
      .filter(|attachment| has_extension(&attachment.file_name, "mobileprovision"))
      .map(|attachment| attachment.file_name.clone())
      .collect::<Vec<_>>();
    if profiles.is_empty() {
      return Err(
        "The Bitwarden item must contain at least one `.mobileprovision` attachment.".to_owned(),
      );
    }
    let password = item
      .fields
      .as_deref()
      .unwrap_or_default()
      .iter()
      .find(|field| field.name == P12_PASSWORD_FIELD)
      .and_then(|field| field.value.clone())
      .filter(|value| !value.is_empty())
      .ok_or_else(|| {
        format!("The Bitwarden item needs a non-empty `{P12_PASSWORD_FIELD}` custom field.")
      })?;
    Ok(Self {
      certificate: certificates.into_iter().next().unwrap_or_default(),
      profiles,
      password,
    })
  }
}

fn ensure_unlocked_bitwarden(runner: &dyn CommandRunner) -> Result<(), String> {
  let status = runner.run("bw", &["status"]);
  if !status.success {
    return Err(
      "Bitwarden CLI (`bw`) is unavailable. Install it, log in, and unlock your vault.".to_owned(),
    );
  }
  let status = serde_json::from_str::<serde_json::Value>(&status.stdout).map_err(|_| {
    "Bitwarden did not return a readable status. Run `bw status` and resolve its error.".to_owned()
  })?;
  if status
    .pointer("/status")
    .and_then(serde_json::Value::as_str)
    == Some("unlocked")
  {
    return Ok(());
  }
  Err("Bitwarden is locked. Run `export BW_SESSION=\"$(bw unlock --raw)\"`, then retry.".to_owned())
}

fn bitwarden_item(runner: &dyn CommandRunner, selector: &str) -> Result<BitwardenItem, String> {
  let item = runner.run("bw", &["get", "item", selector]);
  if !item.success {
    return Err(format!(
      "Could not retrieve Bitwarden item `{selector}`. Use its exact ID or unique name."
    ));
  }
  serde_json::from_str(&item.stdout)
    .map_err(|_| "Bitwarden returned an unreadable signing item.".to_owned())
}

fn download_attachment(
  runner: &dyn CommandRunner,
  item_id: &str,
  filename: &str,
  directory: &TempDir,
) -> Result<PathBuf, String> {
  let destination = directory.path().join(filename);
  let destination_string = destination.to_string_lossy();
  let result = runner.run(
    "bw",
    &[
      "get",
      "attachment",
      filename,
      "--itemid",
      item_id,
      "--output",
      &destination_string,
    ],
  );
  if result.success && destination.is_file() {
    Ok(destination)
  } else {
    Err(format!(
      "Could not download Bitwarden attachment `{filename}`."
    ))
  }
}

fn import_certificate(
  runner: &dyn CommandRunner,
  certificate: &Path,
  password: &str,
) -> Result<(), String> {
  let certificate = certificate.to_string_lossy();
  let keychain = home_directory()?.join("Library/Keychains/login.keychain-db");
  let keychain = keychain.to_string_lossy();
  let result = runner.run(
    "security",
    &[
      "import",
      &certificate,
      "-k",
      &keychain,
      "-P",
      password,
      "-T",
      "/usr/bin/codesign",
    ],
  );
  if result.success {
    Ok(())
  } else {
    Err("Could not import the signing certificate into the login keychain.".to_owned())
  }
}

fn provisioning_profiles_directory() -> Result<PathBuf, String> {
  Ok(
    home_directory()?
      .join("Library")
      .join("Developer")
      .join("Xcode")
      .join("UserData")
      .join("Provisioning Profiles"),
  )
}

fn home_directory() -> Result<PathBuf, String> {
  std::env::var_os("HOME")
    .map(PathBuf::from)
    .ok_or_else(|| "Could not locate the home directory for signing assets.".to_owned())
}

fn has_extension(filename: &str, extension: &str) -> bool {
  Path::new(filename)
    .extension()
    .is_some_and(|candidate| candidate.eq_ignore_ascii_case(extension))
}
