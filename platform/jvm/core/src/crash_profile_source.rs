// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

//! The source codec is platform-neutral so host JVM tests exercise the production JNI encoder.

#[cfg(test)]
#[path = "./crash_profile_source_test.rs"]
mod tests;

use bd_artifact_upload::encode_source;
use bd_error_reporter::reporter::with_handle_unexpected_or;
use jni::JNIEnv;
use jni::objects::JClass;
use jni::sys::{jbyteArray, jint, jlong};

#[unsafe(no_mangle)]
pub extern "system" fn Java_io_bitdrift_capture_CaptureJniLibrary_encodeCrashProfileSource(
  env: JNIEnv<'_>,
  _class: JClass<'_>,
  pid: jint,
  boot_count: jint,
  process_start_elapsed_ns: jlong,
  wall_ns: jlong,
  elapsed_ns: jlong,
) -> jbyteArray {
  with_handle_unexpected_or(
    || -> anyhow::Result<jbyteArray> {
      let source = encode_source(
        u32::try_from(pid)?,
        u64::try_from(boot_count)?,
        u64::try_from(process_start_elapsed_ns)?,
        wall_ns,
        u64::try_from(elapsed_ns)?,
      );
      Ok(env.byte_array_from_slice(&source)?.into_raw())
    },
    std::ptr::null_mut(),
    "jni encode crash profile source",
  )
}
