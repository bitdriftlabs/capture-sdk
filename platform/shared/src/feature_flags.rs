// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

use bd_feature_flags::FeatureFlags;

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

crate::impl_holder_deref!(FeatureFlagsHolder, feature_flags, FeatureFlags);
crate::impl_holder_into_raw!(FeatureFlagsHolder, FeatureFlagsId);

impl FeatureFlagsHolder {
  #[must_use]
  pub const fn new(feature_flags: FeatureFlags) -> Self {
    Self { feature_flags }
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

  /// Retrieves a feature flag by name.
  ///
  /// Returns the feature flag if it exists, or `None` if no flag
  /// with the given name is found.
  ///
  /// # Arguments
  ///
  /// * `key` - The name of the feature flag to retrieve
  #[must_use]
  pub fn get(&self, key: &str) -> Option<bd_feature_flags::FeatureFlag> {
    self.feature_flags.get(key)
  }

  /// Sets or updates a feature flag.
  ///
  /// Creates a new feature flag with the given name and variant, or updates an existing flag.
  /// The flag is immediately stored in persistent storage and receives
  /// a timestamp indicating when it was last modified.
  ///
  /// # Arguments
  ///
  /// * `key` - The name of the feature flag to set or update
  /// * `variant` - The variant value for the flag:
  ///   - `Some(string)` sets the flag with the specified variant
  ///   - `None` sets the flag without a variant (simple boolean-style flag)
  ///
  /// # Returns
  ///
  /// Returns `Ok(())` on success, or an error if the flag cannot be stored.
  pub fn set(&mut self, key: &str, variant: Option<&str>) -> anyhow::Result<()> {
    self.feature_flags.set(key, variant)
  }

  /// Removes all feature flags from persistent storage.
  ///
  /// This method deletes all feature flags, clearing the persistent storage.
  /// This operation cannot be undone.
  ///
  /// # Returns
  ///
  /// Returns `Ok(())` on success, or an error if the storage cannot be cleared.
  pub fn clear(&mut self) -> anyhow::Result<()> {
    self.feature_flags.clear()
  }

  /// Synchronizes in-memory changes to persistent storage.
  ///
  /// This method ensures that all feature flag changes made since the last sync
  /// are written to disk. While changes are typically persisted automatically,
  /// calling this method guarantees that data is flushed to storage immediately.
  ///
  /// # Returns
  ///
  /// Returns `Ok(())` on successful synchronization, or an error if the write operation fails.
  pub fn sync(&self) -> anyhow::Result<()> {
    self.feature_flags.sync()
  }

  /// Returns a `HashMap` containing all feature flags.
  ///
  /// This method provides access to all feature flags as a standard
  /// Rust `HashMap`. This is useful for iterating over all flags or performing
  /// bulk operations. The `HashMap` is generated on-demand from the persistent storage.
  ///
  /// # Returns
  ///
  /// A `HashMap<String, FeatureFlag>` containing all flags.
  #[must_use]
  pub fn as_hashmap(&self) -> std::collections::HashMap<String, bd_feature_flags::FeatureFlag> {
    self.feature_flags.as_hashmap()
  }

  /// Returns a reference to the underlying key-value store's `HashMap`,
  /// allowing direct access to the raw stored values.
  /// This should only be used internally to avoid unnecessary cloning.
  #[must_use]
  pub fn underlying_hashmap(&self) -> &std::collections::HashMap<String, bd_bonjson::Value> {
    self.feature_flags.underlying_hashmap()
  }
}
