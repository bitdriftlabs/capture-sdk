// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

// Helpers for safely interacting with Objective-C types.
use crate::ffi;
use ahash::AHashMap;
use anyhow::bail;
use bd_log_primitives::LogFieldKey;
use bd_logger::{
  AnnotatedLogField,
  AnnotatedLogFields,
  LogFieldKind,
  LogFieldValue,
  LogFields,
  StringOrBytes,
};
use objc::rc::StrongPtr;
use objc::runtime::Object;
use std::collections::HashMap;
use std::ffi::{CStr, CString};
use std::os::raw::c_char;

// NSASCIIStringEncoding
const NS_ASCII_STRING_ENCODING: u64 = 1;
// NSUTF8StringEncoding
const NS_UTF8_STRING_ENCODING: u64 = 4;

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

/// Converts a `NSString` to Rust String. The operation fails if the `NSString` does not
/// contain valid UTF-8.
///
/// # Safety
/// s must point to a valid `NSString` object.
pub(crate) unsafe fn nsstring_into_string(s: *const Object) -> anyhow::Result<String> {
  debug_check_class!(s, NSString);
  if s.is_null() {
    anyhow::bail!("Platform UTF-8 error: Passed NSString is equal to nil")
  }

  let cstr: *const c_char = unsafe { msg_send![s, cStringUsingEncoding: NS_UTF8_STRING_ENCODING] };

  let cstr = if cstr.is_null() {
    let str: *mut Object = {
      let data: *mut Object = unsafe {
        msg_send![s, dataUsingEncoding: NS_ASCII_STRING_ENCODING allowLossyConversion: true]
      };

      if data.is_null() {
        anyhow::bail!("Platform UTF-8 error: dataUsingEncoding(ASCII) returned null");
      }

      let str = {
        let class: *mut Object = msg_send![class!(NSString), alloc];
        let str =
          StrongPtr::new(msg_send![class, initWithData: data encoding: NS_ASCII_STRING_ENCODING]);
        if str.is_null() {
          anyhow::bail!("Platform UTF-8 error: initWithData:encoding(ASCII) returned null");
        }

        str
      };

      str.autorelease()
    };

    let cstr: *const c_char =
      unsafe { msg_send![str, cStringUsingEncoding: NS_UTF8_STRING_ENCODING] };

    if cstr.is_null() {
      anyhow::bail!("Platform UTF-8 error: cStringUsingEncoding(UTF8) returned null");
    }

    cstr
  } else {
    cstr
  };

  Ok(CStr::from_ptr(cstr).to_str()?.to_string())
}

/// Converts a Rust `String` into a `NSString`. Returned `StrongPtr` holds a strong reference to
/// an underlying `NSString` instance that's also autoreleased. Note that the implementations
/// does two copies of the s bytes.
///
/// # Safety
/// The call to the method needs to be wrapped in an autorelease pool.
pub fn make_nsstring(s: &str) -> anyhow::Result<StrongPtr> {
  let string_cls = class!(NSString);
  let c_str = CString::new(s)?;
  Ok(unsafe { StrongPtr::retain(msg_send![string_cls, stringWithUTF8String: c_str.as_ptr()]) })
}

/// Creates an empty `NSString`.
#[must_use]
pub fn make_empty_nsstring() -> StrongPtr {
  let string_cls = class!(NSString);
  unsafe { StrongPtr::retain(msg_send![string_cls, string]) }
}

/// Specifies a conversion between a Objective-C Object and a arbitrary Rust type.
pub trait FromObjcObject<'a> {
  /// Converts a Objective-C object into another reference type.
  ///
  /// # Safety
  /// Fundamentally any calls into Objective-C will be unsafe, and relies on us passing the
  /// correct type and carefully managing assumptions. In general this function assumes that
  /// the provided pointer is valid and of an appropriate type (depending on which type
  /// implements this trait). The lifetime of the returned is likely only valid as long as the
  /// underlying Objective C object does not change, which cannot be formally verified by Rust.
  unsafe fn from_objc(ptr: *const Object) -> anyhow::Result<&'a Self>;
}

impl<'a> FromObjcObject<'a> for [u8] {
  unsafe fn from_objc(ptr: *const Object) -> anyhow::Result<&'a Self> {
    debug_check_class!(ptr, NSData);

    let length: usize = msg_send![ptr, length];
    let bytes: *const u8 = msg_send![ptr, bytes];

    Ok(std::slice::from_raw_parts(bytes, length))
  }
}

pub fn convert_map<S: ::std::hash::BuildHasher>(
  map: &HashMap<&str, &str, S>,
) -> anyhow::Result<StrongPtr> {
  let objc_headers = unsafe { StrongPtr::new(msg_send![class!(NSMutableDictionary), new]) };
  for (key, value) in map {
    unsafe {
      let () =
        msg_send![*objc_headers, setObject:*make_nsstring(value)? forKey:*make_nsstring(key)?];
    };
  }

  Ok(objc_headers)
}

const FIELD_TYPE_STRING: usize = 0;
const FIELD_TYPE_DATA: usize = 1;

/// Converts a `NSArray` into a `AnnotatedLogFields` of references to the underlying data.
/// # Safety
/// This assumes that the provided ptr refers to a `NSArray<Field>`.
pub unsafe fn convert_annotated_fields(
  ptr: *const Object,
  kind: LogFieldKind,
) -> anyhow::Result<AnnotatedLogFields> {
  convert_fields_helper(ptr, |value| AnnotatedLogField { value, kind })
}

/// Converts a `NSArray` into a `LogFields` of references to the underlying data.
/// # Safety
/// This assumes that the provided ptr refers to a `NSArray<Field>`.
pub unsafe fn convert_fields(ptr: *const Object) -> anyhow::Result<LogFields> {
  convert_fields_helper(ptr, Into::into)
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

    let field_key: String = ffi::nsstring_into_string(msg_send![field, key])?;
    let field_type: usize = msg_send![field, type];
    let field_value: *const Object = msg_send![field, data];

    let value = match field_type {
      FIELD_TYPE_STRING => {
        let string_value: String = ffi::nsstring_into_string(field_value)
          .map_err(|e| e.context(format!("field {field_key:?}")))?;
        StringOrBytes::String(string_value)
      },
      FIELD_TYPE_DATA => {
        let data_value = FromObjcObject::from_objc(field_value)? as &[u8];
        StringOrBytes::Bytes(data_value.to_vec())
      },
      _ => bail!("unknown field value type: {field_type:?}"),
    };

    fields.insert(field_key.into(), converter(value));
  }

  Ok(fields)
}

/// Converts a `NSArray` of feature flag tuples into a `Vec<(String, Option<String>)>`.
/// # Safety
/// This assumes that the provided ptr refers to a `NSArray` of objects with `flag` and `variant`
/// properties.
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
    let flag_obj: *const Object = msg_send![feature_flag, flag];
    let flag = nsstring_into_string(flag_obj)?;

    // Extract variant (which can be nil)
    let variant_obj: *const Object = msg_send![feature_flag, variant];
    let variant = if variant_obj.is_null() {
      None
    } else {
      Some(nsstring_into_string(variant_obj)?)
    };

    flags.push((flag, variant));
  }

  Ok(flags)
}
