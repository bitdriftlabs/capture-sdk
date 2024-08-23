// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

use crate::define_object_wrapper;
use crate::jni::{initialize_class, initialize_method_handle, CachedMethod};
use jni::signature::{Primitive, ReturnType};
use jni::JNIEnv;
use std::sync::OnceLock;

// Cached method IDs

static TARGET_TICK: OnceLock<CachedMethod> = OnceLock::new();

pub(crate) fn initialize(env: &mut JNIEnv<'_>) {
  let resource_utilization_target =
    initialize_class(env, "io/bitdrift/capture/IResourceUtilizationTarget", None);
  initialize_method_handle(
    env,
    &resource_utilization_target.class,
    "tick",
    "()V",
    &TARGET_TICK,
  );
}

//
// TargetHandler
//

define_object_wrapper!(TargetHandler);

unsafe impl Send for TargetHandler {}
unsafe impl Sync for TargetHandler {}

impl bd_logger::ResourceUtilizationTarget for TargetHandler {
  fn tick(&self) {
    bd_client_common::error::with_handle_unexpected(
      || {
        self.execute(|e, target| {
          TARGET_TICK
            .get()
            .unwrap()
            .call_method(e, target, ReturnType::Primitive(Primitive::Void), &[])
            .map(|_| ())
        })
      },
      "resource utilization target_handler: fire",
    );
  }
}
