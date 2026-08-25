// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

#[cfg(test)]
#[path = "./ffi_test.rs"]
mod tests;

use crate::jni::{CachedClass, CachedMethod, initialize_class, initialize_method_handle};
use anyhow::bail;
use bd_client_common::error::InvariantError;
use bd_logger::{AnnotatedLogField, AnnotatedLogFields, LogFieldKind, LogFieldValue, LogFields};
use jni::Env;
use jni::objects::{JByteArray, JMap, JObject, JObjectArray, JString};
use jni::signature::{Primitive, ReturnType};
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

// `string_arrays_to_annotated_fields` receives Kotlin `Array<String>` parameters, so every
// element is a Java String. Consuming the local `JObject` before constructing the `JString` keeps
// a single owning wrapper while avoiding `cast_local`'s per-element `IsInstanceOf` JNI call.
fn string_array_element<'local>(env: &Env<'local>, object: JObject<'local>) -> JString<'local> {
  // SAFETY: `object` is a valid local reference returned by `JObjectArray::get_element` from a
  // Kotlin `Array<String>` parameter. `into_raw` consumes the only owning wrapper, and the
  // returned JString cannot outlive `env`'s local JNI frame.
  unsafe { JString::from_raw(env, object.into_raw()) }
}

pub(crate) fn initialize(env: &mut Env<'_>) -> anyhow::Result<()> {
  let string_field_class = initialize_class(
    env,
    "io/bitdrift/capture/providers/FieldValue$StringField",
    None,
  )?;
  let field_class = initialize_class(env, "io/bitdrift/capture/providers/Field", None)?;
  initialize_method_handle(
    env,
    field_class.class.as_ref(),
    "getKey",
    "()Ljava/lang/String;",
    &FIELD_KEY,
  )?;
  initialize_method_handle(
    env,
    field_class.class.as_ref(),
    "getValueType",
    "()I",
    &FIELD_VALUE_TYPE,
  )?;
  initialize_method_handle(
    env,
    field_class.class.as_ref(),
    "getByteArrayValue",
    "()[B",
    &FIELD_BYTE_ARRAY,
  )?;
  initialize_method_handle(
    env,
    field_class.class.as_ref(),
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
    binary_field.class.as_ref(),
    "getByteArrayValue",
    "()[B",
    &BINARY_FIELD_BYTE_ARRAY,
  )?;

  initialize_method_handle(
    env,
    string_field_class.class.as_ref(),
    "getStringValue",
    "()Ljava/lang/String;",
    &STRING_FIELD_STRING,
  )?;

  Ok(())
}

/// Extracts a single field (key and value) from a Java Field object.
/// This is the common extraction logic used by both array and list converters.
fn extract_field(
  env: &mut Env<'_>,
  field_obj: &JObject<'_>,
) -> anyhow::Result<(String, LogFieldValue)> {
  let key = FIELD_KEY
    .get()
    .ok_or(InvariantError::Invariant)?
    .call_method(env, field_obj, ReturnType::Object, &[])?
    .l()?;
  let key = env.cast_local::<JString<'_>>(key)?;
  let key = key.try_to_string(env)?;

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
      let value = env.convert_byte_array(env.cast_local::<JByteArray<'_>>(field_value)?)?;
      LogFieldValue::Bytes(value.into())
    },
    FIELD_VALUE_STRING => {
      let field_value = FIELD_STRING
        .get()
        .ok_or(InvariantError::Invariant)?
        .call_method(env, field_obj, ReturnType::Object, &[])?
        .l()?;
      let field_value = env.cast_local::<JString<'_>>(field_value)?;
      LogFieldValue::String(field_value.try_to_string(env)?)
    },
    _ => bail!("unknown field value type {value_type:?}"),
  };

  Ok((key, value))
}

/// Converts a Java array of Field objects into `AnnotatedLogFields`.
/// More efficient than List because arrays allow direct indexed access without iterator overhead.
pub fn jarray_to_annotated_fields(
  env: &mut Env<'_>,
  fields_array: &JObjectArray<'_>,
  kind: LogFieldKind,
) -> anyhow::Result<AnnotatedLogFields> {
  let len = fields_array.len(env)?;
  #[allow(clippy::cast_sign_loss)]
  let mut fields = AnnotatedLogFields::with_capacity(len);

  for i in 0 .. len {
    env.with_local_frame(16, |env| -> anyhow::Result<()> {
      let field_obj = fields_array.get_element(env, i)?;
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
  env: &Env<'_>,
  fields_array: &JObjectArray<'_>,
) -> anyhow::Result<LogFields> {
  let len = fields_array.len(env)?;
  #[allow(clippy::cast_sign_loss)]
  let mut fields = LogFields::with_capacity(len);

  for i in 0 .. len {
    env.with_local_frame(16, |env| -> anyhow::Result<()> {
      let field_obj = fields_array.get_element(env, i)?;
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
pub fn string_arrays_to_annotated_fields(
  env: &mut Env<'_>,
  keys: &JObjectArray<'_>,
  values: &JObjectArray<'_>,
  kind: LogFieldKind,
) -> anyhow::Result<AnnotatedLogFields> {
  let len = keys.len(env)?;
  let values_len = values.len(env)?;
  if len != values_len {
    bail!("keys and values must have the same length");
  }
  #[allow(clippy::cast_sign_loss)]
  let mut fields = AnnotatedLogFields::with_capacity(len);

  for i in 0 .. len {
    env.with_local_frame(4, |env| -> anyhow::Result<()> {
      let key_obj = keys.get_element(env, i)?;
      let value_obj = values.get_element(env, i)?;

      let key_obj = string_array_element(env, key_obj);
      let key = key_obj.try_to_string(env)?;
      let value_obj = string_array_element(env, value_obj);
      let value = value_obj.try_to_string(env)?;

      fields.insert(
        key.into(),
        AnnotatedLogField {
          value: LogFieldValue::String(value),
          kind,
        },
      );
      Ok(())
    })?;
  }

  Ok(fields)
}

// Converts passed rust hash map into Java HashMap.
pub(crate) fn map_to_jmap<'a, S: std::hash::BuildHasher>(
  env: &mut Env<'a>,
  map: &HashMap<&str, &str, S>,
) -> anyhow::Result<JObject<'a>> {
  let jmap_object: JObject<'a> = env.new_object(
    jni::jni_str!("java/util/HashMap"),
    jni::jni_sig!("()V"),
    &[],
  )?;
  let jmap = env.cast_local::<JMap<'_>>(jmap_object)?;

  for (key, value) in map {
    let key_string = env.new_string(key)?;
    let value_string = env.new_string(value)?;
    _ = jmap.put(env, &key_string, &value_string);
  }

  Ok(jmap.into())
}
