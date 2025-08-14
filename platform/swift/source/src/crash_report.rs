// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

// use bd_bonjson::decoder::Value;
use objc::runtime::Object;
use crate::conversion::{objc_value_to_rust, rust_value_to_objc};

#[no_mangle]
extern "C" fn enhance_metrickit_diagnostic_report(ptr: *const Object) -> *const Object {
    unsafe {
        // Convert Objective-C object to Rust Value
        let value = match objc_value_to_rust(ptr) {
            Ok(v) => v,
            Err(_) => return std::ptr::null(),
        };

        // TODO
        let enhanced_value = value;

        // Convert back to Objective-C object
        match rust_value_to_objc(&enhanced_value) {
            Ok(strong_ptr) => *strong_ptr,
            Err(_) => std::ptr::null(),
        }
    }
}
