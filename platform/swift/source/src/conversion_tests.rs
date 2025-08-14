// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

// This file provides C export functions to test the conversion functions from Swift/Objective-C
// The actual conversion functions require the Objective-C runtime to be available, so they
// cannot be tested as pure Rust unit tests.

use bd_bonjson::decoder::Value;
use objc::runtime::Object;
use std::collections::HashMap;
use crate::conversion::{copy_from_objc, value_to_objc};

/// Test helper: Convert an NSString to a Rust Value and back to NSString
/// Returns the converted NSString, or null if conversion failed
#[no_mangle]
pub extern "C" fn test_string_round_trip(ns_string: *const Object) -> *const Object {
    if ns_string.is_null() {
        return std::ptr::null();
    }
    
    unsafe {
        match copy_from_objc(ns_string) {
            Ok(value) => {
                match value_to_objc(&value) {
                    Ok(result) => *result,
                    Err(_) => std::ptr::null(),
                }
            },
            Err(_) => std::ptr::null(),
        }
    }
}

/// Test helper: Convert an NSArray to a Rust Value and back to NSArray
/// Returns the converted NSArray, or null if conversion failed
#[no_mangle]
pub extern "C" fn test_array_round_trip(ns_array: *const Object) -> *const Object {
    if ns_array.is_null() {
        return std::ptr::null();
    }
    
    unsafe {
        match copy_from_objc(ns_array) {
            Ok(value) => {
                match value_to_objc(&value) {
                    Ok(result) => *result,
                    Err(_) => std::ptr::null(),
                }
            },
            Err(_) => std::ptr::null(),
        }
    }
}

/// Test helper: Convert an NSDictionary to a Rust Value and back to NSDictionary
/// Returns the converted NSDictionary, or null if conversion failed
#[no_mangle]
pub extern "C" fn test_dictionary_round_trip(ns_dict: *const Object) -> *const Object {
    if ns_dict.is_null() {
        return std::ptr::null();
    }
    
    unsafe {
        match copy_from_objc(ns_dict) {
            Ok(value) => {
                match value_to_objc(&value) {
                    Ok(result) => *result,
                    Err(_) => std::ptr::null(),
                }
            },
            Err(_) => std::ptr::null(),
        }
    }
}

/// Test helper: Convert an NSNumber to a Rust Value and back to NSNumber
/// Returns the converted NSNumber, or null if conversion failed
#[no_mangle]
pub extern "C" fn test_number_round_trip(ns_number: *const Object) -> *const Object {
    if ns_number.is_null() {
        return std::ptr::null();
    }
    
    unsafe {
        match copy_from_objc(ns_number) {
            Ok(value) => {
                match value_to_objc(&value) {
                    Ok(result) => *result,
                    Err(_) => std::ptr::null(),
                }
            },
            Err(_) => std::ptr::null(),
        }
    }
}

/// Test helper: Create a simple Rust Value and convert it to Objective-C
/// Creates { "string": "test", "number": 42, "array": ["a", "b"], "bool": true, "null": null }
#[no_mangle]
pub extern "C" fn test_create_complex_objc_structure() -> *const Object {
    unsafe {
        let mut map = HashMap::new();
        map.insert("string".to_string(), Value::String("test".to_string()));
        map.insert("number".to_string(), Value::Signed(42));
        map.insert("array".to_string(), Value::Array(vec![
            Value::String("a".to_string()),
            Value::String("b".to_string()),
        ]));
        map.insert("bool".to_string(), Value::Bool(true));
        map.insert("null".to_string(), Value::Null);
        
        let value = Value::Object(map);
        
        match value_to_objc(&value) {
            Ok(result) => *result,
            Err(_) => std::ptr::null(),
        }
    }
}

/// Test helper: Test null pointer handling
/// Returns 1 if null pointer is properly rejected, 0 otherwise
#[no_mangle]
pub extern "C" fn test_null_pointer_handling() -> i32 {
    unsafe {
        match copy_from_objc(std::ptr::null()) {
            Ok(_) => 0, // Should not succeed
            Err(_) => 1, // Should fail
        }
    }
}

/// Test helper: Convert NSNull to Value and back
#[no_mangle]
pub extern "C" fn test_null_round_trip(ns_null: *const Object) -> *const Object {
    if ns_null.is_null() {
        return std::ptr::null();
    }
    
    unsafe {
        match copy_from_objc(ns_null) {
            Ok(value) => {
                match value_to_objc(&value) {
                    Ok(result) => *result,
                    Err(_) => std::ptr::null(),
                }
            },
            Err(_) => std::ptr::null(),
        }
    }
}
