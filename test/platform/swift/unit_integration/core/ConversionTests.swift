// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

import Foundation
import XCTest

// C function declarations for testing conversion functions
@_silgen_name("test_string_round_trip")
func test_string_round_trip(_ nsString: UnsafeRawPointer?) -> UnsafeRawPointer?

@_silgen_name("test_array_round_trip")
func test_array_round_trip(_ nsArray: UnsafeRawPointer?) -> UnsafeRawPointer?

@_silgen_name("test_dictionary_round_trip")
func test_dictionary_round_trip(_ nsDict: UnsafeRawPointer?) -> UnsafeRawPointer?

@_silgen_name("test_number_round_trip")
func test_number_round_trip(_ nsNumber: UnsafeRawPointer?) -> UnsafeRawPointer?

@_silgen_name("test_create_complex_objc_structure")
func test_create_complex_objc_structure() -> UnsafeRawPointer?

@_silgen_name("test_null_pointer_handling")
func test_null_pointer_handling() -> Int32

@_silgen_name("test_null_round_trip")
func test_null_round_trip(_ nsNull: UnsafeRawPointer?) -> UnsafeRawPointer?

final class ConversionTests: XCTestCase {
    
    // MARK: - String Conversion Tests
    
    func testStringRoundTrip() {
        let originalString = "Hello, World! 🌍"
        let nsString = originalString as NSString
        
        let ptr = Unmanaged.passUnretained(nsString).toOpaque()
        let resultPtr = test_string_round_trip(ptr)
        
        XCTAssertNotNil(resultPtr, "String round trip should not return null")
        
        if let resultPtr = resultPtr {
            let resultString = Unmanaged<NSString>.fromOpaque(resultPtr).takeUnretainedValue()
            XCTAssertEqual(resultString as String, originalString, "String should be preserved through round trip")
        }
    }
    
    func testEmptyStringRoundTrip() {
        let originalString = ""
        let nsString = originalString as NSString
        
        let ptr = Unmanaged.passUnretained(nsString).toOpaque()
        let resultPtr = test_string_round_trip(ptr)
        
        XCTAssertNotNil(resultPtr, "Empty string round trip should not return null")
        
        if let resultPtr = resultPtr {
            let resultString = Unmanaged<NSString>.fromOpaque(resultPtr).takeUnretainedValue()
            XCTAssertEqual(resultString as String, originalString, "Empty string should be preserved")
        }
    }
    
    // MARK: - Number Conversion Tests
    
    func testSignedNumberRoundTrip() {
        let originalNumber = NSNumber(value: -42)
        
        let ptr = Unmanaged.passUnretained(originalNumber).toOpaque()
        let resultPtr = test_number_round_trip(ptr)
        
        XCTAssertNotNil(resultPtr, "Signed number round trip should not return null")
        
        if let resultPtr = resultPtr {
            let resultNumber = Unmanaged<NSNumber>.fromOpaque(resultPtr).takeUnretainedValue()
            XCTAssertEqual(resultNumber.int64Value, originalNumber.int64Value, "Signed number should be preserved")
        }
    }
    
    func testUnsignedNumberRoundTrip() {
        let originalNumber = NSNumber(value: UInt64.max)
        
        let ptr = Unmanaged.passUnretained(originalNumber).toOpaque()
        let resultPtr = test_number_round_trip(ptr)
        
        XCTAssertNotNil(resultPtr, "Unsigned number round trip should not return null")
        
        if let resultPtr = resultPtr {
            let resultNumber = Unmanaged<NSNumber>.fromOpaque(resultPtr).takeUnretainedValue()
            XCTAssertEqual(resultNumber.uint64Value, originalNumber.uint64Value, "Unsigned number should be preserved")
        }
    }
    
    func testFloatNumberRoundTrip() {
        let originalNumber = NSNumber(value: 3.14159)
        
        let ptr = Unmanaged.passUnretained(originalNumber).toOpaque()
        let resultPtr = test_number_round_trip(ptr)
        
        XCTAssertNotNil(resultPtr, "Float number round trip should not return null")
        
        if let resultPtr = resultPtr {
            let resultNumber = Unmanaged<NSNumber>.fromOpaque(resultPtr).takeUnretainedValue()
            XCTAssertEqual(resultNumber.doubleValue, originalNumber.doubleValue, accuracy: 0.000001, "Float number should be preserved")
        }
    }
    
    func testBoolNumberRoundTrip() {
        let originalNumber = NSNumber(value: true)
        
        let ptr = Unmanaged.passUnretained(originalNumber).toOpaque()
        let resultPtr = test_number_round_trip(ptr)
        
        XCTAssertNotNil(resultPtr, "Bool number round trip should not return null")
        
        if let resultPtr = resultPtr {
            let resultNumber = Unmanaged<NSNumber>.fromOpaque(resultPtr).takeUnretainedValue()
            XCTAssertEqual(resultNumber.boolValue, originalNumber.boolValue, "Bool number should be preserved")
        }
    }
    
    // MARK: - Array Conversion Tests
    
    func testEmptyArrayRoundTrip() {
        let originalArray = NSArray()
        
        let ptr = Unmanaged.passUnretained(originalArray).toOpaque()
        let resultPtr = test_array_round_trip(ptr)
        
        XCTAssertNotNil(resultPtr, "Empty array round trip should not return null")
        
        if let resultPtr = resultPtr {
            let resultArray = Unmanaged<NSArray>.fromOpaque(resultPtr).takeUnretainedValue()
            XCTAssertEqual(resultArray.count, 0, "Empty array should remain empty")
        }
    }
    
    func testArrayWithElementsRoundTrip() {
        let originalArray = NSArray(objects: "test1", "test2", NSNumber(value: 42))
        
        let ptr = Unmanaged.passUnretained(originalArray).toOpaque()
        let resultPtr = test_array_round_trip(ptr)
        
        XCTAssertNotNil(resultPtr, "Array round trip should not return null")
        
        if let resultPtr = resultPtr {
            let resultArray = Unmanaged<NSArray>.fromOpaque(resultPtr).takeUnretainedValue()
            
            XCTAssertEqual(resultArray.count, 3, "Array should have same number of elements")
            XCTAssertEqual(resultArray[0] as? String, "test1", "First element should be preserved")
            XCTAssertEqual(resultArray[1] as? String, "test2", "Second element should be preserved")
            XCTAssertEqual((resultArray[2] as? NSNumber)?.int64Value, 42, "Third element should be preserved")
        }
    }
    
    // MARK: - Dictionary Conversion Tests
    
    func testEmptyDictionaryRoundTrip() {
        let originalDict = NSDictionary()
        
        let ptr = Unmanaged.passUnretained(originalDict).toOpaque()
        let resultPtr = test_dictionary_round_trip(ptr)
        
        XCTAssertNotNil(resultPtr, "Empty dictionary round trip should not return null")
        
        if let resultPtr = resultPtr {
            let resultDict = Unmanaged<NSDictionary>.fromOpaque(resultPtr).takeUnretainedValue()
            XCTAssertEqual(resultDict.count, 0, "Empty dictionary should remain empty")
        }
    }
    
    func testDictionaryWithElementsRoundTrip() {
        let originalDict = NSDictionary(dictionary: [
            "key1": "value1",
            "key2": NSNumber(value: 123)
        ])
        
        let ptr = Unmanaged.passUnretained(originalDict).toOpaque()
        let resultPtr = test_dictionary_round_trip(ptr)
        
        XCTAssertNotNil(resultPtr, "Dictionary round trip should not return null")
        
        if let resultPtr = resultPtr {
            let resultDict = Unmanaged<NSDictionary>.fromOpaque(resultPtr).takeUnretainedValue()
            
            XCTAssertEqual(resultDict.count, 2, "Dictionary should have same number of elements")
            XCTAssertEqual(resultDict["key1"] as? String, "value1", "String value should be preserved")
            XCTAssertEqual((resultDict["key2"] as? NSNumber)?.int64Value, 123, "Number value should be preserved")
        }
    }
    
    // MARK: - NSNull Conversion Tests
    
    func testNSNullRoundTrip() {
        let originalNull = NSNull()
        
        let ptr = Unmanaged.passUnretained(originalNull).toOpaque()
        let resultPtr = test_null_round_trip(ptr)
        
        XCTAssertNotNil(resultPtr, "NSNull round trip should not return null")
        
        if let resultPtr = resultPtr {
            let resultNull = Unmanaged<NSNull>.fromOpaque(resultPtr).takeUnretainedValue()
            XCTAssertTrue(resultNull === NSNull(), "NSNull should be preserved")
        }
    }
    
    // MARK: - Complex Structure Tests
    
    func testComplexStructureCreation() {
        let resultPtr = test_create_complex_objc_structure()
        
        XCTAssertNotNil(resultPtr, "Complex structure creation should not return null")
        
        if let resultPtr = resultPtr {
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
        let resultPtr = test_dictionary_round_trip(ptr)
        
        XCTAssertNotNil(resultPtr, "Nested structure round trip should not return null")
        
        if let resultPtr = resultPtr {
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
    
    // MARK: - Error Handling Tests
    
    func testNullPointerHandling() {
        let result = test_null_pointer_handling()
        XCTAssertEqual(result, 1, "Null pointer should be properly rejected")
    }
    
    func testNullParameterHandling() {
        let resultPtr = test_string_round_trip(nil)
        XCTAssertNil(resultPtr, "Null parameter should return null")
    }
}
