// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

#[cfg(test)]
#[path = "./memory_test.rs"]
mod tests;

//
// AddressRange
//

/// A nonempty, half-open range in the captured process's address space.
#[derive(Clone, Copy, Debug, Eq, PartialEq)]
pub struct AddressRange {
  start: u64,
  end: u64,
}

impl AddressRange {
  #[must_use]
  pub const fn new(start: u64, size: u64) -> Option<Self> {
    if size == 0 {
      return None;
    }
    match start.checked_add(size) {
      Some(end) => Some(Self { start, end }),
      None => None,
    }
  }

  #[must_use]
  pub const fn start(self) -> u64 {
    self.start
  }

  #[must_use]
  pub const fn size(self) -> u64 {
    self.end - self.start
  }

  #[must_use]
  pub const fn contains(self, address: u64, size: u64) -> bool {
    address >= self.start && address < self.end && size <= self.end - address
  }
}

//
// MemoryReader
//

/// Reads captured addresses without dereferencing untrusted pointers in the unwinder.
///
/// Success means the entire destination was written. After failure its contents are unspecified.
/// Readers used with suspended threads or crash contexts must themselves avoid allocation, locks,
/// panics and faulting loads. Providing a reader does not make concurrently changing memory
/// coherent.
pub trait MemoryReader {
  fn read(&self, address: u64, destination: &mut [u8]) -> bool;
}

//
// StackMemory
//

/// A borrowed copy of stack memory. Addresses remain those of the original process, not the copy.
pub struct StackMemory<'a> {
  pub address: u64,
  pub bytes: &'a [u8],
}

impl MemoryReader for StackMemory<'_> {
  fn read(&self, address: u64, destination: &mut [u8]) -> bool {
    let Some(offset) = address.checked_sub(self.address) else {
      return false;
    };
    let Ok(offset) = usize::try_from(offset) else {
      return false;
    };
    let Some(end) = offset.checked_add(destination.len()) else {
      return false;
    };
    let Some(source) = self.bytes.get(offset .. end) else {
      return false;
    };
    destination.copy_from_slice(source);
    true
  }
}
