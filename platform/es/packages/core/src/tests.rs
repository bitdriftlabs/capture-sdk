// capture-es - bitdrift's ES SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

use crate::logger::{DiskStorage, RustLogger};
use bd_key_value::Storage;
use std::fs::remove_dir_all;
use tempdir::TempDir;

fn logger() -> anyhow::Result<RustLogger> {
  RustLogger::new(
    "api_key".to_string(),
    "api_address".to_string(),
    "sdk_directory".to_string(),
    "app_id".to_string(),
    "app_version".to_string(),
    "platform".to_string(),
    "platform_version".to_string(),
    "locale".to_string(),
  )
}

#[test]
fn can_initialize_logger() {
  let logger = logger();
  assert!(logger.is_ok());
}

#[test]
fn can_get_session_id() {
  let logger = logger().unwrap();
  let session_id = logger.session_id();
  assert!(!session_id.is_empty(), "The session id should not be empty");
}

#[test]
fn can_get_device_id() {
  let logger = logger().unwrap();
  let session_id = logger.device_id();
  assert!(!session_id.is_empty(), "The device id should not be empty");
}

#[test]
fn device_persisted_accross_runs() {
  let logger1 = logger().unwrap();
  let logger2 = logger().unwrap();
  assert_eq!(logger1.device_id(), logger2.device_id());
}

#[test]
fn session_id_changes_per_instance() {
  let logger1 = logger().unwrap();
  let logger2 = logger().unwrap();
  assert_ne!(logger1.session_id(), logger2.session_id());
}

#[test]
fn storage_can_persist_keys() {
  let path = TempDir::new("bd").unwrap().into_path();
  let storage = DiskStorage::new(path.clone()).unwrap();
  storage.set_string("key1", "value1").unwrap();
  storage.set_string("key2", "value3").unwrap();
  storage.set_string("key1", "value2").unwrap();
  let value = storage.get_string("key1");
  assert!(value.is_ok(), "The value should not raise an error");
  assert_eq!(value.unwrap(), Some("value2".to_string()));

  // Ensure creating a new storage instance loads the persisted values
  let storage = DiskStorage::new(path).unwrap();
  let value = storage.get_string("key1");
  assert_eq!(value.unwrap(), Some("value2".to_string()));

  // A non-existent key should return None
  let value = storage.get_string("key3");
  assert_eq!(value.unwrap(), None);

  // Weird keys should work
  let weird_key: &str = "/abc/def\\🎉";
  storage.set_string(weird_key, "yay!").unwrap();
  assert_eq!(
    storage.get_string(weird_key).unwrap(),
    Some("yay!".to_string())
  );
}

#[test]
fn storage_can_remove_keys() {
  let path = TempDir::new("bd").unwrap().into_path();
  let storage = DiskStorage::new(path).unwrap();
  storage.set_string("key1", "value1").unwrap();
  storage.set_string("key2", "value3").unwrap();
  storage.delete("key1").unwrap();
  assert_eq!(storage.get_string("key1").unwrap(), None);
  assert_eq!(
    storage.get_string("key2").unwrap(),
    Some("value3".to_string())
  );
}

#[test]
fn storage_reads_from_cache() {
  let path = TempDir::new("bd").unwrap().into_path();
  let storage = DiskStorage::new(path.clone()).unwrap();
  storage.set_string("key1", "value1").unwrap();
  storage.set_string("key2", "value2").unwrap();
  storage.set_string("key3", "value3").unwrap();

  remove_dir_all(path).unwrap();

  assert_eq!(
    storage.get_string("key1").unwrap(),
    Some("value1".to_string())
  );
  assert_eq!(
    storage.get_string("key2").unwrap(),
    Some("value2".to_string())
  );
  assert_eq!(
    storage.get_string("key3").unwrap(),
    Some("value3".to_string())
  );
  assert_eq!(storage.get_string("key4").unwrap(), None);

  storage.delete("key1").unwrap();
  assert_eq!(storage.get_string("key1").unwrap(), None);
}
