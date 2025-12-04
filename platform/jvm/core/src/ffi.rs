// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

use crate::jni::{initialize_class, initialize_method_handle, CachedClass, CachedMethod};
use anyhow::bail;
use bd_client_common::error::InvariantError;
use bd_logger::{AnnotatedLogField, AnnotatedLogFields, LogFieldKind, LogFieldValue, LogFields};
use jni::objects::{JList, JMap, JObject, JPrimitiveArray};
use jni::signature::{Primitive, ReturnType};
use jni::JNIEnv;
use std::collections::HashMap;
use std::sync::OnceLock;

const FIELD_VALUE_BYTE_ARRAY: i32 = 0;
const FIELD_VALUE_STRING: i32 = 1;

// Cached classes
static BINARY_FIELD: OnceLock<CachedClass> = OnceLock::new();

// Cached method IDs

static FIELD_KEY: OnceLock<CachedMethod> = OnceLock::new();
static FIELD_VALUE_TYPE: OnceLock<CachedMethod> = OnceLock::new();
static FIELD_BYTE_ARRAY: OnceLock<CachedMethod> = OnceLock::new();
static FIELD_STRING: OnceLock<CachedMethod> = OnceLock::new();

static BINARY_FIELD_BYTE_ARRAY: OnceLock<CachedMethod> = OnceLock::new();
static STRING_FIELD_STRING: OnceLock<CachedMethod> = OnceLock::new();

static FEATURE_FLAG_GET_NAME: OnceLock<CachedMethod> = OnceLock::new();
static FEATURE_FLAG_GET_VARIANT: OnceLock<CachedMethod> = OnceLock::new();

pub(crate) fn initialize(env: &mut JNIEnv<'_>) -> anyhow::Result<()> {
  let field_class = initialize_class(env, "io/bitdrift/capture/providers/Field", None)?;
  initialize_method_handle(
    env,
    &field_class.class,
    "getKey",
    "()Ljava/lang/String;",
    &FIELD_KEY,
  )?;
  initialize_method_handle(
    env,
    &field_class.class,
    "getValueType",
    "()I",
    &FIELD_VALUE_TYPE,
  )?;
  initialize_method_handle(
    env,
    &field_class.class,
    "getByteArrayValue",
    "()[B",
    &FIELD_BYTE_ARRAY,
  )?;
  initialize_method_handle(
    env,
    &field_class.class,
    "getStringValue",
    "()Ljava/lang/String;",
    &FIELD_STRING,
  )?;

  let binary_field = initialize_class(
    env,
    "io/bitdrift/capture/providers/FieldValue$BinaryField",
    Some(&BINARY_FIELD),
  )?;
  initialize_method_handle(
    env,
    &binary_field.class,
    "getByteArrayValue",
    "()[B",
    &BINARY_FIELD_BYTE_ARRAY,
  )?;

  initialize_method_handle(
    env,
    "io/bitdrift/capture/providers/FieldValue$StringField",
    "getStringValue",
    "()Ljava/lang/String;",
    &STRING_FIELD_STRING,
  )?;

  initialize_method_handle(
    env,
    "io/bitdrift/capture/FeatureFlag",
    "getName",
    "()Ljava/lang/String;",
    &FEATURE_FLAG_GET_NAME,
  )?;
  initialize_method_handle(
    env,
    "io/bitdrift/capture/FeatureFlag",
    "getVariant",
    "()Ljava/lang/String;",
    &FEATURE_FLAG_GET_VARIANT,
  )?;

  Ok(())
}

pub(crate) fn jobject_list_to_fields(
  env: &mut JNIEnv<'_>,
  object: &JObject<'_>,
) -> anyhow::Result<LogFields> {
  let list = JList::from_env(env, object)?;
  let size = list.size(env)?;

  // SAFETY: the size of an array should always be >= 0.
  let mut fields = LogFields::with_capacity(size.try_into()?);

  let mut iter = list.iter(env)?;
  while let Some(obj) = iter.next(env)? {
    env.with_local_frame(16, |env| -> anyhow::Result<()> {
      // Key

      let key = FIELD_KEY
        .get()
        .ok_or(InvariantError::Invariant)?
        .call_method(env, &obj, ReturnType::Object, &[])?
        .l()?;
      let key = unsafe { env.get_string_unchecked(&key.into()) }?
        .to_string_lossy()
        .to_string();

      // Field value: String or Byte Array

      let value_type = FIELD_VALUE_TYPE
        .get()
        .ok_or(InvariantError::Invariant)?
        .call_method(env, &obj, ReturnType::Primitive(Primitive::Int), &[])?
        .i()?;
      let value = match value_type {
        FIELD_VALUE_BYTE_ARRAY => {
          let field_value = FIELD_BYTE_ARRAY
            .get()
            .ok_or(InvariantError::Invariant)?
            .call_method(env, &obj, ReturnType::Array, &[])?
            .l()?;

          let value = env.convert_byte_array(JPrimitiveArray::from(field_value))?;
          LogFieldValue::Bytes(value)
        },
        FIELD_VALUE_STRING => {
          let field_value = FIELD_STRING
            .get()
            .ok_or(InvariantError::Invariant)?
            .call_method(env, &obj, ReturnType::Object, &[])?
            .l()?;
          LogFieldValue::String(
            unsafe { env.get_string_unchecked(&field_value.into()) }?
              .to_string_lossy()
              .to_string(),
          )
        },
        _ => bail!("unknown field value type {value_type:?}"),
      };
      fields.insert(key.into(), value);
      Ok(())
    })?;
  }

  Ok(fields)
}

/// Returns `AnnotatedLogFields` copied from the provided `JMap`.
/// Internally does a lossy conversion into UTF-8 per [`to_string_lossy`](
/// https://docs.rs/jni/latest/jni/strings/struct.JNIStr.html#method.to_string_lossy).
pub fn jobject_map_to_fields(
  env: &mut JNIEnv<'_>,
  object: &JObject<'_>,
  kind: LogFieldKind,
) -> anyhow::Result<AnnotatedLogFields> {
  let mut fields = AnnotatedLogFields::new();

  let map = JMap::from_env(env, object)?;
  let mut iter = map.iter(env)?;

  while let Some((key, value)) = iter.next(env)? {
    // Push a local frame to ensure references are cleaned up after each iteration
    env.with_local_frame(16, |env| -> anyhow::Result<()> {
      let key = unsafe { env.get_string_unchecked((&key).into()) }?
        .to_string_lossy()
        .to_string();

      let value = if env.is_instance_of(
        &value,
        &BINARY_FIELD.get().ok_or(InvariantError::Invariant)?.class,
      )? {
        let field_value = BINARY_FIELD_BYTE_ARRAY
          .get()
          .ok_or(InvariantError::Invariant)?
          .call_method(env, &value, ReturnType::Array, &[])?
          .l()?;

        let value = env.convert_byte_array(JPrimitiveArray::from(field_value))?;
        LogFieldValue::Bytes(value)
      } else {
        let field_value = STRING_FIELD_STRING
          .get()
          .ok_or(InvariantError::Invariant)?
          .call_method(env, &value, ReturnType::Object, &[])?
          .l()?;
        LogFieldValue::String(
          unsafe { env.get_string_unchecked(&field_value.into()) }?
            .to_string_lossy()
            .to_string(),
        )
      };

      fields.insert(key.into(), AnnotatedLogField { value, kind });
      Ok(())
    })?;
  }

  Ok(fields)
}

// Converts passed rust hash map into Java HashMap.
pub(crate) fn map_to_jmap<'a, S: std::hash::BuildHasher>(
  env: &mut JNIEnv<'a>,
  map: &HashMap<&str, &str, S>,
) -> anyhow::Result<JObject<'a>> {
  let jmap_object: JObject<'a> = env.new_object("java/util/HashMap", "()V", &[])?;
  let jmap = JMap::from_env(env, &jmap_object)?;

  for (key, value) in map {
    let key_string = env.new_string(key)?;
    let value_string = env.new_string(value)?;
    _ = jmap.put(env, &key_string, &value_string);
  }

  Ok(jmap_object)
}
