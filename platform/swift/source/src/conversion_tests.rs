// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

use bd_bonjson::decoder::Value;
use objc::runtime::Object;
use std::collections::HashMap;
use crate::ffi::{make_nsstring, nsstring_into_string};
use super::{copy_from_objc, value_to_objc};

#[cfg(test)]
mod tests {
    use super::*;
    use objc::*;

    // Helper function to create an autorelease pool for tests
    fn with_autorelease_pool<F, R>(f: F) -> R
    where
        F: FnOnce() -> R,
    {
        unsafe {
            let pool_class = class!(NSAutoreleasePool);
            let pool: *mut Object = msg_send![pool_class, new];
            let result = f();
            let _: () = msg_send![pool, release];
            result
        }
    }

    #[test]
    fn test_copy_from_objc_string() {
        with_autorelease_pool(|| {
            unsafe {
                let test_string = "Hello, World!";
                let ns_string = make_nsstring(test_string);
                
                let result = copy_from_objc(*ns_string).unwrap();
                
                match result {
                    Value::String(s) => assert_eq!(s, test_string),
                    _ => panic!("Expected String, got {:?}", result),
                }
            }
        });
    }

    #[test]
    fn test_copy_from_objc_signed_number() {
        with_autorelease_pool(|| {
            unsafe {
                let number_class = class!(NSNumber);
                let test_value: i64 = -42;
                let ns_number = msg_send![number_class, numberWithLongLong: test_value];
                
                let result = copy_from_objc(ns_number).unwrap();
                
                match result {
                    Value::Signed(n) => assert_eq!(n, test_value),
                    _ => panic!("Expected Signed, got {:?}", result),
                }
            }
        });
    }

    #[test]
    fn test_copy_from_objc_unsigned_number() {
        with_autorelease_pool(|| {
            unsafe {
                let number_class = class!(NSNumber);
                let test_value: u64 = u64::MAX;
                let ns_number = msg_send![number_class, numberWithUnsignedLongLong: test_value];
                
                let result = copy_from_objc(ns_number).unwrap();
                
                match result {
                    Value::Unsigned(n) => assert_eq!(n, test_value),
                    _ => panic!("Expected Unsigned, got {:?}", result),
                }
            }
        });
    }

    #[test]
    fn test_copy_from_objc_float_number() {
        with_autorelease_pool(|| {
            unsafe {
                let number_class = class!(NSNumber);
                let test_value: f64 = 3.14159;
                let ns_number = msg_send![number_class, numberWithDouble: test_value];
                
                let result = copy_from_objc(ns_number).unwrap();
                
                match result {
                    Value::Float(f) => assert!((f - test_value).abs() < f64::EPSILON),
                    _ => panic!("Expected Float, got {:?}", result),
                }
            }
        });
    }

    #[test]
    fn test_copy_from_objc_empty_array() {
        with_autorelease_pool(|| {
            unsafe {
                let array_class = class!(NSArray);
                let empty_array = msg_send![array_class, array];
                
                let result = copy_from_objc(empty_array).unwrap();
                
                match result {
                    Value::Array(arr) => assert!(arr.is_empty()),
                    _ => panic!("Expected Array, got {:?}", result),
                }
            }
        });
    }

    #[test]
    fn test_copy_from_objc_array_with_elements() {
        with_autorelease_pool(|| {
            unsafe {
                let array_class = class!(NSMutableArray);
                let ns_array: *mut Object = msg_send![array_class, array];
                
                // Add test elements
                let string1 = make_nsstring("test1");
                let string2 = make_nsstring("test2");
                let number_class = class!(NSNumber);
                let number: *const Object = msg_send![number_class, numberWithLongLong: 42i64];
                
                let _: () = msg_send![ns_array, addObject: *string1];
                let _: () = msg_send![ns_array, addObject: *string2];
                let _: () = msg_send![ns_array, addObject: number];
                
                let result = copy_from_objc(ns_array).unwrap();
                
                match result {
                    Value::Array(arr) => {
                        assert_eq!(arr.len(), 3);
                        assert_eq!(arr[0], Value::String("test1".to_string()));
                        assert_eq!(arr[1], Value::String("test2".to_string()));
                        assert_eq!(arr[2], Value::Signed(42));
                    },
                    _ => panic!("Expected Array, got {:?}", result),
                }
            }
        });
    }

    #[test]
    fn test_copy_from_objc_empty_dictionary() {
        with_autorelease_pool(|| {
            unsafe {
                let dict_class = class!(NSDictionary);
                let empty_dict = msg_send![dict_class, dictionary];
                
                let result = copy_from_objc(empty_dict).unwrap();
                
                match result {
                    Value::Object(map) => assert!(map.is_empty()),
                    _ => panic!("Expected Object, got {:?}", result),
                }
            }
        });
    }

    #[test]
    fn test_copy_from_objc_dictionary_with_elements() {
        with_autorelease_pool(|| {
            unsafe {
                let dict_class = class!(NSMutableDictionary);
                let ns_dict: *mut Object = msg_send![dict_class, dictionary];
                
                // Add test elements
                let key1 = make_nsstring("key1");
                let value1 = make_nsstring("value1");
                let key2 = make_nsstring("key2");
                let number_class = class!(NSNumber);
                let value2: *const Object = msg_send![number_class, numberWithLongLong: 123i64];
                
                let _: () = msg_send![ns_dict, setObject: *value1 forKey: *key1];
                let _: () = msg_send![ns_dict, setObject: value2 forKey: *key2];
                
                let result = copy_from_objc(ns_dict).unwrap();
                
                match result {
                    Value::Object(map) => {
                        assert_eq!(map.len(), 2);
                        assert_eq!(map.get("key1"), Some(&Value::String("value1".to_string())));
                        assert_eq!(map.get("key2"), Some(&Value::Signed(123)));
                    },
                    _ => panic!("Expected Object, got {:?}", result),
                }
            }
        });
    }

    #[test]
    fn test_copy_from_objc_nested_structures() {
        with_autorelease_pool(|| {
            unsafe {
                // Create nested structure: { "outer": { "inner": ["a", "b"] } }
                let outer_dict_class = class!(NSMutableDictionary);
                let outer_dict: *mut Object = msg_send![outer_dict_class, dictionary];
                
                let inner_dict_class = class!(NSMutableDictionary);
                let inner_dict: *mut Object = msg_send![inner_dict_class, dictionary];
                
                let array_class = class!(NSMutableArray);
                let inner_array: *mut Object = msg_send![array_class, array];
                
                let string_a = make_nsstring("a");
                let string_b = make_nsstring("b");
                let _: () = msg_send![inner_array, addObject: *string_a];
                let _: () = msg_send![inner_array, addObject: *string_b];
                
                let inner_key = make_nsstring("inner");
                let _: () = msg_send![inner_dict, setObject: inner_array forKey: *inner_key];
                
                let outer_key = make_nsstring("outer");
                let _: () = msg_send![outer_dict, setObject: inner_dict forKey: *outer_key];
                
                let result = copy_from_objc(outer_dict).unwrap();
                
                match result {
                    Value::Object(outer_map) => {
                        assert_eq!(outer_map.len(), 1);
                        
                        match outer_map.get("outer") {
                            Some(Value::Object(inner_map)) => {
                                assert_eq!(inner_map.len(), 1);
                                
                                match inner_map.get("inner") {
                                    Some(Value::Array(arr)) => {
                                        assert_eq!(arr.len(), 2);
                                        assert_eq!(arr[0], Value::String("a".to_string()));
                                        assert_eq!(arr[1], Value::String("b".to_string()));
                                    },
                                    _ => panic!("Expected inner array"),
                                }
                            },
                            _ => panic!("Expected inner object"),
                        }
                    },
                    _ => panic!("Expected Object, got {:?}", result),
                }
            }
        });
    }

    #[test]
    fn test_copy_from_objc_null_pointer() {
        unsafe {
            let result = copy_from_objc(std::ptr::null());
            assert!(result.is_err());
            assert!(result.unwrap_err().to_string().contains("null pointer"));
        }
    }

    // Tests for value_to_objc function

    #[test]
    fn test_value_to_objc_string() {
        with_autorelease_pool(|| {
            unsafe {
                let test_string = "Hello, World!";
                let value = Value::String(test_string.to_string());
                
                let result = value_to_objc(&value).unwrap();
                let converted_string = nsstring_into_string(*result).unwrap();
                
                assert_eq!(converted_string, test_string);
            }
        });
    }

    #[test]
    fn test_value_to_objc_signed_number() {
        with_autorelease_pool(|| {
            unsafe {
                let test_value: i64 = -42;
                let value = Value::Signed(test_value);
                
                let result = value_to_objc(&value).unwrap();
                let converted_value: i64 = msg_send![*result, longLongValue];
                
                assert_eq!(converted_value, test_value);
            }
        });
    }

    #[test]
    fn test_value_to_objc_unsigned_number() {
        with_autorelease_pool(|| {
            unsafe {
                let test_value: u64 = u64::MAX;
                let value = Value::Unsigned(test_value);
                
                let result = value_to_objc(&value).unwrap();
                let converted_value: u64 = msg_send![*result, unsignedLongLongValue];
                
                assert_eq!(converted_value, test_value);
            }
        });
    }

    #[test]
    fn test_value_to_objc_float_number() {
        with_autorelease_pool(|| {
            unsafe {
                let test_value: f64 = 3.14159;
                let value = Value::Float(test_value);
                
                let result = value_to_objc(&value).unwrap();
                let converted_value: f64 = msg_send![*result, doubleValue];
                
                assert!((converted_value - test_value).abs() < f64::EPSILON);
            }
        });
    }

    #[test]
    fn test_value_to_objc_bool() {
        with_autorelease_pool(|| {
            unsafe {
                let value = Value::Bool(true);
                
                let result = value_to_objc(&value).unwrap();
                let converted_value: bool = msg_send![*result, boolValue];
                
                assert_eq!(converted_value, true);
            }
        });
    }

    #[test]
    fn test_value_to_objc_empty_array() {
        with_autorelease_pool(|| {
            unsafe {
                let value = Value::Array(Vec::new());
                
                let result = value_to_objc(&value).unwrap();
                let count: usize = msg_send![*result, count];
                
                assert_eq!(count, 0);
            }
        });
    }

    #[test]
    fn test_value_to_objc_array_with_elements() {
        with_autorelease_pool(|| {
            unsafe {
                let value = Value::Array(vec![
                    Value::String("test1".to_string()),
                    Value::String("test2".to_string()),
                    Value::Signed(42),
                ]);
                
                let result = value_to_objc(&value).unwrap();
                let count: usize = msg_send![*result, count];
                assert_eq!(count, 3);
                
                let item0: *const Object = msg_send![*result, objectAtIndex: 0];
                let item1: *const Object = msg_send![*result, objectAtIndex: 1];
                let item2: *const Object = msg_send![*result, objectAtIndex: 2];
                
                let string0 = nsstring_into_string(item0).unwrap();
                let string1 = nsstring_into_string(item1).unwrap();
                let number2: i64 = msg_send![item2, longLongValue];
                
                assert_eq!(string0, "test1");
                assert_eq!(string1, "test2");
                assert_eq!(number2, 42);
            }
        });
    }

    #[test]
    fn test_value_to_objc_empty_dictionary() {
        with_autorelease_pool(|| {
            unsafe {
                let value = Value::Object(HashMap::new());
                
                let result = value_to_objc(&value).unwrap();
                let count: usize = msg_send![*result, count];
                
                assert_eq!(count, 0);
            }
        });
    }

    #[test]
    fn test_value_to_objc_dictionary_with_elements() {
        with_autorelease_pool(|| {
            unsafe {
                let mut map = HashMap::new();
                map.insert("key1".to_string(), Value::String("value1".to_string()));
                map.insert("key2".to_string(), Value::Signed(123));
                let value = Value::Object(map);
                
                let result = value_to_objc(&value).unwrap();
                let count: usize = msg_send![*result, count];
                assert_eq!(count, 2);
                
                let key1 = make_nsstring("key1");
                let key2 = make_nsstring("key2");
                let value1: *const Object = msg_send![*result, objectForKey: *key1];
                let value2: *const Object = msg_send![*result, objectForKey: *key2];
                
                let string_value = nsstring_into_string(value1).unwrap();
                let number_value: i64 = msg_send![value2, longLongValue];
                
                assert_eq!(string_value, "value1");
                assert_eq!(number_value, 123);
            }
        });
    }

    #[test]
    fn test_value_to_objc_null() {
        with_autorelease_pool(|| {
            unsafe {
                let value = Value::Null;
                
                let result = value_to_objc(&value).unwrap();
                let is_null: bool = msg_send![*result, isKindOfClass: class!(NSNull)];
                
                assert!(is_null);
            }
        });
    }

    #[test]
    fn test_value_to_objc_none() {
        with_autorelease_pool(|| {
            unsafe {
                let value = Value::None;
                
                let result = value_to_objc(&value).unwrap();
                let is_null: bool = msg_send![*result, isKindOfClass: class!(NSNull)];
                
                assert!(is_null);
            }
        });
    }

    #[test]
    fn test_value_to_objc_nested_structures() {
        with_autorelease_pool(|| {
            unsafe {
                // Create nested structure: { "outer": { "inner": ["a", "b"] } }
                let mut inner_map = HashMap::new();
                inner_map.insert("inner".to_string(), Value::Array(vec![
                    Value::String("a".to_string()),
                    Value::String("b".to_string()),
                ]));
                
                let mut outer_map = HashMap::new();
                outer_map.insert("outer".to_string(), Value::Object(inner_map));
                
                let value = Value::Object(outer_map);
                
                let result = value_to_objc(&value).unwrap();
                
                // Verify the structure
                let outer_key = make_nsstring("outer");
                let inner_dict: *const Object = msg_send![*result, objectForKey: *outer_key];
                
                let inner_key = make_nsstring("inner");
                let inner_array: *const Object = msg_send![inner_dict, objectForKey: *inner_key];
                
                let count: usize = msg_send![inner_array, count];
                assert_eq!(count, 2);
                
                let item0: *const Object = msg_send![inner_array, objectAtIndex: 0];
                let item1: *const Object = msg_send![inner_array, objectAtIndex: 1];
                
                let string0 = nsstring_into_string(item0).unwrap();
                let string1 = nsstring_into_string(item1).unwrap();
                
                assert_eq!(string0, "a");
                assert_eq!(string1, "b");
            }
        });
    }

    // Round-trip tests to ensure bidirectional conversion works correctly
    
    #[test]
    fn test_round_trip_string() {
        with_autorelease_pool(|| {
            unsafe {
                let original = Value::String("Hello, World!".to_string());
                
                let objc_result = value_to_objc(&original).unwrap();
                let rust_result = copy_from_objc(*objc_result).unwrap();
                
                assert_eq!(original, rust_result);
            }
        });
    }

    #[test]
    fn test_round_trip_complex_structure() {
        with_autorelease_pool(|| {
            unsafe {
                let mut inner_map = HashMap::new();
                inner_map.insert("numbers".to_string(), Value::Array(vec![
                    Value::Signed(1),
                    Value::Unsigned(2),
                    Value::Float(3.14),
                    Value::Bool(true),
                ]));
                inner_map.insert("text".to_string(), Value::String("hello".to_string()));
                inner_map.insert("null_value".to_string(), Value::Null);
                
                let mut outer_map = HashMap::new();
                outer_map.insert("data".to_string(), Value::Object(inner_map));
                outer_map.insert("empty_array".to_string(), Value::Array(Vec::new()));
                
                let original = Value::Object(outer_map);
                
                let objc_result = value_to_objc(&original).unwrap();
                let rust_result = copy_from_objc(*objc_result).unwrap();
                
                assert_eq!(original, rust_result);
            }
        });
    }
}
