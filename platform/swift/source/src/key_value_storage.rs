// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

use crate::ffi::nsstring_to_string;
use objc2::AnyThread;
use objc2::rc::Retained;
use objc2_foundation::{NSString, NSUserDefaults};

//
// UserDefaultsStorage
//

#[allow(clippy::non_send_fields_in_send_ty)]
pub struct UserDefaultsStorage {
  user_defaults: Retained<NSUserDefaults>,
}

impl Default for UserDefaultsStorage {
  fn default() -> Self {
    let suite_name = NSString::from_str("io.bitdrift.storage");
    let user_defaults = NSUserDefaults::initWithSuiteName(
      NSUserDefaults::alloc(),
      Some(&suite_name),
    )
    .unwrap_or_else(|| {
      // Fall back to standard UserDefaults if the suite is unavailable.
      log::debug!("couldn't create specific UserDefaults, falling back to standard UserDefaults");
      NSUserDefaults::standardUserDefaults()
    });

    Self { user_defaults }
  }
}

impl bd_key_value::Storage for UserDefaultsStorage {
  fn set_string(&self, key: &str, value: &str) -> anyhow::Result<()> {
    let key = NSString::from_str(key);
    let value = NSString::from_str(value);
    // `value` is an NSString, which is valid for NSUserDefaults persistence.
    unsafe { self.user_defaults.setObject_forKey(Some(&value), &key) };
    Ok(())
  }

  fn get_string(&self, key: &str) -> anyhow::Result<Option<String>> {
    let key = NSString::from_str(key);
    self
      .user_defaults
      .stringForKey(&key)
      .map(|value| nsstring_to_string(&value))
      .transpose()
  }

  fn delete(&self, key: &str) -> anyhow::Result<()> {
    let key = NSString::from_str(key);
    self.user_defaults.removeObjectForKey(&key);
    Ok(())
  }
}
