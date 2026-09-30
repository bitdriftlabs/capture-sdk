// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

use crate::jni::{
  Java_io_bitdrift_capture_CaptureJniLibrary_addLogField,
  Java_io_bitdrift_capture_CaptureJniLibrary_removeLogField,
  Java_io_bitdrift_capture_Jni_isRuntimeEnabled,
  Java_io_bitdrift_capture_Jni_runtimeStringValue,
  Java_io_bitdrift_capture_Jni_runtimeValue,
};
use jni::objects::JString;
use jni::sys::{jboolean, jint};
use jni::{JNIEnv, NativeMethod};
use platform_shared::LoggerId;
use std::ffi::c_void;

const CAPTURE_JNI_LIBRARY_CLASS: &str = "io/bitdrift/capture/CaptureJniLibrary";
const JNI_RUNTIME_CLASS: &str = "io/bitdrift/capture/Jni";
const MIN_OPTIMIZED_NATIVE_SDK_INT: jint = 26;

extern "system" fn is_tracing_active(logger_id: LoggerId<'_>) -> jboolean {
  logger_id.is_tracing_active().into()
}

extern "system" fn previous_memory_pressure_level(logger_id: LoggerId<'_>) -> jint {
  logger_id.previous_memory_pressure_level().0.into()
}

fn native_method(name: &str, sig: &str, fn_ptr: *mut c_void) -> NativeMethod {
  NativeMethod {
    name: name.into(),
    sig: sig.into(),
    fn_ptr,
  }
}

pub(crate) fn initialize(env: &mut JNIEnv<'_>) -> anyhow::Result<()> {
  if !is_art_with_optimized_natives(env)? {
    return Ok(());
  }

  env.register_native_methods(
    CAPTURE_JNI_LIBRARY_CLASS,
    &[
      native_method("isTracingActive", "(J)Z", is_tracing_active as *mut c_void),
      native_method(
        "previousMemoryPressureLevel",
        "(J)I",
        previous_memory_pressure_level as *mut c_void,
      ),
      native_method(
        "addLogField",
        "(JLjava/lang/String;Ljava/lang/String;)V",
        Java_io_bitdrift_capture_CaptureJniLibrary_addLogField as *mut c_void,
      ),
      native_method(
        "removeLogField",
        "(JLjava/lang/String;)V",
        Java_io_bitdrift_capture_CaptureJniLibrary_removeLogField as *mut c_void,
      ),
    ],
  )?;

  env.register_native_methods(
    JNI_RUNTIME_CLASS,
    &[
      native_method(
        "isRuntimeEnabled",
        "(JLjava/lang/String;Z)Z",
        Java_io_bitdrift_capture_Jni_isRuntimeEnabled as *mut c_void,
      ),
      native_method(
        "runtimeValue",
        "(JLjava/lang/String;I)I",
        Java_io_bitdrift_capture_Jni_runtimeValue as *mut c_void,
      ),
      native_method(
        "runtimeStringValue",
        "(JLjava/lang/String;Ljava/lang/String;)Ljava/lang/String;",
        Java_io_bitdrift_capture_Jni_runtimeStringValue as *mut c_void,
      ),
    ],
  )?;

  Ok(())
}

fn is_art_with_optimized_natives(env: &mut JNIEnv<'_>) -> anyhow::Result<bool> {
  let key = env.new_string("java.vm.name")?;
  let vm_name = env
    .call_static_method(
      "java/lang/System",
      "getProperty",
      "(Ljava/lang/String;)Ljava/lang/String;",
      &[(&key).into()],
    )?
    .l()?;

  if vm_name.is_null() {
    return Ok(false);
  }

  let vm_name: String = env.get_string(&JString::from(vm_name))?.into();
  if vm_name != "Dalvik" {
    return Ok(false);
  }

  let sdk_int = env
    .get_static_field("android/os/Build$VERSION", "SDK_INT", "I")?
    .i()?;

  Ok(sdk_int >= MIN_OPTIMIZED_NATIVE_SDK_INT)
}
