// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

// This file provides C export functions to test the conversion functions from Swift/Objective-C
// The actual conversion functions require the Objective-C runtime to be available, so they
// cannot be tested as pure Rust unit tests.

use crate::conversion::{objc_value_to_rust, rust_value_to_objc};
use bd_bonjson::decoder::Value;
use objc::runtime::Object;
use std::collections::HashMap;

/// Test helper: Convert an NSString to a Rust Value and back to NSString
/// Returns the converted NSString, or null if conversion failed
#[no_mangle]
pub extern "C" fn test_string_round_trip(ns_string: *const Object) -> *const Object {
  if ns_string.is_null() {
    return std::ptr::null();
  }

  unsafe {
    match objc_value_to_rust(ns_string) {
      Ok(value) => match rust_value_to_objc(&value) {
        Ok(result) => *result,
        Err(_) => std::ptr::null(),
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
    match objc_value_to_rust(ns_array) {
      Ok(value) => match rust_value_to_objc(&value) {
        Ok(result) => *result,
        Err(_) => std::ptr::null(),
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
    match objc_value_to_rust(ns_dict) {
      Ok(value) => match rust_value_to_objc(&value) {
        Ok(result) => *result,
        Err(_) => std::ptr::null(),
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
    match objc_value_to_rust(ns_number) {
      Ok(value) => match rust_value_to_objc(&value) {
        Ok(result) => *result,
        Err(_) => std::ptr::null(),
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
    map.insert(
      "array".to_string(),
      Value::Array(vec![
        Value::String("a".to_string()),
        Value::String("b".to_string()),
      ]),
    );
    map.insert("bool".to_string(), Value::Bool(true));
    map.insert("null".to_string(), Value::Null);

    let value = Value::Object(map);

    match rust_value_to_objc(&value) {
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
    match objc_value_to_rust(std::ptr::null()) {
      Ok(_) => 0,  // Should not succeed
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
    match objc_value_to_rust(ns_null) {
      Ok(value) => match rust_value_to_objc(&value) {
        Ok(result) => *result,
        Err(_) => std::ptr::null(),
      },
      Err(_) => std::ptr::null(),
    }
  }
}

/// Test helper: Create an extremely complex and deeply nested data structure
/// This creates a structure with 10+ levels of nesting containing all supported data types
/// to stress-test the stack-based algorithms
#[no_mangle]
pub extern "C" fn test_create_extremely_complex_nested_structure() -> *const Object {
  unsafe {
    let level10_array = Value::Array(vec![
      Value::String("deep_string_1".to_string()),
      Value::Signed(-9223372036854775808),   // i64::MIN
      Value::Unsigned(18446744073709551615), // u64::MAX
      Value::Float(std::f64::consts::PI),
      Value::Bool(true),
      Value::Null,
    ]);

    let mut level10_dict = HashMap::new();
    level10_dict.insert(
      "deepest_key".to_string(),
      Value::String("deepest_value".to_string()),
    );
    level10_dict.insert("deepest_array".to_string(), level10_array);
    level10_dict.insert("deepest_number".to_string(), Value::Float(2.718281828)); // e
    level10_dict.insert("deepest_bool".to_string(), Value::Bool(false));
    level10_dict.insert("deepest_null".to_string(), Value::Null);

    let mut level9_dict = HashMap::new();
    level9_dict.insert("level10_nest".to_string(), Value::Object(level10_dict));
    level9_dict.insert(
      "level9_data".to_string(),
      Value::Array(vec![
        Value::String("level9_item1".to_string()),
        Value::String("level9_item2".to_string()),
        Value::Signed(999999999),
        Value::Array(vec![Value::Bool(true), Value::Bool(false), Value::Null]),
      ]),
    );

    let level8_array = Value::Array(vec![
      Value::Object(level9_dict.clone()),
      Value::Object(level9_dict.clone()),
      Value::String("level8_separator".to_string()),
      Value::Array(vec![
        Value::Signed(1),
        Value::Signed(2),
        Value::Signed(3),
        Value::Signed(4),
        Value::Signed(5),
        Value::String("mid_array".to_string()),
        Value::Float(1.414213562), // sqrt(2)
      ]),
    ]);

    let mut level7_dict = HashMap::new();
    level7_dict.insert("nested_array".to_string(), level8_array);
    level7_dict.insert(
      "numbers_showcase".to_string(),
      Value::Array(vec![
        Value::Signed(0),
        Value::Signed(-1),
        Value::Signed(1),
        Value::Signed(i64::MAX),
        Value::Signed(i64::MIN),
        Value::Unsigned(0),
        Value::Unsigned(1),
        Value::Unsigned(u64::MAX),
        Value::Float(0.0),
        Value::Float(-0.0),
        Value::Float(1.0),
        Value::Float(-1.0),
        Value::Float(std::f64::INFINITY),
        Value::Float(std::f64::NEG_INFINITY),
        Value::Float(std::f64::NAN),
      ]),
    );
    level7_dict.insert(
      "strings_showcase".to_string(),
      Value::Array(vec![
        Value::String("".to_string()),  // empty string
        Value::String("a".to_string()), // single char
        Value::String("hello world".to_string()),
        Value::String("🌟🚀💫🎉".to_string()), // emojis
        Value::String("äöüß".to_string()),     // unicode
        Value::String("日本語".to_string()),   // japanese
        Value::String("עברית".to_string()),    // hebrew
        Value::String("العربية".to_string()),  // arabic
        Value::String("\"quotes\" and 'apostrophes'".to_string()),
        Value::String("line1\nline2\tindented".to_string()), // special chars
      ]),
    );

    let level6_array = Value::Array(vec![
      Value::Object(level7_dict),
      Value::String("level6_divider".to_string()),
      Value::Array(vec![
        Value::Array(vec![
          Value::Array(vec![
            Value::String("triple_nested_string".to_string()),
            Value::Null,
          ]),
          Value::Bool(true),
        ]),
        Value::Signed(42),
      ]),
      Value::Null,
    ]);

    let mut level5_dict = HashMap::new();
    level5_dict.insert("0_key".to_string(), level6_array);
    level5_dict.insert(
      "1_key".to_string(),
      Value::String("level5_value1".to_string()),
    );
    level5_dict.insert(
      "2_key".to_string(),
      Value::Array(vec![
        Value::Bool(false),
        Value::Bool(true),
        Value::Bool(false),
      ]),
    );
    level5_dict.insert("3_key".to_string(), Value::Null);
    level5_dict.insert("4_key".to_string(), Value::Signed(-987654321));
    level5_dict.insert("5_key".to_string(), Value::Float(3.14159265359));

    let mut level4_items = Vec::new();
    for i in 0 .. 20 {
      if i % 4 == 0 {
        level4_items.push(Value::Object(level5_dict.clone()));
      } else if i % 4 == 1 {
        level4_items.push(Value::Array(vec![
          Value::String(format!("item_{}", i)),
          Value::Signed(i as i64),
          Value::Bool(i % 2 == 0),
        ]));
      } else if i % 4 == 2 {
        level4_items.push(Value::String(format!("string_item_{}", i)));
      } else {
        level4_items.push(Value::Null);
      }
    }
    let level4_array = Value::Array(level4_items);

    let mut level3_dict = HashMap::new();
    level3_dict.insert("massive_array".to_string(), level4_array);
    level3_dict.insert("empty_array".to_string(), Value::Array(vec![]));
    level3_dict.insert("empty_dict".to_string(), Value::Object(HashMap::new()));
    level3_dict.insert(
      "single_item_array".to_string(),
      Value::Array(vec![Value::String("lonely_item".to_string())]),
    );
    level3_dict.insert("single_item_dict".to_string(), {
      let mut single_dict = HashMap::new();
      single_dict.insert(
        "only_key".to_string(),
        Value::String("only_value".to_string()),
      );
      Value::Object(single_dict)
    });
    level3_dict.insert(
      "boolean_tests".to_string(),
      Value::Array(vec![
        Value::Bool(true),
        Value::Bool(false),
        Value::Bool(true),
        Value::Bool(false),
      ]),
    );
    level3_dict.insert(
      "null_tests".to_string(),
      Value::Array(vec![
        Value::Null,
        Value::String("not_null".to_string()),
        Value::Null,
      ]),
    );

    let level2_array = Value::Array(vec![
      Value::Object(level3_dict.clone()),
      Value::String("level2_separator_1".to_string()),
      Value::Object(level3_dict.clone()),
      Value::String("level2_separator_2".to_string()),
      Value::Array(vec![
        Value::String("nested_in_level2".to_string()),
        Value::Object(level3_dict),
      ]),
      Value::Null,
    ]);

    let mut root_dict = HashMap::new();
    root_dict.insert("complex_structure".to_string(), level2_array);
    root_dict.insert(
      "metadata".to_string(),
      Value::Object({
        let mut meta = HashMap::new();
        meta.insert("version".to_string(), Value::String("1.0.0".to_string()));
        meta.insert(
          "test_type".to_string(),
          Value::String("extreme_nesting".to_string()),
        );
        meta.insert("nesting_levels".to_string(), Value::Signed(10));
        meta.insert("total_elements".to_string(), Value::Signed(500)); // approximate
        meta.insert(
          "created_by".to_string(),
          Value::String("rust_ffi_test".to_string()),
        );
        meta
      }),
    );
    root_dict.insert(
      "edge_cases".to_string(),
      Value::Object({
        let mut edge_cases = HashMap::new();
        edge_cases.insert("max_signed".to_string(), Value::Signed(i64::MAX));
        edge_cases.insert("min_signed".to_string(), Value::Signed(i64::MIN));
        edge_cases.insert("max_unsigned".to_string(), Value::Unsigned(u64::MAX));
        edge_cases.insert("zero_unsigned".to_string(), Value::Unsigned(0));
        edge_cases.insert(
          "positive_infinity".to_string(),
          Value::Float(std::f64::INFINITY),
        );
        edge_cases.insert(
          "negative_infinity".to_string(),
          Value::Float(std::f64::NEG_INFINITY),
        );
        edge_cases.insert("nan".to_string(), Value::Float(std::f64::NAN));
        edge_cases.insert("zero_float".to_string(), Value::Float(0.0));
        edge_cases.insert("negative_zero".to_string(), Value::Float(-0.0));
        edge_cases
      }),
    );
    root_dict.insert(
      "stress_arrays".to_string(),
      Value::Array({
        let mut stress_arrays = Vec::new();
        for i in 0 .. 50 {
          stress_arrays.push(Value::Array(vec![
            Value::String(format!("stress_item_{}", i)),
            Value::Signed(i as i64),
            Value::Bool(i % 3 == 0),
            Value::Float(i as f64 * 0.1),
            if i % 5 == 0 {
              Value::Null
            } else {
              Value::String(format!("non_null_{}", i))
            },
          ]));
        }
        stress_arrays
      }),
    );

    let root_value = Value::Object(root_dict);

    match rust_value_to_objc(&root_value) {
      Ok(result) => *result,
      Err(_) => std::ptr::null(),
    }
  }
}

/// Test helper: Round-trip test for the extremely complex nested structure
/// This creates the complex structure, converts it to Objective-C, then back to Rust
/// Returns 1 if the round-trip succeeds, 0 if it fails
#[no_mangle]
pub extern "C" fn test_complex_nested_round_trip() -> i32 {
  unsafe {
    let objc_structure = test_create_extremely_complex_nested_structure();
    if objc_structure.is_null() {
      return 0;
    }

    match objc_value_to_rust(objc_structure) {
      Ok(_value) => {
        // If we can successfully convert back to Rust Value, the round-trip worked
        // We don't need to do a deep comparison here, just verify it doesn't crash
        1
      },
      Err(_) => 0,
    }
  }
}
