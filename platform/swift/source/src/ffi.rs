// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

// Helpers for safely interacting with Objective-C types.
use crate::ffi;
use ahash::AHashMap;
use anyhow::{Context, bail};
use bd_log_primitives::LogFieldKey;
use bd_logger::{
  AnnotatedLogField,
  AnnotatedLogFields,
  DataValue,
  LogFieldKind,
  LogFieldValue,
  LogFields,
};
use objc::rc::StrongPtr;
use objc::runtime::Object;
use objc2::Message;
use objc2::rc::Retained;
use objc2::runtime::{AnyObject, ProtocolObject};
use objc2_foundation::{NSData, NSMutableDictionary, NSString};
use std::collections::HashMap;
use std::sync::Arc;

#[macro_export]
macro_rules! debug_check_class {
      ($e:expr, $class:ident) => {
        debug_assert!(
          $e.is_null() || unsafe { msg_send![$e, isKindOfClass: class!($class)] },
          "value of {:?} not of expected type {}",
          $e,
          stringify!($class)
        );
      };
    }

/// Converts an owned `objc2` object to the legacy pointer owner used at the C ABI boundary.
///
/// The bridge still exposes `objc::runtime::Object` pointers to Swift. Keep that representation
/// at the boundary while using the typed `objc2` representation within Foundation operations.
pub(crate) fn retained_to_strong_ptr<T: Message>(object: Retained<T>) -> StrongPtr {
  // The legacy bridge returns raw autoreleased pointers. Preserve that lifetime contract while
  // temporarily owning a StrongPtr for callers that still use the `objc` crate API.
  let ptr = Retained::autorelease_ptr(object).cast::<Object>();
  unsafe { StrongPtr::retain(ptr) }
}

/// Erases a typed Foundation object's class while retaining its Objective-C ownership.
pub(crate) fn into_any_object<T: Message>(object: Retained<T>) -> Retained<AnyObject> {
  // Every Objective-C class has the same object representation as `AnyObject`.
  unsafe { Retained::cast_unchecked(object) }
}

/// Views a non-null Objective-C pointer from the C ABI as an `objc2` object.
///
/// # Safety
///
/// `ptr` must point to a live Objective-C object for the returned reference's lifetime.
pub(crate) unsafe fn objc_object_from_ptr<'a>(ptr: *const Object) -> anyhow::Result<&'a AnyObject> {
  if ptr.is_null() {
    anyhow::bail!("received a null Objective-C object");
  }

  Ok(unsafe { &*ptr.cast::<AnyObject>() })
}

/// Views a non-null `NSString` pointer from the C ABI as a typed Foundation string.
///
/// # Safety
///
/// `ptr` must point to a live `NSString` for the returned reference's lifetime.
pub(crate) unsafe fn nsstring_from_ptr<'a>(ptr: *const Object) -> anyhow::Result<&'a NSString> {
  let object = unsafe { objc_object_from_ptr(ptr) }?;
  object
    .downcast_ref::<NSString>()
    .ok_or_else(|| anyhow::anyhow!("expected NSString at Objective-C bridge boundary"))
}

/// Views a non-null `NSData` pointer from the C ABI as typed Foundation data.
///
/// # Safety
///
/// `ptr` must point to a live `NSData` for the returned reference's lifetime.
pub(crate) unsafe fn nsdata_from_ptr<'a>(ptr: *const Object) -> anyhow::Result<&'a NSData> {
  let object = unsafe { objc_object_from_ptr(ptr) }?;
  object
    .downcast_ref::<NSData>()
    .ok_or_else(|| anyhow::anyhow!("expected NSData at Objective-C bridge boundary"))
}

const NS_ASCII_STRING_ENCODING: usize = 1;
const NS_UTF8_STRING_ENCODING: usize = 4;

/// Converts an `NSString` to a Rust `String`, preserving the bridge's legacy lossy fallback.
///
/// Some `NSString` values can contain invalid UTF-16. Formatting such a value directly would
/// require objc2 to assume valid Unicode, whereas the old bridge explicitly converted it through
/// ASCII with lossy conversion. Prefer UTF-8 and retain that fallback for malformed values.
pub(crate) fn nsstring_to_string(s: &NSString) -> anyhow::Result<String> {
  let data = s
    .dataUsingEncoding(NS_UTF8_STRING_ENCODING)
    .or_else(|| s.dataUsingEncoding_allowLossyConversion(NS_ASCII_STRING_ENCODING, true))
    .ok_or_else(|| anyhow::anyhow!("could not encode NSString as UTF-8 or lossy ASCII"))?;

  String::from_utf8(data.to_vec()).context("NSString encoding produced invalid UTF-8")
}

/// Converts an `NSString` to an Arc-backed Rust string.
pub(crate) fn nsstring_to_arc_str(s: &NSString) -> anyhow::Result<Arc<str>> {
  Ok(Arc::from(nsstring_to_string(s)?))
}

/// Converts an `NSString` C-ABI pointer to Rust `String`.
///
/// # Safety
/// If `s` is non-null, it must point to a live `NSString` for the duration of this call.
pub(crate) unsafe fn nsstring_into_string(s: *const Object) -> anyhow::Result<String> {
  nsstring_to_string(unsafe { nsstring_from_ptr(s) }?)
}

/// Converts an `NSString` C-ABI pointer to an Arc-backed Rust string.
///
/// # Safety
/// If `s` is non-null, it must point to a live `NSString` for the duration of this call.
pub(crate) unsafe fn nsstring_into_arc_str(s: *const Object) -> anyhow::Result<Arc<str>> {
  nsstring_to_arc_str(unsafe { nsstring_from_ptr(s) }?)
}

/// Converts a Rust string into an `NSString` retained by the legacy bridge owner.
pub fn make_nsstring(s: &str) -> anyhow::Result<StrongPtr> {
  Ok(retained_to_strong_ptr(NSString::from_str(s)))
}

/// Creates an empty `NSString`.
#[must_use]
pub fn make_empty_nsstring() -> StrongPtr {
  retained_to_strong_ptr(NSString::from_str(""))
}

pub fn convert_map<S: ::std::hash::BuildHasher>(
  map: &HashMap<&str, &str, S>,
) -> anyhow::Result<StrongPtr> {
  let objc_headers = NSMutableDictionary::<NSString, NSString>::dictionaryWithCapacity(map.len());
  for (key, value) in map {
    let key = NSString::from_str(key);
    let value = NSString::from_str(value);
    let key = ProtocolObject::from_ref(&*key);
    // The typed key and value satisfy the Objective-C dictionary's generic parameters.
    unsafe { objc_headers.setObject_forKey(&value, key) };
  }

  Ok(retained_to_strong_ptr(objc_headers))
}

const FIELD_TYPE_STRING: usize = 0;
const FIELD_TYPE_DATA: usize = 1;

/// Converts a `NSArray` into a `AnnotatedLogFields` of references to the underlying data.
/// # Safety
/// `ptr` must either be null or point to a live `NSArray<Field>` for the duration of this call.
pub unsafe fn convert_annotated_fields(
  ptr: *const Object,
  kind: LogFieldKind,
) -> anyhow::Result<AnnotatedLogFields> {
  unsafe { convert_fields_helper(ptr, |value| AnnotatedLogField { value, kind }) }
}

/// Converts a `NSArray` into a `LogFields` of references to the underlying data.
/// # Safety
/// `ptr` must either be null or point to a live `NSArray<Field>` for the duration of this call.
pub unsafe fn convert_fields(ptr: *const Object) -> anyhow::Result<LogFields> {
  unsafe { convert_fields_helper(ptr, Into::into) }
}

unsafe fn convert_fields_helper<FieldValue>(
  ptr: *const Object,
  converter: impl Fn(LogFieldValue) -> FieldValue,
) -> anyhow::Result<AHashMap<LogFieldKey, FieldValue>> {
  debug_check_class!(ptr, NSArray);

  // Helps us to avoid having to call to make a `count` Objective-C call below.
  if ptr.is_null() {
    return Ok(AHashMap::default());
  }

  let count: usize = msg_send![ptr, count];

  let mut fields = AHashMap::default();
  for i in 0 .. count {
    // TODO(snowp): Figure out how to use objc/2 to better model ths.
    let field: *const Object = msg_send![ptr, objectAtIndex: i];

    let field_key: String = unsafe { ffi::nsstring_into_string(msg_send![field, key]) }?;
    let field_type: usize = msg_send![field, type];
    let field_value: *const Object = msg_send![field, data];

    let value = match field_type {
      FIELD_TYPE_STRING => {
        let string_value: String = unsafe { ffi::nsstring_into_string(field_value) }
          .map_err(|e| e.context(format!("field {field_key:?}")))?;
        DataValue::String(string_value)
      },
      FIELD_TYPE_DATA => {
        let data_value = unsafe { nsdata_from_ptr(field_value) }?;
        DataValue::Bytes(data_value.to_vec().into())
      },
      _ => bail!("unknown field value type: {field_type:?}"),
    };

    fields.insert(field_key.into(), converter(value));
  }

  Ok(fields)
}

/// Converts a `NSArray` of feature flag tuples into a `Vec<(String, Option<String>)>`.
/// # Safety
/// `ptr` must either be null or point to a live `NSArray` of objects with `flag` and `variant`
/// properties for the duration of this call.
pub unsafe fn convert_feature_flags_array(
  ptr: *const Object,
) -> anyhow::Result<Vec<(String, Option<String>)>> {
  debug_check_class!(ptr, NSArray);

  // Handle null array
  if ptr.is_null() {
    return Ok(Vec::new());
  }

  let count: usize = msg_send![ptr, count];
  let mut flags = Vec::with_capacity(count);

  for i in 0 .. count {
    let feature_flag: *const Object = msg_send![ptr, objectAtIndex: i];

    // Extract flag name
    let flag_obj: *const Object = msg_send![feature_flag, name];
    let flag = unsafe { nsstring_into_string(flag_obj) }?;

    // Extract variant (which can be nil)
    let variant_obj: *const Object = msg_send![feature_flag, variant];
    let variant = if variant_obj.is_null() {
      None
    } else {
      Some(unsafe { nsstring_into_string(variant_obj) }?)
    };

    flags.push((flag, variant));
  }

  Ok(flags)
}
