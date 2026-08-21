// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

use super::{AndroidStaticFields, AppleStaticFields, Mobile};
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
  let metadata = Mobile::android(
    Some("app-id".to_string()),
    Some("1.2.3".to_string()),
    Some("14".to_string()),
    test_device(),
    "Pixel".to_string(),
    AndroidStaticFields {
      manufacturer: "Google".to_string(),
      app_version_code: 123,
      os_api_level: 35,
      architecture: "arm64-v8a".to_string(),
    },
  );

  let collected = bd_api::Metadata::collect_inner(&metadata);

  let android = metadata.android_static_fields();
  assert_eq!(
    android.map(|fields| fields.manufacturer.as_str()),
    Some("Google")
  );
  assert_eq!(android.map(|fields| fields.app_version_code), Some(123));
  assert_eq!(
    android.map(|fields| fields.architecture.as_str()),
    Some("arm64-v8a")
  );

  assert_eq!(collected.get("os_version"), Some(&"14".to_string()));
  assert_eq!(collected.get("_manufacturer"), Some(&"Google".to_string()));

  let initial_fields = metadata.static_log_fields();
  assert_eq!(
    initial_fields
      .get("app_id")
      .and_then(|value| value.as_str()),
    Some("app-id")
  );
  assert_eq!(
    initial_fields.get("os").and_then(|value| value.as_str()),
    Some("Android")
  );
  assert_eq!(
    initial_fields
      .get("app_version")
      .and_then(|value| value.as_str()),
    Some("1.2.3")
  );
  assert_eq!(
    initial_fields
      .get("os_version")
      .and_then(|value| value.as_str()),
    Some("14")
  );
  assert_eq!(
    initial_fields
      .get("_manufacturer")
      .and_then(|value| value.as_str()),
    Some("Google")
  );
  assert_eq!(
    initial_fields.get("model").and_then(|value| value.as_str()),
    Some("Pixel")
  );
  assert_eq!(
    initial_fields
      .get("_app_version_code")
      .and_then(|value| value.as_str()),
    Some("123")
  );
  assert_eq!(
    initial_fields
      .get("_os_api_level")
      .and_then(|value| value.as_str()),
    Some("35")
  );
  assert_eq!(
    initial_fields
      .get("_architecture")
      .and_then(|value| value.as_str()),
    Some("arm64-v8a")
  );
}

#[test]
fn collect_inner_omits_manufacturer_for_non_android() {
  let metadata = Mobile::apple(
    Some("app-id".to_string()),
    Some("1.2.3".to_string()),
    Some("18.0".to_string()),
    test_device(),
    "iPhone".to_string(),
    AppleStaticFields {
      build_number: "456".to_string(),
    },
  );

  let collected = bd_api::Metadata::collect_inner(&metadata);

  assert!(metadata.android_static_fields().is_none());

  assert_eq!(collected.get("os_version"), Some(&"18.0".to_string()));
  assert!(!collected.contains_key("_manufacturer"));

  let initial_fields = metadata.static_log_fields();
  assert_eq!(
    initial_fields
      .get("app_id")
      .and_then(|value| value.as_str()),
    Some("app-id")
  );
  assert_eq!(
    initial_fields.get("os").and_then(|value| value.as_str()),
    Some("iOS")
  );
  assert_eq!(
    initial_fields.get("model").and_then(|value| value.as_str()),
    Some("iPhone")
  );
  assert!(!initial_fields.contains_key("_manufacturer"));
  assert_eq!(
    initial_fields
      .get("_build_number")
      .and_then(|value| value.as_str()),
    Some("456")
  );
}
