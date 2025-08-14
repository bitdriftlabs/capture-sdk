// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

#[cfg(test)]
#[path = "./conversion_tests.rs"]
mod conversion_tests;

use objc::runtime::Object;
use bd_bonjson::decoder::Value;
use crate::ffi::nsstring_into_string;
use crate::ffi::make_nsstring;
use std::collections::HashMap;
use objc::rc::StrongPtr;

// unsafe extern "C" {
//   fn NSStringFromClass(cls: *const Object) -> *const Object;
// }

pub(crate) unsafe fn class_name_as_string(s: *const Object) -> anyhow::Result<String> {
    Ok(format!("Class name: {:?}", s).to_string())
//   // println!("$ {:?}", s);
//   let class: *const Object = unsafe { msg_send![s, class] };
//   // println!("$$ {:?}", class);
//   let name: *const Object = unsafe { NSStringFromClass(class) };
//   // println!("$$$ name {:?}", name);
//   let name_str =  nsstring_into_string(name)?;
//   // println!("$$$ name str {:?}", name_str);
//   Ok(name_str)
}


#[allow(dead_code)]
pub(crate) unsafe fn copy_from_objc(ptr: *const Object) -> anyhow::Result<Value> {
  if ptr.is_null() {
    anyhow::bail!("Cannot convert null pointer to Value");
  }

  #[derive(Debug)]
  enum WorkItem {
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

  let mut work_stack: Vec<WorkItem> = Vec::new();
  let mut results: HashMap<usize, Value> = HashMap::new();
  let mut next_id = 0;
  
  let mut get_next_id = || {
    let id = next_id;
    next_id += 1;
    id
  };

  // Start with the root object
  let root_id = get_next_id();
  work_stack.push(WorkItem::ProcessObject {
    ptr,
    result_id: root_id,
  });

  while let Some(work_item) = work_stack.pop() {
    match work_item {
      WorkItem::ProcessObject { ptr, result_id } => {
        if msg_send![ptr, isKindOfClass: class!(NSDictionary)] {
          // println!("dict {}", class_name_as_string(ptr)?);
          let count: usize = msg_send![ptr, count];
          
          if count > 0 {
            let map_id = get_next_id();
            results.insert(map_id, Value::Object(HashMap::new()));
            let all_keys: *const Object = msg_send![ptr, allKeys];
            
            // Add a work item to finalize the dictionary after all items are processed
            work_stack.push(WorkItem::FinalizeDictionary {
              map_id,
              result_id,
            });
            
            // Process dictionary items in reverse order to maintain correct order
            for i in (0..count).rev() {
              let key: *const Object = msg_send![all_keys, objectAtIndex: i];
              let key_str: String = nsstring_into_string(key)?;
              let value: *const Object = msg_send![ptr, objectForKey: key];
              
              let value_id = get_next_id();
              
              // Schedule the insertion after the value is processed
              work_stack.push(WorkItem::InsertDictValue {
                key_str,
                value_id,
                map_id,
              });
              
              // Process the value
              work_stack.push(WorkItem::ProcessObject {
                ptr: value,
                result_id: value_id,
              });
            }
          } else {
            // Empty dictionary
            results.insert(result_id, Value::Object(HashMap::new()));
          }
        }
        else if msg_send![ptr, isKindOfClass: class!(NSArray)] {
          // println!("array {}", class_name_as_string(ptr)?);
          let count: usize = msg_send![ptr, count];
          
          if count > 0 {
            let array_id = get_next_id();
            results.insert(array_id, Value::Array(Vec::with_capacity(count)));
            
            // Add a work item to finalize the array after all items are processed
            work_stack.push(WorkItem::FinalizeArray {
              array_id,
              result_id,
            });
            
            // Process array items in reverse order to maintain correct order with stack
            for i in (0..count).rev() {
              let item: *const Object = msg_send![ptr, objectAtIndex: i];
              let value_id = get_next_id();
              
              // Schedule the insertion after the value is processed
              work_stack.push(WorkItem::InsertArrayValue {
                value_id,
                array_id,
              });
              
              // Process the item
              work_stack.push(WorkItem::ProcessObject {
                ptr: item,
                result_id: value_id,
              });
            }
          } else {
            // Empty array
            results.insert(result_id, Value::Array(Vec::new()));
          }
        }
        else if msg_send![ptr, isKindOfClass: class!(NSString)] {
          let string_value = nsstring_into_string(ptr)?;
          // println!("string {}: {:?}", class_name_as_string(ptr)?, string_value);
          results.insert(result_id, Value::String(string_value));
        }
        else if msg_send![ptr, isKindOfClass: class!(NSNumber)] {
          let num_i64: i64 = msg_send![ptr, longLongValue];
          let num_u64: u64 = msg_send![ptr, unsignedLongLongValue];
          let num_f64: f64 = msg_send![ptr, doubleValue];
          
          let value = if num_f64 != num_i64 as f64 && num_f64 != num_u64 as f64 {
            // println!("float {}: {}", class_name_as_string(ptr)?, num_f64);
            Value::Float(num_f64)
          } else if num_u64 > i64::MAX as u64 {
            // println!("unsigned {}: {}", class_name_as_string(ptr)?, num_u64);
            Value::Unsigned(num_u64)
          } else {
            // println!("signed {}: {}", class_name_as_string(ptr)?, num_i64);
            Value::Signed(num_i64)
          };
          results.insert(result_id, value);
        }
        else if msg_send![ptr, isKindOfClass: class!(NSNull)] {
          results.insert(result_id, Value::Null);
        }
        else {
          anyhow::bail!("Unsupported Objective-C type for conversion: {:?}", class_name_as_string(ptr));
        }
      },
      
      WorkItem::InsertDictValue { key_str, value_id, map_id } => {
        let value = results.remove(&value_id).unwrap();
        if let Some(Value::Object(ref mut map)) = results.get_mut(&map_id) {
          map.insert(key_str, value);
        }
      },
      
      WorkItem::InsertArrayValue { value_id, array_id } => {
        let value = results.remove(&value_id).unwrap();
        if let Some(Value::Array(ref mut vec)) = results.get_mut(&array_id) {
          // Push to maintain order (items are processed in reverse due to stack)
          vec.push(value);
        }
      },
      
      WorkItem::FinalizeDictionary { map_id, result_id } => {
        // Move the completed map to the final result
        let final_map = results.remove(&map_id).unwrap();
        results.insert(result_id, final_map);
      },
      
      WorkItem::FinalizeArray { array_id, result_id } => {
        // Move the completed array to the final result
        let final_array = results.remove(&array_id).unwrap();
        results.insert(result_id, final_array);
      },
    }
  }

  results.remove(&root_id).ok_or_else(|| anyhow::anyhow!("Failed to find root result"))
}

/// Converts a Rust `Value` to its Objective-C equivalent using a non-recursive, stack-based approach.
/// Returns a `StrongPtr` that holds a strong reference to the created object.
/// 
/// # Safety
/// The call to this method needs to be wrapped in an autorelease pool.
#[allow(dead_code)]
pub(crate) unsafe fn value_to_objc(value: &Value) -> anyhow::Result<StrongPtr> {
  #[derive(Debug)]
  enum WorkItem {
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

  let mut work_stack: Vec<WorkItem> = Vec::new();
  let mut results: HashMap<usize, StrongPtr> = HashMap::new();
  let mut next_id = 0;
  
  let mut get_next_id = || {
    let id = next_id;
    next_id += 1;
    id
  };

  // Start with the root value
  let root_id = get_next_id();
  work_stack.push(WorkItem::ProcessValue {
    value: value.clone(),
    result_id: root_id,
  });

  while let Some(work_item) = work_stack.pop() {
    match work_item {
      WorkItem::ProcessValue { value, result_id } => {
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
              // Empty array
              let array_class = class!(NSArray);
              let empty_array = msg_send![array_class, array];
              results.insert(result_id, StrongPtr::retain(empty_array));
            } else {
              let array_id = get_next_id();
              let mutable_array_class = class!(NSMutableArray);
              let ns_array: *mut Object = msg_send![mutable_array_class, arrayWithCapacity: arr.len()];
              results.insert(array_id, StrongPtr::retain(ns_array));
              
              // Schedule finalization
              work_stack.push(WorkItem::FinalizeArray {
                array_id,
                result_id,
              });
              
              // Process array items in reverse order to maintain correct order with stack
              for (_i, item) in arr.iter().enumerate().rev() {
                let value_id = get_next_id();
                
                // Schedule insertion
                work_stack.push(WorkItem::InsertArrayValue {
                  value_id,
                  array_id,
                });
                
                // Process the item
                work_stack.push(WorkItem::ProcessValue {
                  value: item.clone(),
                  result_id: value_id,
                });
              }
            }
          },
          
          Value::Object(map) => {
            if map.is_empty() {
              // Empty dictionary
              let dict_class = class!(NSDictionary);
              let empty_dict = msg_send![dict_class, dictionary];
              results.insert(result_id, StrongPtr::retain(empty_dict));
            } else {
              let dict_id = get_next_id();
              let mutable_dict_class = class!(NSMutableDictionary);
              let ns_dict: *mut Object = msg_send![mutable_dict_class, dictionaryWithCapacity: map.len()];
              results.insert(dict_id, StrongPtr::retain(ns_dict));
              
              // Schedule finalization
              work_stack.push(WorkItem::FinalizeDict {
                dict_id,
                result_id,
              });
              
              // Process dictionary items
              for (key, val) in map.iter() {
                let value_id = get_next_id();
                
                // Schedule insertion
                work_stack.push(WorkItem::InsertDictValue {
                  key: key.clone(),
                  value_id,
                  dict_id,
                });
                
                // Process the value
                work_stack.push(WorkItem::ProcessValue {
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
      
      WorkItem::InsertDictValue { key, value_id, dict_id } => {
        let value_obj = results.remove(&value_id).unwrap();
        let dict_obj = results.get(&dict_id).unwrap();
        let ns_key = make_nsstring(&key);
        let () = msg_send![**dict_obj, setObject: *value_obj forKey: *ns_key];
      },
      
      WorkItem::InsertArrayValue { value_id, array_id } => {
        let value_obj = results.remove(&value_id).unwrap();
        let array_obj = results.get(&array_id).unwrap();
        let () = msg_send![**array_obj, addObject: *value_obj];
      },
      
      WorkItem::FinalizeDict { dict_id, result_id } => {
        let mutable_dict = results.remove(&dict_id).unwrap();
        // Convert to immutable NSDictionary
        let dict_class = class!(NSDictionary);
        let immutable_dict = msg_send![dict_class, dictionaryWithDictionary: *mutable_dict];
        results.insert(result_id, StrongPtr::retain(immutable_dict));
      },
      
      WorkItem::FinalizeArray { array_id, result_id } => {
        let mutable_array = results.remove(&array_id).unwrap();
        // Convert to immutable NSArray
        let array_class = class!(NSArray);
        let immutable_array = msg_send![array_class, arrayWithArray: *mutable_array];
        results.insert(result_id, StrongPtr::retain(immutable_array));
      },
    }
  }

  results.remove(&root_id).ok_or_else(|| anyhow::anyhow!("Failed to find root result"))
}

//
//
//

#[allow(dead_code)]
pub(crate) unsafe fn copy_from_objc1(ptr: *const Object) -> anyhow::Result<Value> {
  #[derive(Debug)]
  enum WorkItem {
    ProcessObject {
      ptr: *const Object,
      result_key: String,
    },
    InsertDictValue {
      key_str: String,
      value_key: String,
      map_key: String,
    },
    InsertArrayValue {
      value_key: String,
      vec_key: String,
    },
  }

  let mut work_stack: Vec<WorkItem> = Vec::new();
  let mut results: HashMap<String, Value> = HashMap::new();
  let mut next_id = 0;
  
  // Helper to get next unique ID
  let mut get_next_key = || {
    let key = format!("result_{}", next_id);
    next_id += 1;
    key
  };

  // Start with the root object
  let root_key = get_next_key();
  work_stack.push(WorkItem::ProcessObject {
    ptr,
    result_key: root_key.clone(),
  });

  while let Some(work_item) = work_stack.pop() {
    match work_item {
      WorkItem::ProcessObject { ptr, result_key } => {
        
        if msg_send![ptr, isKindOfClass: class!(NSDictionary)] {
          let count: usize = msg_send![ptr, count];
          let map: HashMap<String, Value> = HashMap::new();
          
          if count > 0 {
            let all_keys: *const Object = msg_send![ptr, allKeys];
            let map_key = get_next_key();
            results.insert(map_key.clone(), Value::Object(HashMap::new()));
            
            // Process dictionary items in reverse order to maintain correct order
            for i in (0..count).rev() {
              let key: *const Object = msg_send![all_keys, objectAtIndex: i];
              let key_str: String = nsstring_into_string(key)?;
              let value: *const Object = msg_send![ptr, objectForKey: key];
              
              let value_key = get_next_key();
              
              // Schedule the insertion after the value is processed
              work_stack.push(WorkItem::InsertDictValue {
                key_str,
                value_key: value_key.clone(),
                map_key: map_key.clone(),
              });
              
              // Process the value
              work_stack.push(WorkItem::ProcessObject {
                ptr: value,
                result_key: value_key,
              });
            }
            
            // Move the map to the final result key when all processing is done
            let final_map = results.remove(&map_key).unwrap();
            results.insert(result_key, final_map);
          } else {
            results.insert(result_key, Value::Object(map));
          }
        }
        else if msg_send![ptr, isKindOfClass: class!(NSArray)] {
          let count: usize = msg_send![ptr, count];
          let vec: Vec<Value> = Vec::with_capacity(count);
          
          if count > 0 {
            let vec_key = get_next_key();
            results.insert(vec_key.clone(), Value::Array(Vec::with_capacity(count)));
            
            // Process array items in reverse order to maintain correct order with stack
            for i in (0..count).rev() {
              let item: *const Object = msg_send![ptr, objectAtIndex: i];
              let value_key = get_next_key();
              
              // Schedule the insertion after the value is processed
              work_stack.push(WorkItem::InsertArrayValue {
                value_key: value_key.clone(),
                vec_key: vec_key.clone(),
              });
              
              // Process the item
              work_stack.push(WorkItem::ProcessObject {
                ptr: item,
                result_key: value_key,
              });
            }
            
            // Move the vec to the final result key when all processing is done
            let final_vec = results.remove(&vec_key).unwrap();
            results.insert(result_key, final_vec);
          } else {
            results.insert(result_key, Value::Array(vec));
          }
        }
        else if msg_send![ptr, isKindOfClass: class!(NSNumber)] {
          let num_i64: i64 = msg_send![ptr, longLongValue];
          let num_u64: u64 = msg_send![ptr, unsignedLongLongValue];
          let num_f64: f64 = msg_send![ptr, doubleValue];
          let value = if num_f64 != num_i64 as f64 && num_f64 != num_u64 as f64 {
            Value::Float(num_f64)
          } else if num_u64 > i64::MAX as u64 {
            Value::Unsigned(num_u64)
          } else {
            Value::Signed(num_i64)
          };
          results.insert(result_key, value);
        }
        else if msg_send![ptr, isKindOfClass: class!(NSString)] {
          let string: String = nsstring_into_string(ptr)?;
          results.insert(result_key, Value::String(string));
        }
        else if msg_send![ptr, isKindOfClass: class!(NSNull)] {
          results.insert(result_key, Value::Null);
        }
        else {
          anyhow::bail!("Unsupported Objective-C type for conversion: {:?}", class_name_as_string(ptr));
        }
      },
      
      WorkItem::InsertDictValue { key_str, value_key, map_key } => {
        let value = results.remove(&value_key).unwrap();
        if let Some(Value::Object(ref mut map)) = results.get_mut(&map_key) {
          map.insert(key_str, value);
        }
      },
      
      WorkItem::InsertArrayValue { value_key, vec_key } => {
        let value = results.remove(&value_key).unwrap();
        if let Some(Value::Array(ref mut vec)) = results.get_mut(&vec_key) {
          // Push to maintain order (items are processed in reverse due to stack)
          vec.push(value);
        }
      },
    }
  }

  let root_key = "result_0".to_string();
  results.remove(&root_key).ok_or_else(|| anyhow::anyhow!("Failed to find root result"))
}
