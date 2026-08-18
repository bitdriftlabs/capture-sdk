// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

#![allow(clippy::expect_used, clippy::panic, clippy::unwrap_used)]

use jni::{InitArgsBuilder, JNIEnv, JavaVM};
use std::sync::OnceLock;

pub fn java_vm() -> &'static JavaVM {
  static JVM: OnceLock<JavaVM> = OnceLock::new();

  JVM.get_or_init(|| {
    let arguments = InitArgsBuilder::new()
      .option("-Xcheck:jni")
      .build()
      .expect("valid JVM arguments");
    // `jni` 0.21 finds the VM through JAVA_HOME. The Bazel wrapper sets it to its selected JDK.
    JavaVM::new(arguments).expect("start Bazel JVM")
  })
}

pub fn with_env<T>(
  callback: impl FnOnce(&mut JNIEnv<'_>) -> anyhow::Result<T>,
) -> anyhow::Result<T> {
  let mut env = java_vm().attach_current_thread()?;
  callback(&mut env)
}
