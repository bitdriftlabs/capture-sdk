// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt
#![allow(clippy::unwrap_used)]

use super::{ExportError, write_attachment, write_file, write_json};
use profiler::{CancellationToken, Snapshot};
use std::collections::BTreeMap;
use std::fs;
use std::io::{self, Write};
use std::path::PathBuf;
use std::sync::atomic::{AtomicUsize, Ordering};

static NEXT_PATH: AtomicUsize = AtomicUsize::new(0);

//
// TestPath
//

struct TestPath(PathBuf);

impl TestPath {
  fn new() -> Self {
    Self(std::env::temp_dir().join(format!(
      "bd-stack-output-{}-{}.json",
      std::process::id(),
      NEXT_PATH.fetch_add(1, Ordering::Relaxed)
    )))
  }
}

impl Drop for TestPath {
  fn drop(&mut self) {
    let _ = fs::remove_file(&self.0);
  }
}

#[test]
fn oversized_export_removes_its_partial_file() {
  let path = TestPath::new();
  assert_eq!(
    write_attachment(
      &sample_snapshot(),
      &path.0,
      200,
      &CancellationToken::default()
    ),
    Err(ExportError::TooLarge)
  );
  assert!(!path.0.exists());
}

#[test]
fn existing_files_are_never_overwritten_or_removed() {
  let path = TestPath::new();
  fs::write(&path.0, b"existing file").unwrap();
  assert_eq!(
    write_attachment(
      &sample_snapshot(),
      &path.0,
      usize::MAX,
      &CancellationToken::default()
    ),
    Err(ExportError::Io)
  );
  assert_eq!(fs::read(&path.0).unwrap(), b"existing file");
}

#[test]
fn cancelled_export_creates_no_file() {
  let path = TestPath::new();
  let cancellation = CancellationToken::default();
  cancellation.cancel();
  assert_eq!(
    write_attachment(&sample_snapshot(), &path.0, usize::MAX, &cancellation),
    Err(ExportError::Cancelled)
  );
  assert!(!path.0.exists());
}

#[test]
fn cancellation_at_successful_completion_still_removes_the_owned_file() {
  let path = TestPath::new();
  let cancellation = CancellationToken::default();
  let result = write_file(&path.0, &cancellation, |output| {
    write_json(&sample_snapshot(), output, usize::MAX, &cancellation)?;
    cancellation.cancel();
    Ok(())
  });
  assert_eq!(result, Err(ExportError::Cancelled));
  assert!(!path.0.exists());
}

//
// CancellingWriter
//

struct CancellingWriter<'a, W> {
  inner: &'a mut W,
  cancellation: &'a CancellationToken,
}

impl<W: Write> Write for CancellingWriter<'_, W> {
  fn write(&mut self, bytes: &[u8]) -> io::Result<usize> {
    let written = self.inner.write(bytes)?;
    self.cancellation.cancel();
    Ok(written)
  }
  fn flush(&mut self) -> io::Result<()> {
    self.inner.flush()
  }
}

#[test]
fn cancellation_while_serializing_stops_writing_and_removes_the_partial_file() {
  let path = TestPath::new();
  let cancellation = CancellationToken::default();
  let result = write_file(&path.0, &cancellation, |output| {
    let mut output = CancellingWriter {
      inner: output,
      cancellation: &cancellation,
    };
    write_json(&sample_snapshot(), &mut output, usize::MAX, &cancellation)
  });
  assert_eq!(result, Err(ExportError::Cancelled));
  assert!(!path.0.exists());
}

#[test]
fn io_failure_removes_the_owned_partial_file() {
  let path = TestPath::new();
  let cancellation = CancellationToken::default();
  let result = write_file(&path.0, &cancellation, |output| {
    output.write_all(b"partial file")?;
    Err(io::ErrorKind::StorageFull.into())
  });
  assert_eq!(result, Err(ExportError::Io));
  assert!(!path.0.exists());
}

fn sample_snapshot() -> Snapshot {
  Snapshot {
    pid: 42,
    threads: vec![],
    images: vec![],
    image_errors: BTreeMap::new(),
  }
}
