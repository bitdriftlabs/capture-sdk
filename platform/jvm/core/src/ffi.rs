// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

use crate::jni::{initialize_class, initialize_method_handle, CachedClass, CachedMethod};
use ahash::AHashMap;
use anyhow::bail;
use bd_client_common::error::InvariantError;
use bd_log_primitives::LogMapData;
use bd_logger::{AnnotatedLogField, AnnotatedLogFields, DataValue, LogFieldKind, LogFieldValue, LogFields};
use jni::objects::{JMap, JObject, JObjectArray, JPrimitiveArray, ReleaseMode};
use jni::signature::{Primitive, ReturnType};
use jni::JNIEnv;
use ordered_float::NotNan;
use std::collections::HashMap;
use std::sync::OnceLock;

const FIELD_VALUE_BYTE_ARRAY: i32 = 0;
const FIELD_VALUE_STRING: i32 = 1;
const FIELD_VALUE_MAP: i32 = 2;

const MAP_VALUE_TYPE_STRING: u8 = 0x00;
const MAP_VALUE_TYPE_BYTES: u8 = 0x01;
const MAP_VALUE_TYPE_BOOL: u8 = 0x02;
const MAP_VALUE_TYPE_U64: u8 = 0x03;
const MAP_VALUE_TYPE_I64: u8 = 0x04;
const MAP_VALUE_TYPE_DOUBLE: u8 = 0x05;
const MAP_VALUE_TYPE_MAP: u8 = 0x06;
const MAP_VALUE_TYPE_ARRAY: u8 = 0x07;
const ROOT_TYPE_MAP: u8 = 0x01;
const ROOT_TYPE_ARRAY: u8 = 0x02;

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

struct MapDecoder<'a> {
  data: &'a [u8],
  pos: usize,
}

impl<'a> MapDecoder<'a> {
  fn new(data: &'a [u8]) -> Self {
    Self { data, pos: 0 }
  }

  fn decode_root(&mut self) -> anyhow::Result<DataValue> {
    match self.read_u8()? {
      ROOT_TYPE_MAP => self.decode_map(),
      ROOT_TYPE_ARRAY => self.decode_array(),
      value_type => bail!("unknown structured field root type: {value_type:#x}"),
    }
  }

  fn decode_map(&mut self) -> anyhow::Result<DataValue> {
    let entry_count = self.read_u32()? as usize;
    let mut entries = AHashMap::with_capacity(entry_count);
    for _ in 0 .. entry_count {
      entries.insert(self.read_string()?, self.decode_value()?);
    }
    Ok(DataValue::Map(LogMapData::new(entries)))
  }

  fn decode_value(&mut self) -> anyhow::Result<DataValue> {
    match self.read_u8()? {
      MAP_VALUE_TYPE_STRING => Ok(DataValue::String(self.read_string()?)),
      MAP_VALUE_TYPE_BYTES => {
        let len = self.read_u32()? as usize;
        Ok(DataValue::Bytes(self.read_bytes(len)?.into()))
      },
      MAP_VALUE_TYPE_BOOL => Ok(DataValue::Boolean(self.read_u8()? != 0)),
      MAP_VALUE_TYPE_U64 => Ok(DataValue::U64(self.read_u64()?)),
      MAP_VALUE_TYPE_I64 => Ok(DataValue::I64(self.read_u64()? as i64)),
      MAP_VALUE_TYPE_DOUBLE => Ok(DataValue::Double(
        NotNan::new(f64::from_bits(self.read_u64()?)).map_err(|_| anyhow::anyhow!("NaN value in map"))?,
      )),
      MAP_VALUE_TYPE_MAP => self.decode_map(),
      MAP_VALUE_TYPE_ARRAY => self.decode_array(),
      value_type => bail!("unknown map value type: {value_type:#x}"),
    }
  }

  fn decode_array(&mut self) -> anyhow::Result<DataValue> {
    let item_count = self.read_u32()? as usize;
    let mut items = Vec::with_capacity(item_count);
    for _ in 0 .. item_count {
      items.push(self.decode_value()?);
    }
    Ok(DataValue::Array(bd_log_primitives::LogArrayData::new(items)))
  }

  fn read_u8(&mut self) -> anyhow::Result<u8> {
    if self.pos >= self.data.len() { bail!("unexpected end of map data"); }
    let value = self.data[self.pos];
    self.pos += 1;
    Ok(value)
  }

  fn read_u32(&mut self) -> anyhow::Result<u32> {
    let bytes: [u8; 4] = self.read_bytes(4)?.try_into().unwrap();
    Ok(u32::from_le_bytes(bytes))
  }

  fn read_u64(&mut self) -> anyhow::Result<u64> {
    let bytes: [u8; 8] = self.read_bytes(8)?.try_into().unwrap();
    Ok(u64::from_le_bytes(bytes))
  }

  fn read_bytes(&mut self, len: usize) -> anyhow::Result<Vec<u8>> {
    if self.pos + len > self.data.len() { bail!("unexpected end of map data"); }
    let bytes = self.data[self.pos .. self.pos + len].to_vec();
    self.pos += len;
    Ok(bytes)
  }

  fn read_string(&mut self) -> anyhow::Result<String> {
    let len = self.read_u32()? as usize;
    String::from_utf8(self.read_bytes(len)?).map_err(Into::into)
  }
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
    FIELD_VALUE_MAP => {
      let field_value = FIELD_BYTE_ARRAY
        .get()
        .ok_or(InvariantError::Invariant)?
        .call_method(env, field_obj, ReturnType::Array, &[])?.l()?;
      let byte_array = JPrimitiveArray::from(field_value);
      let elements = unsafe { env.get_array_elements(&byte_array, ReleaseMode::NoCopyBack) }?;
      let bytes: &[u8] = unsafe { &*(std::ptr::from_ref(&*elements) as *const [i8] as *const [u8]) };
      MapDecoder::new(bytes).decode_root()?
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
pub fn string_arrays_to_annotated_fields(
  env: &mut JNIEnv<'_>,
  keys: &JObjectArray<'_>,
  values: &JObjectArray<'_>,
  kind: LogFieldKind,
) -> anyhow::Result<AnnotatedLogFields> {
  let len = env.get_array_length(keys)?;
  #[allow(clippy::cast_sign_loss)]
  let mut fields = AnnotatedLogFields::with_capacity(len as usize);

  for i in 0 .. len {
    env.with_local_frame(4, |env| -> anyhow::Result<()> {
      let key_obj = env.get_object_array_element(keys, i)?;
      let value_obj = env.get_object_array_element(values, i)?;

      let key = unsafe { env.get_string_unchecked(&key_obj.into()) }?
        .to_string_lossy()
        .to_string();
      let value = unsafe { env.get_string_unchecked(&value_obj.into()) }?
        .to_string_lossy()
        .to_string();

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
