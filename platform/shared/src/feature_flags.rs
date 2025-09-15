// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

use bd_feature_flags::FeatureFlags;
use std::ops::{Deref, DerefMut};

/// This is the feature flags ID that is passed to the platform code. It is a typed wrapper around an i64
/// that encodes the pointer to the `FeatureFlagsHolder` object.
///
/// We use a fixed size representation to ensure that we can always fit a pointer in it regardless
/// of the platform (assuming 32 or 64 bit pointers).
///
/// We use a signed type since the JVM doesn't support unsigned, though it doesn't matter too much
/// since it's really just an opaque 64 bit value.
///
/// Thanks to the captured lifetime, it is not possible to send or outside of the current function
/// scope when provided as `FeatureFlagsId<'_>` (note that `FeatureFlagsId<'static>` bypasses this).
///
/// ```compile_fail
/// fn f(feature_flags: platform_shared::FeatureFlagsId<'_>) {
///   std::thread::spawn(move || {
///     feature_flags.feature_flags_holder().get_flag("some_flag");
///   });
/// }
/// ```
///
/// This means that it is relatively safe to use `FeatureFlagsId<'_>` in FFI functions, as it is treated
/// exactly like a `i64` in terms of memory layout and passing it around but we get an automatic
/// conversion to a `FeatureFlagsHolder` when we provide it over FFI. As the FFI calls are fundamentally
/// unsafe, this seems no worse than passing i64 and doing an unsafe call at the start of the
/// function.
#[repr(transparent)]
pub struct FeatureFlagsId<'a> {
  value: i64,
  // A fake lifetime to allow us to link a non-static lifetime to the type, which allows us to
  // limit its usage outside of the current function scope.
  _lifetime: std::marker::PhantomData<&'a ()>,
}

impl FeatureFlagsId<'_> {
  /// Creates a new `FeatureFlagsId` from a raw pointer to a `FeatureFlagsHolder`. Use this in cases where you
  /// need a manual conversion from an i64 to a `FeatureFlagsId`.
  ///
  /// # Safety
  /// The provided pointer *must* be a valid pointer to a `FeatureFlagsHolder` object.
  #[must_use]
  pub const unsafe fn from_raw(value: i64) -> Self {
    Self {
      value,
      _lifetime: std::marker::PhantomData,
    }
  }

  /// Returns a reference to the `FeatureFlagsHolder` object that this `FeatureFlagsId` represents. This is
  /// safe because all instances of `FeatureFlagsId` are assumed to wrap a valid `FeatureFlagsHolder` object.
  const fn feature_flags_holder(&self) -> &FeatureFlagsHolder {
    unsafe { &*(self.value as *const FeatureFlagsHolder) }
  }
}

impl Deref for FeatureFlagsId<'_> {
  type Target = FeatureFlagsHolder;

  fn deref(&self) -> &Self::Target {
    self.feature_flags_holder()
  }
}

impl DerefMut for FeatureFlagsId<'_> {
  fn deref_mut(&mut self) -> &mut Self::Target {
    unsafe { &mut *(self.value as *mut FeatureFlagsHolder) }
  }
}

//
// FeatureFlagsHolder
//

/// A wrapper around the feature flags. The platform code passes a pointer to this
/// struct as the feature flags ID, allowing the ffi functions to cast this to the correct type and access
/// these fields to support the feature flags API.
pub struct FeatureFlagsHolder {
  feature_flags: FeatureFlags,
}

impl Deref for FeatureFlagsHolder {
  type Target = FeatureFlags;

  fn deref(&self) -> &Self::Target {
    &self.feature_flags
  }
}

impl DerefMut for FeatureFlagsHolder {
  fn deref_mut(&mut self) -> &mut Self::Target {
    &mut self.feature_flags
  }
}

impl FeatureFlagsHolder {
  #[must_use]
  pub const fn new(feature_flags: FeatureFlags) -> Self {
    Self { feature_flags }
  }

  /// Consumes the feature flags holder and returns the raw pointer to it. This effectively leaks the object, so
  /// in order to avoid leaks the caller must ensure that the `destroy` is called with the returned
  /// value.
  #[must_use]
  pub fn into_raw<'a>(self) -> FeatureFlagsId<'a> {
    FeatureFlagsId {
      value: Box::into_raw(Box::new(self)) as i64,
      _lifetime: std::marker::PhantomData,
    }
  }

  /// Given a valid feature flags ID, destroys the feature flags holder and frees the memory associated with it.
  ///
  /// # Safety
  /// The provided id *must* correspond to the pointer of a valid `FeatureFlagsHolder` as returned by
  /// `into_raw`. This function *cannot* be called multiple times for the same id.
  pub unsafe fn destroy(id: i64) {
    let holder = Box::from_raw(id as *mut Self);
    drop(holder);
  }
}

impl<'a> From<FeatureFlagsId<'a>> for i64 {
  fn from(feature_flags: FeatureFlagsId<'a>) -> Self {
    feature_flags.value
  }
}
