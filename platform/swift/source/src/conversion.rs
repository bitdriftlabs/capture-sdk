// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

use crate::ffi::{make_nsstring, nsstring_into_string};
use bd_bonjson::decoder::Value;
use objc::rc::StrongPtr;
use objc::runtime::Object;
use std::collections::HashMap;

#[derive(Debug)]
enum ObjcToRustWorkItem {
  ProcessObject {
    ptr: *const Object,
    result_id: usize,
  },
  InsertDictValue {
    key_str: String,
    value_id: usize,
    map_id: usize,
  },
  InsertArrayValue {
    value_id: usize,
    array_id: usize,
  },
  FinalizeDictionary {
    map_id: usize,
    result_id: usize,
  },
  FinalizeArray {
    array_id: usize,
    result_id: usize,
  },
}

#[derive(Debug)]
enum RustToObjcWorkItem {
  ProcessValue {
    value: Value,
    result_id: usize,
  },
  InsertDictValue {
    key: String,
    value_id: usize,
    dict_id: usize,
  },
  InsertArrayValue {
    value_id: usize,
    array_id: usize,
  },
  FinalizeDict {
    dict_id: usize,
    result_id: usize,
  },
  FinalizeArray {
    array_id: usize,
    result_id: usize,
  },
}


unsafe extern "C" {
  fn NSStringFromClass(cls: *const Object) -> *const Object;
}

pub(crate) unsafe fn objc_obj_class_name(s: *const Object) -> anyhow::Result<String> {
  let class: *const Object = unsafe { msg_send![s, class] };
  let name: *const Object = unsafe { NSStringFromClass(class) };
  let name_str = nsstring_into_string(name)?;
  Ok(name_str)
}

/// Deep-convert an Objective-C object to a Rust `Value`.
///
/// Note: Uses a non-recursive, stack-based approach to avoid a stack overflow.
///
/// # Conversions
/// - `NSString` → `Value::String`
/// - `NSNumber` (signed integers) → `Value::Signed`
/// - `NSNumber` (unsigned integers) → `Value::Unsigned`
/// - `NSNumber` (floating point) → `Value::Float`
/// - `NSArray` → `Value::Array`
/// - `NSDictionary` → `Value::Object` (`HashMap<String, Value>`)
/// - `NSNull` → `Value::Null`
///
/// # Algorithm
/// Stack-based work queue system with the following work items:
/// - `ProcessObject`: Generic Objective-C conversion
/// - `InsertDictValue`: Insert a value into a dictionary after processing
/// - `InsertArrayValue`: Insert a value into an array after processing
/// - `FinalizeDictionary`: Move a completed dictionary to its final location
/// - `FinalizeArray`: Move a completed array to its final location
///
/// For collections (arrays and dictionaries):
/// 1. Create intermediate storage with a unique ID
/// 2. Process array items in reverse order to maintain correct final ordering
/// 3. Use deferred insertion work items to handle nested structures
/// 4. Finalize the collection once all items are processed
///
/// # Arguments
/// * `ptr` - A pointer to an Objective-C object
///
/// # Returns
/// * `Ok(Value)` - The converted Rust value
/// * `Err(anyhow::Error)` - If the pointer is null, the object type is unsupported, or a conversion
///   error occurs
#[allow(clippy::cognitive_complexity)]
pub unsafe fn objc_value_to_rust(ptr: *const Object) -> anyhow::Result<Value> {
  if ptr.is_null() {
    anyhow::bail!("Cannot convert null pointer to Value");
  }

  let mut work_stack: Vec<ObjcToRustWorkItem> = Vec::new();
  let mut results: HashMap<usize, Value> = HashMap::new();
  let mut next_id = 0;

  let mut get_next_id = || {
    let id = next_id;
    next_id += 1;
    id
  };

  // Start with the root object
  let root_id = get_next_id();
  work_stack.push(ObjcToRustWorkItem::ProcessObject {
    ptr,
    result_id: root_id,
  });

  while let Some(work_item) = work_stack.pop() {
    match work_item {
      ObjcToRustWorkItem::ProcessObject { ptr, result_id } => {
        if msg_send![ptr, isKindOfClass: class!(NSDictionary)] {
          let count: usize = msg_send![ptr, count];

          if count > 0 {
            let map_id = get_next_id();
            results.insert(map_id, Value::Object(HashMap::new()));
            let all_keys: *const Object = msg_send![ptr, allKeys];

            // Add a work item to finalize the dictionary after all items are processed
            work_stack.push(ObjcToRustWorkItem::FinalizeDictionary { map_id, result_id });

            for i in 0 .. count {
              let key: *const Object = msg_send![all_keys, objectAtIndex: i];
              let key_str: String = nsstring_into_string(key)?;
              let value: *const Object = msg_send![ptr, objectForKey: key];

              let value_id = get_next_id();

              // Schedule the insertion after the value is processed
              work_stack.push(ObjcToRustWorkItem::InsertDictValue {
                key_str,
                value_id,
                map_id,
              });

              work_stack.push(ObjcToRustWorkItem::ProcessObject {
                ptr: value,
                result_id: value_id,
              });
            }
          } else {
            // Empty dictionary
            results.insert(result_id, Value::Object(HashMap::new()));
          }
        } else if msg_send![ptr, isKindOfClass: class!(NSArray)] {
          let count: usize = msg_send![ptr, count];

          if count > 0 {
            let array_id = get_next_id();
            results.insert(array_id, Value::Array(Vec::with_capacity(count)));

            // Add a work item to finalize the array after all items are processed
            work_stack.push(ObjcToRustWorkItem::FinalizeArray {
              array_id,
              result_id,
            });

            // Process array items in reverse order to maintain correct order with stack
            for i in (0 .. count).rev() {
              let item: *const Object = msg_send![ptr, objectAtIndex: i];
              let value_id = get_next_id();

              // Schedule the insertion after the value is processed
              work_stack.push(ObjcToRustWorkItem::InsertArrayValue { value_id, array_id });

              work_stack.push(ObjcToRustWorkItem::ProcessObject {
                ptr: item,
                result_id: value_id,
              });
            }
          } else {
            // Empty array
            results.insert(result_id, Value::Array(Vec::new()));
          }
        } else if msg_send![ptr, isKindOfClass: class!(NSString)] {
          let string_value = nsstring_into_string(ptr)?;
          results.insert(result_id, Value::String(string_value));
        } else if msg_send![ptr, isKindOfClass: class!(NSNumber)] {
          let num_i64: i64 = msg_send![ptr, longLongValue];
          let num_u64: u64 = msg_send![ptr, unsignedLongLongValue];
          let num_f64: f64 = msg_send![ptr, doubleValue];

          #[allow(clippy::float_cmp, clippy::cast_precision_loss)]
          let value = if num_f64 != num_i64 as f64 && num_f64 != num_u64 as f64 {
            Value::Float(num_f64)
          } else if num_u64 > i64::MAX as u64 {
            Value::Unsigned(num_u64)
          } else {
            Value::Signed(num_i64)
          };
          results.insert(result_id, value);
        } else if msg_send![ptr, isKindOfClass: class!(NSNull)] {
          results.insert(result_id, Value::Null);
        } else {
          anyhow::bail!(
            "Unsupported Objective-C type for conversion: {:?}",
            objc_obj_class_name(ptr)
          );
        }
      },

      ObjcToRustWorkItem::InsertDictValue {
        key_str,
        value_id,
        map_id,
      } => {
        let value = results.remove(&value_id).unwrap();
        if let Some(Value::Object(ref mut map)) = results.get_mut(&map_id) {
          map.insert(key_str, value);
        }
      },

      ObjcToRustWorkItem::InsertArrayValue { value_id, array_id } => {
        let value = results.remove(&value_id).unwrap();
        if let Some(Value::Array(ref mut vec)) = results.get_mut(&array_id) {
          // Push to maintain order (items are processed in reverse due to stack)
          vec.push(value);
        }
      },

      ObjcToRustWorkItem::FinalizeDictionary { map_id, result_id } => {
        // Move the completed map to the final result
        let final_map = results.remove(&map_id).unwrap();
        results.insert(result_id, final_map);
      },

      ObjcToRustWorkItem::FinalizeArray {
        array_id,
        result_id,
      } => {
        // Move the completed array to the final result
        let final_array = results.remove(&array_id).unwrap();
        results.insert(result_id, final_array);
      },
    }
  }

  results
    .remove(&root_id)
    .ok_or_else(|| anyhow::anyhow!("Failed to find root result"))
}

/// Deep-convert a Rust `Value` to its Objective-C equivalent.
///
/// Note: Uses a non-recursive, stack-based approach to avoid a stack overflow.
///
/// Returns a `StrongPtr` that holds a strong reference to the created object.
///
/// # Safety
/// The call to this method needs to be wrapped in an autorelease pool.
#[allow(clippy::cognitive_complexity)]
pub unsafe fn rust_value_to_objc(value: &Value) -> anyhow::Result<StrongPtr> {
  let mut work_stack: Vec<RustToObjcWorkItem> = Vec::new();
  let mut results: HashMap<usize, StrongPtr> = HashMap::new();
  let mut next_id = 0;

  let mut get_next_id = || {
    let id = next_id;
    next_id += 1;
    id
  };

  let root_id = get_next_id();
  work_stack.push(RustToObjcWorkItem::ProcessValue {
    value: value.clone(),
    result_id: root_id,
  });

  while let Some(work_item) = work_stack.pop() {
    match work_item {
      RustToObjcWorkItem::ProcessValue { value, result_id } => {
        match value {
          Value::String(s) => {
            let ns_string = make_nsstring(&s);
            results.insert(result_id, ns_string);
          },

          Value::Signed(n) => {
            let number_class = class!(NSNumber);
            let number = msg_send![number_class, numberWithLongLong: n];
            results.insert(result_id, StrongPtr::retain(number));
          },

          Value::Unsigned(n) => {
            let number_class = class!(NSNumber);
            let number = msg_send![number_class, numberWithUnsignedLongLong: n];
            results.insert(result_id, StrongPtr::retain(number));
          },

          Value::Float(f) => {
            let number_class = class!(NSNumber);
            let number = msg_send![number_class, numberWithDouble: f];
            results.insert(result_id, StrongPtr::retain(number));
          },

          Value::Bool(b) => {
            let number_class = class!(NSNumber);
            let number = msg_send![number_class, numberWithBool: b];
            results.insert(result_id, StrongPtr::retain(number));
          },

          Value::Array(arr) => {
            if arr.is_empty() {
              let array_class = class!(NSArray);
              let empty_array = msg_send![array_class, array];
              results.insert(result_id, StrongPtr::retain(empty_array));
            } else {
              let array_id = get_next_id();
              let mutable_array_class = class!(NSMutableArray);
              let ns_array: *mut Object =
                msg_send![mutable_array_class, arrayWithCapacity: arr.len()];
              results.insert(array_id, StrongPtr::retain(ns_array));

              // Schedule finalization
              work_stack.push(RustToObjcWorkItem::FinalizeArray {
                array_id,
                result_id,
              });

              // Process array items in reverse order to maintain correct order with stack
              for (_i, item) in arr.iter().enumerate().rev() {
                let value_id = get_next_id();

                // Schedule insertion
                work_stack.push(RustToObjcWorkItem::InsertArrayValue { value_id, array_id });

                work_stack.push(RustToObjcWorkItem::ProcessValue {
                  value: item.clone(),
                  result_id: value_id,
                });
              }
            }
          },

          Value::Object(map) => {
            if map.is_empty() {
              let dict_class = class!(NSDictionary);
              let empty_dict = msg_send![dict_class, dictionary];
              results.insert(result_id, StrongPtr::retain(empty_dict));
            } else {
              let dict_id = get_next_id();
              let mutable_dict_class = class!(NSMutableDictionary);
              let ns_dict: *mut Object =
                msg_send![mutable_dict_class, dictionaryWithCapacity: map.len()];
              results.insert(dict_id, StrongPtr::retain(ns_dict));

              // Schedule finalization
              work_stack.push(RustToObjcWorkItem::FinalizeDict { dict_id, result_id });

              // Process dictionary items
              for (key, val) in &map {
                let value_id = get_next_id();

                // Schedule insertion
                work_stack.push(RustToObjcWorkItem::InsertDictValue {
                  key: key.clone(),
                  value_id,
                  dict_id,
                });

                work_stack.push(RustToObjcWorkItem::ProcessValue {
                  value: val.clone(),
                  result_id: value_id,
                });
              }
            }
          },

          Value::Null | Value::None => {
            let null_class = class!(NSNull);
            let null_obj = msg_send![null_class, null];
            results.insert(result_id, StrongPtr::retain(null_obj));
          },
        }
      },

      RustToObjcWorkItem::InsertDictValue {
        key,
        value_id,
        dict_id,
      } => {
        let value_obj = results.remove(&value_id).unwrap();
        let dict_obj = results.get(&dict_id).unwrap();
        let ns_key = make_nsstring(&key);
        let () = msg_send![**dict_obj, setObject: *value_obj forKey: *ns_key];
      },

      RustToObjcWorkItem::InsertArrayValue { value_id, array_id } => {
        let value_obj = results.remove(&value_id).unwrap();
        let array_obj = results.get(&array_id).unwrap();
        let () = msg_send![**array_obj, addObject: *value_obj];
      },

      RustToObjcWorkItem::FinalizeDict { dict_id, result_id } => {
        let mutable_dict = results.remove(&dict_id).unwrap();
        let dict_class = class!(NSDictionary);
        let immutable_dict = msg_send![dict_class, dictionaryWithDictionary: *mutable_dict];
        results.insert(result_id, StrongPtr::retain(immutable_dict));
      },

      RustToObjcWorkItem::FinalizeArray {
        array_id,
        result_id,
      } => {
        let mutable_array = results.remove(&array_id).unwrap();
        let array_class = class!(NSArray);
        let immutable_array = msg_send![array_class, arrayWithArray: *mutable_array];
        results.insert(result_id, StrongPtr::retain(immutable_array));
      },
    }
  }

  results
    .remove(&root_id)
    .ok_or_else(|| anyhow::anyhow!("Failed to find root result"))
}
