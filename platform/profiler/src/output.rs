// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt
use crate::{CancellationToken, Snapshot, json};
use std::io::{self, Write};

#[cfg(test)]
#[path = "./output_test.rs"]
mod tests;

/// Writes Firefox Profiler JSON with a byte ceiling and cooperative cancellation. Samples describe
/// sequential thread observations; native IDs and capture outcomes accompany the imported samples.
pub fn write_json(
  snapshot: &Snapshot,
  output: &mut impl Write,
  max_bytes: usize,
  cancellation: &CancellationToken,
) -> io::Result<()> {
  let mut output = LimitedWriter {
    output,
    remaining: max_bytes,
    cancellation,
  };
  json::write(snapshot, &mut output)?;
  output.flush()
}

//
// LimitedWriter
//

struct LimitedWriter<'a, W> {
  output: &'a mut W,
  remaining: usize,
  cancellation: &'a CancellationToken,
}

impl<W: Write> Write for LimitedWriter<'_, W> {
  fn write(&mut self, bytes: &[u8]) -> io::Result<usize> {
    if self.cancellation.is_cancelled() {
      // Interrupted would make write_all retry forever on cancellation.
      return Err(io::ErrorKind::ConnectionAborted.into());
    }
    if bytes.len() > self.remaining {
      return Err(io::ErrorKind::FileTooLarge.into());
    }
    let written = self.output.write(bytes)?;
    self.remaining -= written;
    Ok(written)
  }

  fn flush(&mut self) -> io::Result<()> {
    if self.cancellation.is_cancelled() {
      return Err(io::ErrorKind::ConnectionAborted.into());
    }
    self.output.flush()
  }
}
