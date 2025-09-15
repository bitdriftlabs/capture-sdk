// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

use bd_feature_flags::FeatureFlags;
use std::ops::{Deref, DerefMut};

// Use the generic FFI ID system for FeatureFlagsId
crate::ffi_id_for!(FeatureFlagsHolder, FeatureFlagsId);

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
    unsafe { FeatureFlagsId::from_raw(Box::into_raw(Box::new(self)) as i64) }
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
