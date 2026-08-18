// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

use super::{map_to_jmap, string_arrays_to_annotated_fields};
use crate::test_jvm::with_env;
use anyhow::{Result, anyhow};
use bd_logger::{LogFieldKind, LogFieldValue};
use jni::JNIEnv;
use jni::objects::{JMap, JObject, JObjectArray, JString};
use std::collections::HashMap;

const TEST_ENTRY_COUNT: usize = 65;

fn string_array<'local>(
  env: &mut JNIEnv<'local>,
  values: &[String],
) -> Result<JObjectArray<'local>> {
  let length = values.len().try_into()?;
  let array = env.new_object_array(length, "java/lang/String", JObject::null())?;

  env.with_local_frame(i32::try_from(values.len() + 1)?, |env| -> Result<()> {
    for (index, value) in values.iter().enumerate() {
      let value = env.new_string(value)?;
      env.set_object_array_element(&array, i32::try_from(index)?, &value)?;
    }
    Ok(())
  })?;

  Ok(array)
}

#[test]
fn string_arrays_convert_all_entries() -> Result<()> {
  with_env(|env| -> Result<()> {
    let values: Vec<_> = (0 .. TEST_ENTRY_COUNT)
      .map(|index| format!("value-{index}"))
      .collect();
    let keys: Vec<_> = (0 .. TEST_ENTRY_COUNT)
      .map(|index| format!("key-{index}"))
      .collect();
    let keys_array = string_array(env, &keys)?;
    let values_array = string_array(env, &values)?;

    let fields =
      string_arrays_to_annotated_fields(env, &keys_array, &values_array, LogFieldKind::Custom)?;

    assert_eq!(fields.len(), TEST_ENTRY_COUNT);
    for (key, value) in keys.iter().zip(values) {
      let field = fields
        .get(key.as_str())
        .ok_or_else(|| anyhow!("fields are missing {key:?}"))?;
      assert_eq!(field.value, LogFieldValue::String(value));
      assert_eq!(field.kind, LogFieldKind::Custom);
    }
    Ok(())
  })
}

#[test]
fn string_arrays_convert_empty_arrays() -> Result<()> {
  with_env(|env| -> Result<()> {
    let keys = string_array(env, &[])?;
    let values = string_array(env, &[])?;

    let fields = string_arrays_to_annotated_fields(env, &keys, &values, LogFieldKind::Ootb)?;

    assert!(fields.is_empty());
    Ok(())
  })
}

#[test]
fn string_arrays_reject_mismatched_lengths() -> Result<()> {
  with_env(|env| -> Result<()> {
    let keys = string_array(env, &["key".to_owned()])?;
    let values = string_array(env, &[])?;

    assert!(string_arrays_to_annotated_fields(env, &keys, &values, LogFieldKind::Custom).is_err());
    assert!(!env.exception_check()?);
    Ok(())
  })
}

#[test]
fn map_to_jmap_preserves_each_string_mapping() -> Result<()> {
  with_env(|env| -> Result<()> {
    let fields = HashMap::from([("first", "one"), ("second", "two")]);
    let java_map = map_to_jmap(env, &fields)?;
    let java_map = JMap::from_env(env, &java_map)?;

    for (key, expected_value) in fields {
      let map_key = key;
      let key = env.new_string(key)?;
      let value = java_map
        .get(env, &key)?
        .ok_or_else(|| anyhow!("map is missing {map_key:?}"))?;
      let value = JString::from(value);
      assert_eq!(env.get_string(&value)?.to_string_lossy(), expected_value);
    }
    Ok(())
  })
}
