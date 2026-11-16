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

use crate::sys::mach::read_memory;

pub fn read(address: usize, destination: &mut [u8]) -> bool {
  read_memory(address, destination)
}
