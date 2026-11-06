// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt
// Fixed-capacity arrays in their own anonymous mappings. Malloc keeps freed memory in the
// process's footprint; an unmapped array gives it back, and its blocks never show in the census.

#[cfg(test)]
#[path = "./mapped_test.rs"]
mod tests;

use std::ops::{Deref, DerefMut};
use std::ptr::NonNull;

//
// MappedVec
//

/// Pages are zero-filled and committed only when first touched. Never grows.
pub struct MappedVec<T> {
  pointer: NonNull<T>,
  len: usize,
  capacity: usize,
}

// SAFETY: owns its elements like a `Vec`.
unsafe impl<T: Send> Send for MappedVec<T> {}

impl<T> MappedVec<T> {
  /// None if the mapping fails.
  pub fn with_capacity(capacity: usize) -> Option<Self> {
    let bytes = capacity.checked_mul(size_of::<T>())?;
    if bytes == 0 {
      return Some(Self::default());
    }
    // SAFETY: a fresh anonymous mapping, owned by the returned value.
    let mapping = unsafe {
      libc::mmap(
        std::ptr::null_mut(),
        bytes,
        libc::PROT_READ | libc::PROT_WRITE,
        libc::MAP_ANON | libc::MAP_PRIVATE,
        -1,
        0,
      )
    };
    if mapping == libc::MAP_FAILED {
      return None;
    }
    Some(Self {
      pointer: NonNull::new(mapping.cast())?,
      len: 0,
      capacity,
    })
  }

  /// `capacity` elements, all zeroed.
  ///
  /// # Safety
  ///
  /// The all-zero bit pattern must be a valid `T`.
  pub unsafe fn zeroed(capacity: usize) -> Option<Self> {
    let mut vec = Self::with_capacity(capacity)?;
    vec.len = capacity;
    Some(vec)
  }

  /// Hands the value back when full.
  pub const fn push(&mut self, value: T) -> Result<(), T> {
    if self.len == self.capacity {
      return Err(value);
    }
    // SAFETY: `len < capacity`, so the slot is inside the mapping and unused.
    unsafe { self.pointer.as_ptr().add(self.len).write(value) };
    self.len += 1;
    Ok(())
  }

  pub const fn capacity(&self) -> usize {
    self.capacity
  }

  pub fn clear(&mut self) {
    let len = self.len;
    self.len = 0;
    // SAFETY: the first `len` slots were initialized and are no longer reachable.
    unsafe {
      std::ptr::drop_in_place(std::ptr::slice_from_raw_parts_mut(
        self.pointer.as_ptr(),
        len,
      ));
    }
  }
}

impl<T> Default for MappedVec<T> {
  fn default() -> Self {
    Self {
      pointer: NonNull::dangling(),
      len: 0,
      capacity: 0,
    }
  }
}

impl<T> Deref for MappedVec<T> {
  type Target = [T];

  fn deref(&self) -> &[T] {
    // SAFETY: the first `len` slots are initialized.
    unsafe { std::slice::from_raw_parts(self.pointer.as_ptr(), self.len) }
  }
}

impl<T> DerefMut for MappedVec<T> {
  fn deref_mut(&mut self) -> &mut [T] {
    // SAFETY: the first `len` slots are initialized and uniquely borrowed.
    unsafe { std::slice::from_raw_parts_mut(self.pointer.as_ptr(), self.len) }
  }
}

impl<'a, T> IntoIterator for &'a MappedVec<T> {
  type Item = &'a T;
  type IntoIter = std::slice::Iter<'a, T>;

  fn into_iter(self) -> Self::IntoIter {
    self.iter()
  }
}

impl<T> Drop for MappedVec<T> {
  fn drop(&mut self) {
    self.clear();
    if self.capacity > 0 {
      // SAFETY: the mapping created in `with_capacity`, with its size.
      unsafe {
        libc::munmap(self.pointer.as_ptr().cast(), self.capacity * size_of::<T>());
      }
    }
  }
}
