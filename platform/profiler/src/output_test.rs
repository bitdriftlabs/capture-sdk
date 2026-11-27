// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt
#![allow(clippy::unwrap_used)]

use super::write_snapshot;
use crate::CancellationToken;
use crate::tests::sample_snapshot;
use std::io::{self, Write};

//
// ShortWriter
//

struct ShortWriter(Vec<u8>);

impl Write for ShortWriter {
  fn write(&mut self, bytes: &[u8]) -> io::Result<usize> {
    let count = bytes.len().min(1);
    self.0.extend_from_slice(&bytes[.. count]);
    Ok(count)
  }

  fn flush(&mut self) -> io::Result<()> {
    Ok(())
  }
}

#[test]
fn serialization_preserves_all_bytes_when_the_writer_only_accepts_partial_writes() {
  let snapshot = sample_snapshot();
  let cancellation = CancellationToken::default();
  let mut expected = Vec::new();
  write_snapshot(&snapshot, &mut expected, &cancellation).unwrap();
  let mut output = ShortWriter(Vec::new());
  write_snapshot(&snapshot, &mut output, &cancellation).unwrap();
  assert_eq!(output.0, expected);
}
