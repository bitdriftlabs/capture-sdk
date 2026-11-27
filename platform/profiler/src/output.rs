// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt
use crate::{CancellationToken, Profile, Snapshot, encode};
use protobuf::Message;
use std::io::{self, Write};

#[cfg(test)]
#[path = "./output_test.rs"]
mod tests;

/// Writes a thread snapshot protobuf with cooperative cancellation.
pub fn write_snapshot(
  snapshot: &Snapshot,
  output: &mut impl Write,
  cancellation: &CancellationToken,
) -> io::Result<()> {
  write_message(
    &encode::snapshot(snapshot, cancellation)?,
    output,
    cancellation,
  )
}

/// Writes a periodic profile protobuf with cooperative cancellation.
pub fn write_profile(
  profile: &Profile,
  output: &mut impl Write,
  cancellation: &CancellationToken,
) -> io::Result<()> {
  write_message(
    &encode::profile(profile, cancellation)?,
    output,
    cancellation,
  )
}

fn write_message(
  message: &impl Message,
  output: &mut impl Write,
  cancellation: &CancellationToken,
) -> io::Result<()> {
  let mut output = CancellableWriter {
    output,
    cancellation,
  };
  message.write_to_writer(&mut output)?;
  output.flush()
}

//
// CancellableWriter
//

struct CancellableWriter<'a, W> {
  output: &'a mut W,
  cancellation: &'a CancellationToken,
}

impl<W: Write> Write for CancellableWriter<'_, W> {
  fn write(&mut self, bytes: &[u8]) -> io::Result<usize> {
    // ConnectionAborted avoids write_all retrying forever as it would for Interrupted.
    encode::check(self.cancellation)?;
    self.output.write(bytes)
  }

  fn flush(&mut self) -> io::Result<()> {
    encode::check(self.cancellation)?;
    self.output.flush()
  }
}
