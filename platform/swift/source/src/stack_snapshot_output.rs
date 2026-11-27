// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt
use profiler::{CancellationToken, Snapshot, write_json};
use std::fs::{self, File, OpenOptions};
use std::io::{self, BufWriter};
use std::path::Path;

#[cfg(test)]
#[path = "./stack_snapshot_output_test.rs"]
mod tests;

//
// ExportError
//

#[derive(Clone, Copy, Debug, Eq, PartialEq)]
pub enum ExportError {
  Cancelled,
  TooLarge,
  Io,
}

/// Creates a new, exclusively owned attachment. Any failed or cancelled export removes its partial
/// file. Existing paths are never overwritten or deleted. The byte limit covers the encoded JSON.
pub fn write_attachment(
  snapshot: &Snapshot,
  path: &Path,
  max_bytes: usize,
  cancellation: &CancellationToken,
) -> Result<(), ExportError> {
  write_file(path, cancellation, |output| {
    write_json(snapshot, output, max_bytes, cancellation)
  })
}

fn write_file(
  path: &Path,
  cancellation: &CancellationToken,
  write: impl FnOnce(&mut BufWriter<File>) -> io::Result<()>,
) -> Result<(), ExportError> {
  if cancellation.is_cancelled() {
    return Err(ExportError::Cancelled);
  }
  let file = OpenOptions::new()
    .write(true)
    .create_new(true)
    .open(path)
    .map_err(|_| ExportError::Io)?;
  let result = {
    let mut output = BufWriter::new(file);
    write(&mut output)
  };
  if result.is_err() || cancellation.is_cancelled() {
    let _ = fs::remove_file(path);
    if cancellation.is_cancelled() {
      return Err(ExportError::Cancelled);
    }
    return Err(match result.err().map(|error| error.kind()) {
      Some(io::ErrorKind::FileTooLarge) => ExportError::TooLarge,
      _ => ExportError::Io,
    });
  }
  Ok(())
}
