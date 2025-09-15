// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

use std::ops::{Deref, DerefMut};

/// A generic FFI ID type that provides a type-safe wrapper around i64 pointers
/// for passing objects across FFI boundaries.
///
/// This provides lifetime safety by preventing the ID from being sent across
/// thread boundaries when used as `FfiId<'_, T>` (note that `FfiId<'static, T>`
/// bypasses this protection).
#[repr(transparent)]
pub struct FfiId<'a, T> {
  value: i64,
  // A fake lifetime to allow us to link a non-static lifetime to the type, which allows us to
  // limit its usage outside of the current function scope.
  _lifetime: std::marker::PhantomData<&'a ()>,
  // Phantom data to ensure the type parameter is part of the type system
  _type: std::marker::PhantomData<T>,
}

impl<T> FfiId<'_, T> {
  /// Creates a new `FfiId` from a raw pointer to a holder object. Use this in cases where you
  /// need a manual conversion from an i64 to an `FfiId`.
  ///
  /// # Safety
  /// The provided pointer *must* be a valid pointer to a holder object of type T.
  #[must_use]
  pub const unsafe fn from_raw(value: i64) -> Self {
    Self {
      value,
      _lifetime: std::marker::PhantomData,
      _type: std::marker::PhantomData,
    }
  }

  /// Returns a reference to the holder object that this `FfiId` represents.
  ///
  /// # Safety
  /// This is safe because all instances of `FfiId` are assumed to wrap a valid holder object.
  const fn holder(&self) -> &T {
    unsafe { &*(self.value as *const T) }
  }

  /// Returns a mutable reference to the holder object that this `FfiId` represents.
  fn holder_mut(&mut self) -> &mut T {
    unsafe { &mut *(self.value as *mut T) }
  }
}

impl<T> Deref for FfiId<'_, T> {
  type Target = T;

  fn deref(&self) -> &Self::Target {
    self.holder()
  }
}

impl<T> DerefMut for FfiId<'_, T> {
  fn deref_mut(&mut self) -> &mut Self::Target {
    self.holder_mut()
  }
}

impl<T> From<FfiId<'_, T>> for i64 {
  fn from(id: FfiId<'_, T>) -> Self {
    id.value
  }
}

/// Convenience macro for creating type-specific FFI ID aliases.
#[macro_export]
macro_rules! ffi_id_for {
  ($holder_type:ty, $id_type:ident) => {
    pub type $id_type<'a> = $crate::ffi_wrapper::FfiId<'a, $holder_type>;
  };
}

/// Convenience macro for implementing `Deref` and `DerefMut` for holder types.
///
/// This generates both `Deref` and `DerefMut` implementations that forward to a specific field.
#[macro_export]
macro_rules! impl_holder_deref {
  ($holder_type:ty, $field_name:ident, $target_type:ty) => {
    impl std::ops::Deref for $holder_type {
      type Target = $target_type;

      fn deref(&self) -> &Self::Target {
        &self.$field_name
      }
    }

    impl std::ops::DerefMut for $holder_type {
      fn deref_mut(&mut self) -> &mut Self::Target {
        &mut self.$field_name
      }
    }
  };
}
