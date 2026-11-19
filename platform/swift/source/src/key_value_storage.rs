// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

use crate::ffi::{make_nsstring, nsstring_into_string};
use objc::rc::StrongPtr;
use objc::runtime::Object;

//
// UserDefaultsStorage
//

#[allow(clippy::non_send_fields_in_send_ty)]
pub struct UserDefaultsStorage {
  user_defaults: StrongPtr,
}

impl Default for UserDefaultsStorage {
  fn default() -> Self {
    let mut user_defaults = {
      let defaults: *mut Object = unsafe { msg_send![class!(NSUserDefaults), alloc] };
      make_nsstring("io.bitdrift.storage").map_or_else(
        |_| unsafe { StrongPtr::new(std::ptr::null_mut()) },
        |suite_name| {
          let defaults: *mut Object = unsafe { msg_send![defaults, initWithSuiteName:*suite_name] };
          unsafe { StrongPtr::new(defaults) }
        },
      )
    };

    if user_defaults.is_null() {
      // Fallback to standard UserDefaults if the suite is not available.
      log::debug!("couldn't create specific UserDefaults, falling back to standard UserDefaults");
      user_defaults =
        unsafe { StrongPtr::retain(msg_send![class!(NSUserDefaults), standardUserDefaults]) }
    }

    Self { user_defaults }
  }
}

unsafe impl Sync for UserDefaultsStorage {}
unsafe impl Send for UserDefaultsStorage {}

impl bd_key_value::Storage for UserDefaultsStorage {
  fn set_string(&self, key: &str, value: &str) -> anyhow::Result<()> {
    objc::rc::autoreleasepool(|| {
      let key = make_nsstring(key)?;
      let value = make_nsstring(value)?;

      unsafe {
        let () = msg_send![*self.user_defaults, setObject:*value forKey:*key];
      };
      Ok::<_, anyhow::Error>(())
    })?;

    Ok(())
  }

  fn get_string(&self, key: &str) -> anyhow::Result<Option<String>> {
    objc::rc::autoreleasepool(|| {
      let key = make_nsstring(key)?;
      let value: *const Object = unsafe { msg_send![*self.user_defaults, objectForKey:*key] };

      if value.is_null() {
        return Ok(None);
      }

      unsafe { nsstring_into_string(value) }.map(Some)
    })
  }

  fn delete(&self, key: &str) -> anyhow::Result<()> {
    objc::rc::autoreleasepool(|| {
      let key = make_nsstring(key)?;
      unsafe { msg_send![*self.user_defaults, removeObjectForKey:*key] }
      Ok::<_, anyhow::Error>(())
    })?;

    Ok(())
  }
}
