// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

use super::Mobile;
use bd_api::Platform;
use bd_key_value::{Storage, Store};
use parking_lot::Mutex;
use std::collections::HashMap;
use std::sync::Arc;

struct TestStorage {
  values: Mutex<HashMap<String, String>>,
}

impl Storage for TestStorage {
  fn set_string(&self, key: &str, value: &str) -> anyhow::Result<()> {
    self
      .values
      .lock()
      .insert(key.to_string(), value.to_string());
    Ok(())
  }

  fn get_string(&self, key: &str) -> anyhow::Result<Option<String>> {
    Ok(self.values.lock().get(key).cloned())
  }

  fn delete(&self, key: &str) -> anyhow::Result<()> {
    self.values.lock().remove(key);
    Ok(())
  }
}

fn test_device() -> Arc<bd_logger::Device> {
  let store = Arc::new(Store::new(Box::new(TestStorage {
    values: Mutex::new(HashMap::new()),
  })));

  Arc::new(bd_logger::Device::new(store))
}

#[test]
fn collect_inner_includes_os_version_and_android_manufacturer() {
  let metadata = Mobile {
    app_id: Some("app-id".to_string()),
    app_version: Some("1.2.3".to_string()),
    platform: Platform::Android,
    os: "android".to_string(),
    device: test_device(),
    os_version: Some("14".to_string()),
    manufacturer: Some("Google".to_string()),
    model: "Pixel".to_string(),
  };

  let collected = bd_api::Metadata::collect_inner(&metadata);

  assert_eq!(collected.get("os_version"), Some(&"14".to_string()));
  assert_eq!(collected.get("_manufacturer"), Some(&"Google".to_string()));
}

#[test]
fn collect_inner_omits_manufacturer_for_non_android() {
  let metadata = Mobile {
    app_id: Some("app-id".to_string()),
    app_version: Some("1.2.3".to_string()),
    platform: Platform::Apple,
    os: "ios".to_string(),
    device: test_device(),
    os_version: Some("18.0".to_string()),
    manufacturer: Some("Apple".to_string()),
    model: "iPhone".to_string(),
  };

  let collected = bd_api::Metadata::collect_inner(&metadata);

  assert_eq!(collected.get("os_version"), Some(&"18.0".to_string()));
  assert!(!collected.contains_key("_manufacturer"));
}
