// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

import Foundation
import XCTest

// Testing conversion functions in conversion_tests.rs
@_silgen_name("test_convert_nsstring_to_rust_and_back")
func test_convert_nsstring_to_rust_and_back(_ nsString: UnsafeRawPointer?) -> UnsafeRawPointer?

@_silgen_name("test_convert_nsarray_to_rust_and_back")
func test_convert_nsarray_to_rust_and_back(_ nsArray: UnsafeRawPointer?) -> UnsafeRawPointer?

@_silgen_name("test_convert_nsdictionary_to_rust_and_back")
func test_convert_nsdictionary_to_rust_and_back(_ nsDict: UnsafeRawPointer?) -> UnsafeRawPointer?

@_silgen_name("test_convert_nsnumber_to_rust_and_back")
func test_convert_nsnumber_to_rust_and_back(_ nsNumber: UnsafeRawPointer?) -> UnsafeRawPointer?

@_silgen_name("test_create_complex_objc_structure")
func test_create_complex_objc_structure() -> UnsafeRawPointer?

@_silgen_name("test_null_pointer_handling")
func test_null_pointer_handling() -> Int32

@_silgen_name("test_convert_nsnull_to_rust_and_back")
func test_convert_nsnull_to_rust_and_back(_ nsNull: UnsafeRawPointer?) -> UnsafeRawPointer?

@_silgen_name("test_create_extremely_complex_nested_structure")
func test_create_extremely_complex_nested_structure() -> UnsafeRawPointer?

@_silgen_name("test_complex_nested_round_trip")
func test_complex_nested_round_trip() -> Int32

final class ConversionTests: XCTestCase {
    // String Conversion Tests

    func testStringRoundTrip() {
        let originalString = "Hello, World! 🌍"
        let nsString = originalString as NSString

        let ptr = Unmanaged.passUnretained(nsString).toOpaque()
        let resultPtr = test_convert_nsstring_to_rust_and_back(ptr)

        XCTAssertNotNil(resultPtr, "String round trip should not return null")

        if let resultPtr {
            let resultString = Unmanaged<NSString>.fromOpaque(resultPtr).takeUnretainedValue()
            XCTAssertEqual(resultString as String, originalString, "String should be preserved through round trip")
        }
    }

    func testEmptyStringRoundTrip() {
        let originalString = ""
        let nsString = originalString as NSString

        let ptr = Unmanaged.passUnretained(nsString).toOpaque()
        let resultPtr = test_convert_nsstring_to_rust_and_back(ptr)

        XCTAssertNotNil(resultPtr, "Empty string round trip should not return null")

        if let resultPtr {
            let resultString = Unmanaged<NSString>.fromOpaque(resultPtr).takeUnretainedValue()
            XCTAssertEqual(resultString as String, originalString, "Empty string should be preserved")
        }
    }

    // Number Conversion Tests

    func testSignedNumberRoundTrip() {
        let originalNumber = NSNumber(value: -42)

        let ptr = Unmanaged.passUnretained(originalNumber).toOpaque()
        let resultPtr = test_convert_nsnumber_to_rust_and_back(ptr)

        XCTAssertNotNil(resultPtr, "Signed number round trip should not return null")

        if let resultPtr {
            let resultNumber = Unmanaged<NSNumber>.fromOpaque(resultPtr).takeUnretainedValue()
            XCTAssertEqual(resultNumber.int64Value, originalNumber.int64Value, "Signed number should be preserved")
        }
    }

    func testUnsignedNumberRoundTrip() {
        let originalNumber = NSNumber(value: UInt64.max)

        let ptr = Unmanaged.passUnretained(originalNumber).toOpaque()
        let resultPtr = test_convert_nsnumber_to_rust_and_back(ptr)

        XCTAssertNotNil(resultPtr, "Unsigned number round trip should not return null")

        if let resultPtr {
            let resultNumber = Unmanaged<NSNumber>.fromOpaque(resultPtr).takeUnretainedValue()
            XCTAssertEqual(resultNumber.uint64Value, originalNumber.uint64Value, "Unsigned number should be preserved")
        }
    }

    func testFloatNumberRoundTrip() {
        let originalNumber = NSNumber(value: 3.14159)

        let ptr = Unmanaged.passUnretained(originalNumber).toOpaque()
        let resultPtr = test_convert_nsnumber_to_rust_and_back(ptr)

        XCTAssertNotNil(resultPtr, "Float number round trip should not return null")

        if let resultPtr {
            let resultNumber = Unmanaged<NSNumber>.fromOpaque(resultPtr).takeUnretainedValue()
            XCTAssertEqual(resultNumber.doubleValue, originalNumber.doubleValue, accuracy: 0.000001, "Float number should be preserved")
        }
    }

    func testBoolNumberRoundTrip() {
        let originalNumber = NSNumber(value: true)

        let ptr = Unmanaged.passUnretained(originalNumber).toOpaque()
        let resultPtr = test_convert_nsnumber_to_rust_and_back(ptr)

        XCTAssertNotNil(resultPtr, "Bool number round trip should not return null")

        if let resultPtr {
            let resultNumber = Unmanaged<NSNumber>.fromOpaque(resultPtr).takeUnretainedValue()
            XCTAssertEqual(resultNumber.boolValue, originalNumber.boolValue, "Bool number should be preserved")
        }
    }

    // Array Conversion Tests

    func testEmptyArrayRoundTrip() {
        let originalArray = NSArray()

        let ptr = Unmanaged.passUnretained(originalArray).toOpaque()
        let resultPtr = test_convert_nsarray_to_rust_and_back(ptr)

        XCTAssertNotNil(resultPtr, "Empty array round trip should not return null")

        if let resultPtr {
            let resultArray = Unmanaged<NSArray>.fromOpaque(resultPtr).takeUnretainedValue()
            XCTAssertEqual(resultArray.count, 0, "Empty array should remain empty")
        }
    }

    func testArrayWithElementsRoundTrip() {
        let originalArray = NSArray(objects: "test1", "test2", NSNumber(value: 42))

        let ptr = Unmanaged.passUnretained(originalArray).toOpaque()
        let resultPtr = test_convert_nsarray_to_rust_and_back(ptr)

        XCTAssertNotNil(resultPtr, "Array round trip should not return null")

        if let resultPtr {
            let resultArray = Unmanaged<NSArray>.fromOpaque(resultPtr).takeUnretainedValue()

            XCTAssertEqual(resultArray.count, 3, "Array should have same number of elements")
            XCTAssertEqual(resultArray[0] as? String, "test1", "First element should be preserved")
            XCTAssertEqual(resultArray[1] as? String, "test2", "Second element should be preserved")
            XCTAssertEqual((resultArray[2] as? NSNumber)?.int64Value, 42, "Third element should be preserved")
        }
    }

    // Dictionary Conversion Tests

    func testEmptyDictionaryRoundTrip() {
        let originalDict = NSDictionary()

        let ptr = Unmanaged.passUnretained(originalDict).toOpaque()
        let resultPtr = test_convert_nsdictionary_to_rust_and_back(ptr)

        XCTAssertNotNil(resultPtr, "Empty dictionary round trip should not return null")

        if let resultPtr {
            let resultDict = Unmanaged<NSDictionary>.fromOpaque(resultPtr).takeUnretainedValue()
            XCTAssertEqual(resultDict.count, 0, "Empty dictionary should remain empty")
        }
    }

    func testDictionaryWithElementsRoundTrip() {
        let originalDict = NSDictionary(dictionary: [
            "key1": "value1",
            "key2": NSNumber(value: 123),
        ])

        let ptr = Unmanaged.passUnretained(originalDict).toOpaque()
        let resultPtr = test_convert_nsdictionary_to_rust_and_back(ptr)

        XCTAssertNotNil(resultPtr, "Dictionary round trip should not return null")

        if let resultPtr {
            let resultDict = Unmanaged<NSDictionary>.fromOpaque(resultPtr).takeUnretainedValue()

            XCTAssertEqual(resultDict.count, 2, "Dictionary should have same number of elements")
            XCTAssertEqual(resultDict["key1"] as? String, "value1", "String value should be preserved")
            XCTAssertEqual((resultDict["key2"] as? NSNumber)?.int64Value, 123, "Number value should be preserved")
        }
    }

    // NSNull Conversion Tests

    func testNSNullRoundTrip() {
        let originalNull = NSNull()

        let ptr = Unmanaged.passUnretained(originalNull).toOpaque()
        let resultPtr = test_convert_nsnull_to_rust_and_back(ptr)

        XCTAssertNotNil(resultPtr, "NSNull round trip should not return null")

        if let resultPtr {
            let resultNull = Unmanaged<NSNull>.fromOpaque(resultPtr).takeUnretainedValue()
            XCTAssertTrue(resultNull === NSNull(), "NSNull should be preserved")
        }
    }

    // Complex Structure Tests

    func testComplexStructureCreation() {
        let resultPtr = test_create_complex_objc_structure()

        XCTAssertNotNil(resultPtr, "Complex structure creation should not return null")

        if let resultPtr {
            let resultDict = Unmanaged<NSDictionary>.fromOpaque(resultPtr).takeUnretainedValue()

            // Verify the structure: { "string": "test", "number": 42, "array": ["a", "b"], "bool": true, "null": null }
            XCTAssertEqual(resultDict.count, 5, "Complex structure should have 5 elements")

            XCTAssertEqual(resultDict["string"] as? String, "test", "String value should be correct")
            XCTAssertEqual((resultDict["number"] as? NSNumber)?.int64Value, 42, "Number value should be correct")
            XCTAssertEqual((resultDict["bool"] as? NSNumber)?.boolValue, true, "Bool value should be correct")
            XCTAssertTrue(resultDict["null"] is NSNull, "Null value should be NSNull")

            if let array = resultDict["array"] as? NSArray {
                XCTAssertEqual(array.count, 2, "Array should have 2 elements")
                XCTAssertEqual(array[0] as? String, "a", "First array element should be 'a'")
                XCTAssertEqual(array[1] as? String, "b", "Second array element should be 'b'")
            } else {
                XCTFail("Array value should be present")
            }
        }
    }

    func testNestedStructureRoundTrip() {
        // Create a nested structure: { "outer": { "inner": ["a", "b"] } }
        let innerArray = NSArray(objects: "a", "b")
        let innerDict = NSDictionary(dictionary: ["inner": innerArray])
        let outerDict = NSDictionary(dictionary: ["outer": innerDict])

        let ptr = Unmanaged.passUnretained(outerDict).toOpaque()
        let resultPtr = test_convert_nsdictionary_to_rust_and_back(ptr)

        XCTAssertNotNil(resultPtr, "Nested structure round trip should not return null")

        if let resultPtr {
            let resultDict = Unmanaged<NSDictionary>.fromOpaque(resultPtr).takeUnretainedValue()

            XCTAssertEqual(resultDict.count, 1, "Outer dictionary should have 1 element")

            if let outerValue = resultDict["outer"] as? NSDictionary {
                XCTAssertEqual(outerValue.count, 1, "Inner dictionary should have 1 element")

                if let innerValue = outerValue["inner"] as? NSArray {
                    XCTAssertEqual(innerValue.count, 2, "Inner array should have 2 elements")
                    XCTAssertEqual(innerValue[0] as? String, "a", "First inner element should be 'a'")
                    XCTAssertEqual(innerValue[1] as? String, "b", "Second inner element should be 'b'")
                } else {
                    XCTFail("Inner array should be present")
                }
            } else {
                XCTFail("Outer dictionary should be present")
            }
        }
    }

    // Error Handling Tests

    func testNullPointerHandling() {
        let result = test_null_pointer_handling()
        XCTAssertEqual(result, 1, "Null pointer should be properly rejected")
    }

    func testNullParameterHandling() {
        let resultPtr = test_convert_nsstring_to_rust_and_back(nil)
        XCTAssertNil(resultPtr, "Null parameter should return null")
    }

    // Very Complex Nested Structure Tests

    func testExtremelyComplexNestedStructureCreation() {
        let resultPtr = test_create_extremely_complex_nested_structure()
        XCTAssertNotNil(resultPtr, "Complex nested structure creation should succeed")

        if let resultPtr {
            let resultDict = Unmanaged<NSDictionary>.fromOpaque(resultPtr).takeUnretainedValue()

            // Verify the top-level structure exists
            XCTAssertTrue(resultDict.count >= 3, "Root dictionary should have at least 3 top-level keys")

            // Check metadata section
            if let metadata = resultDict["metadata"] as? NSDictionary {
                XCTAssertEqual(metadata["version"] as? String, "1.0.0", "Version should be correct")
                XCTAssertEqual(metadata["test_type"] as? String, "extreme_nesting", "Test type should be correct")
                XCTAssertEqual(metadata["nesting_levels"] as? Int64, 10, "Nesting levels should be 10")
                XCTAssertEqual(metadata["created_by"] as? String, "rust_ffi_test", "Created by should be correct")
            } else {
                XCTFail("Metadata section should exist")
            }

            // Check edge cases section
            if let edgeCases = resultDict["edge_cases"] as? NSDictionary {
                XCTAssertNotNil(edgeCases["max_signed"], "Max signed should exist")
                XCTAssertNotNil(edgeCases["min_signed"], "Min signed should exist")
                XCTAssertNotNil(edgeCases["max_unsigned"], "Max unsigned should exist")
                XCTAssertNotNil(edgeCases["positive_infinity"], "Positive infinity should exist")
                XCTAssertNotNil(edgeCases["negative_infinity"], "Negative infinity should exist")
                XCTAssertNotNil(edgeCases["nan"], "NaN should exist")

                // Verify some specific edge case values
                XCTAssertEqual(edgeCases["zero_unsigned"] as? UInt64, 0, "Zero unsigned should be 0")
                XCTAssertEqual(edgeCases["zero_float"] as? Double, 0.0, "Zero float should be 0.0")
            } else {
                XCTFail("Edge cases section should exist")
            }

            // Check stress arrays section
            if let stressArrays = resultDict["stress_arrays"] as? NSArray {
                XCTAssertEqual(stressArrays.count, 50, "Stress arrays should have 50 elements")

                // Check a few stress array elements
                if stressArrays.count > 0,
                   let firstStressArray = stressArrays[0] as? NSArray {
                    XCTAssertEqual(firstStressArray.count, 5, "Each stress array should have 5 elements")
                    XCTAssertEqual(firstStressArray[0] as? String, "stress_item_0", "First stress item should be correct")
                    XCTAssertEqual(firstStressArray[1] as? Int64, 0, "First stress number should be 0")
                    XCTAssertEqual(firstStressArray[2] as? Bool, true, "First stress bool should be true (0 % 3 == 0)")
                }
            } else {
                XCTFail("Stress arrays section should exist")
            }

            // Check complex structure section (this is the deeply nested part)
            if let complexStructure = resultDict["complex_structure"] as? NSArray {
                XCTAssertTrue(complexStructure.count >= 5, "Complex structure should have multiple elements")

                // The first element should be a deeply nested dictionary
                if let firstElement = complexStructure[0] as? NSDictionary {
                    XCTAssertTrue(firstElement.count >= 5, "First complex element should have multiple keys")

                    // Check that the deeply nested structure is accessible
                    if let massiveArray = firstElement["massive_array"] as? NSArray {
                        XCTAssertEqual(massiveArray.count, 20, "Massive array should have 20 elements")
                    }

                    if let emptyArray = firstElement["empty_array"] as? NSArray {
                        XCTAssertEqual(emptyArray.count, 0, "Empty array should be empty")
                    }

                    if let emptyDict = firstElement["empty_dict"] as? NSDictionary {
                        XCTAssertEqual(emptyDict.count, 0, "Empty dict should be empty")
                    }
                } else {
                    XCTFail("First complex structure element should be a dictionary")
                }
            } else {
                XCTFail("Complex structure section should exist")
            }
        }
    }

    func testExtremelyComplexNestedStructureRoundTrip() {
        let result = test_complex_nested_round_trip()
        XCTAssertEqual(result, 1, "Complex nested structure round trip should succeed")
    }

    func testExtremelyComplexNestedStructurePerformance() {
        // Test that the complex structure can be created and converted multiple times
        // without performance degradation or stack overflow
        measure {
            for _ in 0..<10 {
                let resultPtr = test_create_extremely_complex_nested_structure()
                XCTAssertNotNil(resultPtr, "Complex structure should be created successfully")

                if let resultPtr {
                    // Convert back to verify the round trip works
                    let resultDict = Unmanaged<NSDictionary>.fromOpaque(resultPtr).takeUnretainedValue()
                    XCTAssertTrue(resultDict.count >= 3, "Structure should maintain its complexity")
                }
            }
        }
    }

    func testExtremelyComplexNestedStructureMemoryStability() {
        // Create multiple complex structures to test memory management
        var structures: [NSDictionary] = []

        for _ in 0..<5 {
            let resultPtr = test_create_extremely_complex_nested_structure()
            XCTAssertNotNil(resultPtr, "Complex structure should be created successfully")

            if let resultPtr {
                let resultDict = Unmanaged<NSDictionary>.fromOpaque(resultPtr).takeUnretainedValue()
                structures.append(resultDict)
            }
        }

        // Verify all structures are still valid and contain expected data
        XCTAssertEqual(structures.count, 5, "All 5 structures should be created")

        for (index, structure) in structures.enumerated() {
            XCTAssertTrue(structure.count >= 3, "Structure \(index) should maintain its complexity")

            if let metadata = structure["metadata"] as? NSDictionary {
                XCTAssertEqual(metadata["version"] as? String, "1.0.0", "Version should be consistent across all structures")
            }
        }
    }
}
