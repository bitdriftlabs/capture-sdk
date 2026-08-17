// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

#![allow(clippy::expect_used, clippy::panic, clippy::unwrap_used)]

use super::read_jstring_list;
use crate::jni::initialize_class;
use jni::objects::{JList, JObject};
use jni::{InitArgsBuilder, JavaVM};
use std::sync::OnceLock;

fn java_vm() -> &'static JavaVM {
  static JVM: OnceLock<JavaVM> = OnceLock::new();

  JVM.get_or_init(|| {
    let arguments = InitArgsBuilder::new().build().expect("valid JVM arguments");
    JavaVM::new(arguments).expect("start JVM")
  })
}

#[test]
fn class_lookup_failure_clears_java_exception() {
  java_vm()
    .attach_current_thread(|env| -> anyhow::Result<()> {
      assert!(initialize_class(env, "io/bitdrift/capture/DoesNotExist", None).is_err());
      assert!(!env.exception_check());
      Ok(())
    })
    .expect("run JNI test");
}

#[test]
fn string_list_skips_null_elements() {
  java_vm()
    .attach_current_thread(|env| -> anyhow::Result<()> {
      let list = env.new_object(
        jni::jni_str!("java/util/ArrayList"),
        jni::jni_sig!("()V"),
        &[],
      )?;
      let list = env.cast_local::<JList<'_>>(list)?;
      list.add(env, &JObject::null())?;
      let abi = env.new_string("arm64-v8a")?;
      list.add(env, &abi)?;

      assert_eq!(read_jstring_list(env, &list)?, vec!["arm64-v8a"]);
      Ok(())
    })
    .expect("run JNI test");
}
