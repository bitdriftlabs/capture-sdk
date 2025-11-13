// AI workflow instrumentation library for Android
// This crate provides instrumentation for AI workflows in the Android SDK.

#![deny(
  clippy::expect_used,
  clippy::panic,
  clippy::todo,
  clippy::unimplemented,
  clippy::unreachable,
  clippy::unwrap_used
)]

use jni::objects::JByteArray;
use jni::sys::{jint, JNI_VERSION_1_6};
use jni::JNIEnv;
use std::os::raw::c_void;

#[no_mangle]
pub extern "C" fn get_string_length(s: *const u8, len: usize) -> usize {
    if s.is_null() {
        return 0;
    }
    unsafe {
        let slice = std::slice::from_raw_parts(s, len);
        slice.len()
    }
}

/// JNI OnLoad function - called when the library is loaded
#[no_mangle]
pub extern "system" fn JNI_OnLoad(_vm: jni::JavaVM, _reserved: *mut c_void) -> jint {
    JNI_VERSION_1_6
}

/// JNI function to get string length from a byte array
/// 
/// This function is called from Kotlin/Java code via:
/// `AiWorkflowInstrumentationJni.getStringLength(byteArray)`
#[no_mangle]
pub extern "system" fn Java_io_bitdrift_capture_aiworkflow_AiWorkflowInstrumentationJni_getStringLength(
    mut env: JNIEnv<'_>,
    _class: jni::objects::JClass<'_>,
    byte_array: JByteArray<'_>,
) -> jint {
    let result = (|| -> jni::errors::Result<jint> {
        let bytes = env.convert_byte_array(&byte_array)?;
        let length = get_string_length(bytes.as_ptr(), bytes.len());
        Ok(length as jint)
    })();

    match result {
        Ok(length) => length,
        Err(e) => {
            if let Err(err) = env.throw_new(
                "java/lang/RuntimeException",
                format!("Failed to get string length: {}", e),
            ) {
                // If we can't throw an exception, log it and return 0
                eprintln!("Failed to throw exception: {}", err);
            }
            0
        }
    }
}

