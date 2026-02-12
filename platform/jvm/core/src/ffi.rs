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
use jni::objects::{JMap, JObject, JObjectArray, JPrimitiveArray, JString};
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

  Ok(())
}

/// Extracts a single field (key and value) from a Java Field object.
/// This is the common extraction logic used by both array and list converters.
fn extract_field(
  env: &mut JNIEnv<'_>,
  field_obj: &JObject<'_>,
) -> anyhow::Result<(String, LogFieldValue)> {
  let key = FIELD_KEY
    .get()
    .ok_or(InvariantError::Invariant)?
    .call_method(env, field_obj, ReturnType::Object, &[])?
    .l()?;
  let key = unsafe { env.get_string_unchecked(&key.into()) }?
    .to_string_lossy()
    .to_string();

  let value_type = FIELD_VALUE_TYPE
    .get()
    .ok_or(InvariantError::Invariant)?
    .call_method(env, field_obj, ReturnType::Primitive(Primitive::Int), &[])?
    .i()?;

  let value = match value_type {
    FIELD_VALUE_BYTE_ARRAY => {
      let field_value = FIELD_BYTE_ARRAY
        .get()
        .ok_or(InvariantError::Invariant)?
        .call_method(env, field_obj, ReturnType::Array, &[])?
        .l()?;
      let value = env.convert_byte_array(JPrimitiveArray::from(field_value))?;
      LogFieldValue::Bytes(value.into())
    },
    FIELD_VALUE_STRING => {
      let field_value = FIELD_STRING
        .get()
        .ok_or(InvariantError::Invariant)?
        .call_method(env, field_obj, ReturnType::Object, &[])?
        .l()?;
      LogFieldValue::String(
        unsafe { env.get_string_unchecked(&field_value.into()) }?
          .to_string_lossy()
          .to_string(),
      )
    },
    _ => bail!("unknown field value type {value_type:?}"),
  };

  Ok((key, value))
}

/// Converts a Java array of Field objects into `AnnotatedLogFields`.
/// More efficient than List because arrays allow direct indexed access without iterator overhead.
pub fn jarray_to_annotated_fields(
  env: &mut JNIEnv<'_>,
  fields_array: &JObjectArray<'_>,
  kind: LogFieldKind,
) -> anyhow::Result<AnnotatedLogFields> {
  let len = env.get_array_length(fields_array)?;
  #[allow(clippy::cast_sign_loss)]
  let mut fields = AnnotatedLogFields::with_capacity(len as usize);

  for i in 0 .. len {
    env.with_local_frame(16, |env| -> anyhow::Result<()> {
      let field_obj = env.get_object_array_element(fields_array, i)?;
      let (key, value) = extract_field(env, &field_obj)?;
      fields.insert(key.into(), AnnotatedLogField { value, kind });
      Ok(())
    })?;
  }

  Ok(fields)
}

/// Converts a Java array of Field objects into `LogFields`.
/// Similar to `jarray_to_annotated_fields` but returns `LogFields` without annotations.
pub(crate) fn jarray_to_fields(
  env: &mut JNIEnv<'_>,
  fields_array: &JObjectArray<'_>,
) -> anyhow::Result<LogFields> {
  let len = env.get_array_length(fields_array)?;
  #[allow(clippy::cast_sign_loss)]
  let mut fields = LogFields::with_capacity(len as usize);

  for i in 0 .. len {
    env.with_local_frame(16, |env| -> anyhow::Result<()> {
      let field_obj = env.get_object_array_element(fields_array, i)?;
      let (key, value) = extract_field(env, &field_obj)?;
      fields.insert(key.into(), value);
      Ok(())
    })?;
  }

  Ok(fields)
}

/// Converts parallel Java String arrays (keys and values) into `AnnotatedLogFields`.
/// This is more efficient than using Field objects because it avoids:
/// 1. Creating Field wrapper objects on the Kotlin side
/// 2. Multiple JNI method calls per field (getKey, getValueType, getValue)
///
/// The keys and values arrays must have the same length - keys[i] corresponds to values[i].
/// Returns empty fields if keys is null.
pub fn nullable_string_arrays_to_annotated_fields(
  env: &mut JNIEnv<'_>,
  keys: &JObjectArray<'_>,
  values: &JObjectArray<'_>,
  kind: LogFieldKind,
) -> anyhow::Result<AnnotatedLogFields> {
  if keys.is_null() {
    return Ok(AnnotatedLogFields::new());
  }
  string_arrays_to_annotated_fields(env, keys, values, kind)
}

/// The keys and values arrays must have the same length - keys[i] corresponds to values[i].
pub fn string_arrays_to_annotated_fields(
  env: &mut JNIEnv<'_>,
  keys: &JObjectArray<'_>,
  values: &JObjectArray<'_>,
  kind: LogFieldKind,
) -> anyhow::Result<AnnotatedLogFields> {
  let len = env.get_array_length(keys)?;
  #[allow(clippy::cast_sign_loss)]
  let len_usize = len as usize;
  let mut fields = AnnotatedLogFields::with_capacity(len_usize);

  if len == 0 {
    return Ok(fields);
  }

  for i in 0 .. len {
    let key_obj = env.get_object_array_element(keys, i)?;
    let value_obj = env.get_object_array_element(values, i)?;

    let key_jstr: JString<'_> = key_obj.into();
    let value_jstr: JString<'_> = value_obj.into();

    // Extract strings and convert to owned immediately so we can release JNI refs
    let key_str = {
      let key = unsafe { env.get_string_unchecked(&key_jstr) }?;
      key.to_string_lossy().into_owned()
    };
    let value_str = {
      let value = unsafe { env.get_string_unchecked(&value_jstr) }?;
      value.to_string_lossy().into_owned()
    };

    fields.insert(
      key_str.into(),
      AnnotatedLogField {
        value: LogFieldValue::String(value_str),
        kind,
      },
    );

    // Clean up local references to avoid exhausting JNI local ref table
    env.delete_local_ref(key_jstr)?;
    env.delete_local_ref(value_jstr)?;
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
