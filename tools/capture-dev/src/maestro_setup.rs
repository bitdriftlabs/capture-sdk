// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

#[cfg(test)]
#[path = "./maestro_setup_test.rs"]
mod tests;

use crate::command::CommandRunner;
use std::fs;
use std::path::{Path, PathBuf};
use tempfile::TempDir;

const RELEASE_TAG: &str = "bd-v2.11.1";
const RELEASE_ARCHIVE: &str = "bitdrift-maestro.zip";
const RELEASE_URL: &str =
  "https://github.com/bitdriftlabs/maestro/releases/download/bd-v2.11.1/bitdrift-maestro.zip";
const RELEASE_SHA256: &str = "66080b880c2ca90c5dea9996e63313c7d0fca7431e2c75a2fdf7ad4ca3a58106";

pub(crate) fn setup_maestro(runner: &dyn CommandRunner) -> Result<(), String> {
  if std::env::consts::OS != "macos" {
    return Err("The Bitdrift Maestro release is currently supported only on macOS.".to_owned());
  }

  let installation = maestro_installation_directory()?;
  let executable = installation.join("bin/maestro");
  if executable.is_file() {
    println!(
      "Bitdrift Maestro {RELEASE_TAG} is already installed at {}.",
      executable.display()
    );
    return Ok(());
  }
  if installation.exists() {
    return Err(format!(
      "The Maestro installation at {} is incomplete. Remove that specific directory, then rerun \
       `capture-dev setup maestro`.",
      installation.display()
    ));
  }

  let installation_parent = installation.parent().ok_or_else(|| {
    "Could not determine the parent directory for the Maestro installation.".to_owned()
  })?;
  fs::create_dir_all(installation_parent).map_err(|error| {
    format!(
      "Could not create the Maestro installation directory {}: {error}",
      installation_parent.display()
    )
  })?;
  let temporary_directory = TempDir::new()
    .map_err(|error| format!("Could not create a temporary Maestro directory: {error}"))?;
  let archive = temporary_directory.path().join(RELEASE_ARCHIVE);
  let archive_string = archive.to_string_lossy();

  println!("[1/3] Downloading Bitdrift Maestro {RELEASE_TAG}...");
  let download = runner.run(
    "curl",
    &[
      "--fail",
      "--location",
      "--silent",
      "--show-error",
      "--output",
      &archive_string,
      RELEASE_URL,
    ],
  );
  if !download.success || !archive.is_file() {
    return Err(format!(
      "Could not download Bitdrift Maestro {RELEASE_TAG}. Check network access to GitHub and \
       retry. {}",
      command_error(&download.stderr)
    ));
  }

  println!("[2/3] Verifying the release checksum...");
  verify_archive_checksum(runner, &archive)?;

  println!("[3/3] Installing Bitdrift Maestro...");
  let extracted = temporary_directory.path().join("extracted");
  let extracted_string = extracted.to_string_lossy();
  let unzip = runner.run("unzip", &["-q", &archive_string, "-d", &extracted_string]);
  if !unzip.success {
    return Err(format!(
      "Could not unpack the Bitdrift Maestro release. {}",
      command_error(&unzip.stderr)
    ));
  }
  let distribution = extracted.join("maestro");
  if !distribution.join("bin/maestro").is_file() {
    return Err(
      "The downloaded Bitdrift Maestro release did not contain `maestro/bin/maestro`. Retry the \
       download or contact the SDK team."
        .to_owned(),
    );
  }
  fs::rename(&distribution, &installation).map_err(|error| {
    format!(
      "Could not install Bitdrift Maestro in {}: {error}",
      installation.display()
    )
  })?;
  println!("Bitdrift Maestro is ready at {}.", executable.display());
  Ok(())
}

pub(crate) fn maestro_binary_path() -> Result<PathBuf, String> {
  Ok(maestro_installation_directory()?.join("bin/maestro"))
}

fn maestro_installation_directory() -> Result<PathBuf, String> {
  let home = std::env::var_os("HOME").map(PathBuf::from).ok_or_else(|| {
    "Could not determine your home directory for the Maestro installation.".to_owned()
  })?;
  Ok(
    home
      .join("Library/Application Support/capture-dev/maestro")
      .join(RELEASE_TAG),
  )
}

fn verify_archive_checksum(runner: &dyn CommandRunner, archive: &Path) -> Result<(), String> {
  let archive_string = archive.to_string_lossy();
  let result = runner.run("shasum", &["-a", "256", &archive_string]);
  if !result.success {
    return Err(format!(
      "Could not verify the Bitdrift Maestro checksum. {}",
      command_error(&result.stderr)
    ));
  }
  verify_checksum_output(&result.stdout)
}

fn verify_checksum_output(output: &str) -> Result<(), String> {
  let actual = output.split_whitespace().next().unwrap_or_default();
  if actual.eq_ignore_ascii_case(RELEASE_SHA256) {
    Ok(())
  } else {
    Err(
      "The downloaded Bitdrift Maestro release did not match its expected checksum. The archive \
       was not installed; retry the download or contact the SDK team."
        .to_owned(),
    )
  }
}

fn command_error(stderr: &str) -> String {
  let stderr = stderr.trim();
  if stderr.is_empty() {
    "No additional command output was available.".to_owned()
  } else {
    stderr.to_owned()
  }
}
