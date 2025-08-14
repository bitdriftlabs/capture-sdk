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

### Complex Nested Structure Test Functions

For stress-testing the stack-based algorithms with extreme nesting:

- `test_create_extremely_complex_nested_structure() -> *const Object` - Creates an extremely complex nested structure with 10+ levels of nesting
- `test_complex_nested_round_trip() -> i32` - Tests round-trip conversion of the complex structure (returns 1 on success, 0 on failure)

## Complex Nested Structure Unit Test

The complex nested structure test is designed to stress-test the stack-based non-recursive algorithms with extreme nesting and all supported data types.

### Test Structure

The complex test creates a data structure with **10+ levels of nesting** containing:

#### Root Level (Level 1)
- `complex_structure`: Array containing Level 2 structures
- `metadata`: Dictionary with test information
- `edge_cases`: Dictionary with numerical edge cases
- `stress_arrays`: Array of 50 stress test arrays

#### Deep Nesting Details

**Level 10 (Deepest)**
```rust
let level10_array = Value::Array(vec![
    Value::String("deep_string_1".to_string()),
    Value::Signed(-9223372036854775808), // i64::MIN
    Value::Unsigned(18446744073709551615), // u64::MAX  
    Value::Float(std::f64::consts::PI),
    Value::Bool(true),
    Value::Null,
]);

let mut level10_dict = HashMap::new();
level10_dict.insert("deepest_key".to_string(), Value::String("deepest_value".to_string()));
level10_dict.insert("deepest_array".to_string(), level10_array);
level10_dict.insert("deepest_number".to_string(), Value::Float(2.718281828)); // e
level10_dict.insert("deepest_bool".to_string(), Value::Bool(false));
level10_dict.insert("deepest_null".to_string(), Value::Null);
```

**Edge Cases Section**
```rust
edge_cases.insert("max_signed".to_string(), Value::Signed(i64::MAX));
edge_cases.insert("min_signed".to_string(), Value::Signed(i64::MIN));
edge_cases.insert("max_unsigned".to_string(), Value::Unsigned(u64::MAX));
edge_cases.insert("positive_infinity".to_string(), Value::Float(std::f64::INFINITY));
edge_cases.insert("negative_infinity".to_string(), Value::Float(std::f64::NEG_INFINITY));
edge_cases.insert("nan".to_string(), Value::Float(std::f64::NAN));
```

**Stress Arrays (50 elements)**
Each stress array contains:
- Dynamic string with index
- Signed integer with index
- Boolean based on index
- Float based on index
- Conditional null values

### Complex Test Swift Functions

#### `testExtremelyComplexNestedStructureCreation`
- Creates the complex structure via Rust
- Validates the top-level structure exists
- Checks metadata section (version, test_type, nesting_levels, created_by)
- Verifies edge cases section (numerical limits, infinity, NaN)
- Validates stress arrays (50 elements with expected structure)
- Tests deeply nested access paths

#### `testExtremelyComplexNestedStructureRoundTrip`
- Tests full round-trip conversion
- Ensures no data loss during Objective-C ↔ Rust conversion

#### `testExtremelyComplexNestedStructurePerformance`
- Performance test creating complex structure 10 times
- Measures execution time
- Verifies consistency across multiple creations

#### `testExtremelyComplexNestedStructureMemoryStability`
- Creates 5 complex structures simultaneously
- Tests memory management under load
- Verifies all structures remain valid and consistent

### Stack-Based Algorithm Validation

This test specifically validates the non-recursive, stack-based algorithms by:

1. **Deep Nesting**: 10+ levels deep to test stack management
2. **Large Breadth**: 50+ elements in arrays to test iteration order
3. **Mixed Structures**: Alternating arrays and dictionaries
4. **Order Preservation**: Verifies correct processing order with reverse iteration
5. **Memory Management**: Tests with multiple simultaneous complex structures

### Complex Test Data Types

**All Supported Value Types**
- **Strings**: Empty, single char, unicode, emojis, special characters
- **Signed Integers**: 0, ±1, i64::MIN, i64::MAX
- **Unsigned Integers**: 0, 1, u64::MAX
- **Floats**: 0.0, ±1.0, π, e, ∞, -∞, NaN
- **Booleans**: true, false
- **Null**: Value::Null and Value::None
- **Arrays**: Empty, single element, nested arrays
- **Objects**: Empty, single key, deeply nested dictionaries

**Special Cases**
- Empty containers (arrays and dictionaries)
- Single-element containers
- Extremely large containers (50+ elements)
- Mixed nesting patterns
- Circular reference prevention (through value semantics)

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

### Algorithm Features Tested
- Stack-based processing (no recursion)
- Correct array element ordering
- Dictionary key-value preservation
- Memory management with StrongPtr
- Error handling for complex structures
- Deep nesting (10+ levels) with complex nested structure test
- Large breadth (50+ elements) stress testing
- Performance under extreme conditions

## Key Features Tested

1. **Round-trip Conversion**: Data should be preserved when converting from Objective-C to Rust and back
2. **Empty Containers**: Empty arrays and dictionaries should be handled correctly
3. **Nested Structures**: Complex nested arrays and dictionaries should be preserved
4. **Type Preservation**: Numbers should maintain their type (signed/unsigned/float)
5. **Unicode Support**: Strings with Unicode characters should be preserved
6. **Null Handling**: NSNull and null pointers should be handled appropriately
7. **Memory Management**: No memory leaks should occur during conversions
8. **Deep Nesting**: 10+ levels of nesting work correctly (complex nested structure test)
9. **Large Containers**: Handling of containers with 50+ elements
10. **Edge Cases**: Numerical limits and special float values (∞, -∞, NaN)
11. **Performance**: Complex structures convert within reasonable time
12. **Order Preservation**: Arrays maintain element order with stack-based processing

### Complex Nested Structure Test Validation

When the complex nested structure test runs successfully, it validates:

1. **Correctness**: All data types convert accurately through deep nesting
2. **Performance**: Complex structures (500+ elements) convert within reasonable time
3. **Memory Safety**: No memory leaks or corruption with multiple simultaneous structures
4. **Order Preservation**: Arrays maintain element order at all nesting levels
5. **Depth Handling**: 10+ levels of nesting work correctly without stack overflow
6. **Edge Cases**: Numerical limits and special values handled properly at all levels

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

### Implementation Details

The conversion functions use the existing C export bridge architecture:
- Rust functions exported with `#[no_mangle]` and `extern "C"`
- Swift functions declared with `@_silgen_name`
- Memory management via `Unmanaged<T>` in Swift
- Autorelease pool handling for Objective-C objects

The stack-based algorithms are specifically designed to handle:
- **Deep Nesting**: Tested up to 10+ levels deep
- **Large Breadth**: Tested with 50+ elements in arrays
- **Mixed Structures**: Alternating arrays and dictionaries
- **Order Preservation**: Correct processing order with reverse iteration + push
- **Memory Efficiency**: Multiple simultaneous complex structures

This comprehensive testing approach ensures the robustness of the non-recursive conversion algorithms under extreme conditions.
