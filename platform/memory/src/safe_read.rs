// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt
// Reads memory that may have been freed or unmapped since its address was recorded. A failed read
// returns None instead of faulting.

#[cfg(test)]
#[path = "./safe_read_test.rs"]
mod tests;

use crate::sys::mach::{page_size, read_memory};

const WORD: usize = size_of::<usize>();

pub fn read(address: usize, destination: &mut [u8]) -> bool {
  read_memory(address, destination)
}

pub fn read_word(address: usize) -> Option<usize> {
  if !address.is_multiple_of(WORD) {
    return None;
  }
  let mut bytes = [0u8; WORD];
  read_memory(address, &mut bytes).then(|| usize::from_ne_bytes(bytes))
}

/// Reads a NUL-terminated string of at most `capacity` bytes, without the terminator. A string
/// longer than that, or one that cannot be read, gives None.
pub fn read_string(address: usize, capacity: usize) -> Option<Vec<u8>> {
  let mut bytes = Vec::with_capacity(capacity);
  let mut chunk = [0u8; 256];

  // Read page by page: a short string near the end of a mapping must not fail because the cap
  // reaches into the next, unmapped page.
  let page_mask = page_size() - 1;
  while bytes.len() < capacity {
    let at = address.checked_add(bytes.len())?;
    let length = (page_size() - (at & page_mask))
      .min(capacity - bytes.len())
      .min(chunk.len());
    let piece = &mut chunk[.. length];
    if !read_memory(at, piece) {
      return None;
    }
    if let Some(terminator) = piece.iter().position(|byte| *byte == 0) {
      bytes.extend_from_slice(&piece[.. terminator]);
      return Some(bytes);
    }
    bytes.extend_from_slice(piece);
  }
  None
}
