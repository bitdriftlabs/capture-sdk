# Conversion Functions Testing

This document describes how to test the conversion functions `copy_from_objc` and `value_to_objc` in the `conversion.rs` module.

## Overview

The conversion functions require the Objective-C runtime to be available, so they cannot be tested as pure Rust unit tests. Instead, they need to be tested from Swift/Objective-C code that has access to the Foundation framework.

## Test Functions Available

The `conversion_tests.rs` module exports several C functions that can be called from Swift/Objective-C to test the conversion functionality:

### Round-trip Test Functions

These functions test bidirectional conversion (Objective-C → Rust → Objective-C):

- `test_string_round_trip(ns_string: *const Object) -> *const Object`
- `test_array_round_trip(ns_array: *const Object) -> *const Object`
- `test_dictionary_round_trip(ns_dict: *const Object) -> *const Object`
- `test_number_round_trip(ns_number: *const Object) -> *const Object`
- `test_null_round_trip(ns_null: *const Object) -> *const Object`

### Creation Test Functions

These functions test creating Objective-C objects from Rust:

- `test_create_complex_objc_structure() -> *const Object` - Creates a complex nested structure

### Error Handling Test Functions

- `test_null_pointer_handling() -> i32` - Tests null pointer rejection (returns 1 if properly handled)

## Example Swift Test Usage

```swift
import Foundation
import XCTest

class ConversionTests: XCTestCase {
    
    func testStringRoundTrip() {
        let originalString = "Hello, World! 🌍"
        let nsString = originalString as NSString
        
        let ptr = Unmanaged.passUnretained(nsString).toOpaque()
        let resultPtr = test_string_round_trip(ptr)
        
        XCTAssertNotNil(resultPtr, "String round trip should not return null")
        
        if let resultPtr = resultPtr {
            let resultString = Unmanaged<NSString>.fromOpaque(resultPtr).takeUnretainedValue()
            XCTAssertEqual(resultString as String, originalString)
        }
    }
    
    func testComplexStructure() {
        let resultPtr = test_create_complex_objc_structure()
        XCTAssertNotNil(resultPtr)
        
        if let resultPtr = resultPtr {
            let resultDict = Unmanaged<NSDictionary>.fromOpaque(resultPtr).takeUnretainedValue()
            
            // The structure should be: { "string": "test", "number": 42, "array": ["a", "b"], "bool": true, "null": null }
            XCTAssertEqual(resultDict["string"] as? String, "test")
            XCTAssertEqual((resultDict["number"] as? NSNumber)?.int64Value, 42)
            XCTAssertEqual((resultDict["bool"] as? NSNumber)?.boolValue, true)
            XCTAssertTrue(resultDict["null"] is NSNull)
            
            if let array = resultDict["array"] as? NSArray {
                XCTAssertEqual(array.count, 2)
                XCTAssertEqual(array[0] as? String, "a")
                XCTAssertEqual(array[1] as? String, "b")
            }
        }
    }
}
```

## Test Coverage

The conversion functions support the following types:

### Objective-C to Rust (copy_from_objc)
- `NSString` → `Value::String`
- `NSNumber` (signed) → `Value::Signed`
- `NSNumber` (unsigned) → `Value::Unsigned`
- `NSNumber` (float) → `Value::Float`
- `NSNumber` (bool) → `Value::Bool`
- `NSArray` → `Value::Array`
- `NSDictionary` → `Value::Object`
- `NSNull` → `Value::Null`

### Rust to Objective-C (value_to_objc)
- `Value::String` → `NSString`
- `Value::Signed` → `NSNumber`
- `Value::Unsigned` → `NSNumber`
- `Value::Float` → `NSNumber`
- `Value::Bool` → `NSNumber`
- `Value::Array` → `NSArray`
- `Value::Object` → `NSDictionary`
- `Value::Null`/`Value::None` → `NSNull`

## Key Features Tested

1. **Round-trip Conversion**: Data should be preserved when converting from Objective-C to Rust and back
2. **Empty Containers**: Empty arrays and dictionaries should be handled correctly
3. **Nested Structures**: Complex nested arrays and dictionaries should be preserved
4. **Type Preservation**: Numbers should maintain their type (signed/unsigned/float)
5. **Unicode Support**: Strings with Unicode characters should be preserved
6. **Null Handling**: NSNull and null pointers should be handled appropriately
7. **Memory Management**: No memory leaks should occur during conversions

## Error Conditions

The functions properly handle:
- Null pointer inputs (should return error)
- Unsupported Objective-C types (should return error)
- Memory allocation failures (should return error)

## Running Tests

To run these tests, they need to be integrated into the existing Swift test suite in the `test/platform/swift/unit_integration/core/` directory. The tests require:

1. The Objective-C runtime to be available
2. Foundation framework to be linked
3. The swift_bridge library to be compiled and linked
4. Proper autorelease pool management

## Non-Recursive Implementation

Both conversion functions use stack-based algorithms to avoid recursion and potential stack overflow with deeply nested structures. This makes them safe for converting large, complex data structures.
