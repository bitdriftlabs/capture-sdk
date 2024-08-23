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

static TARGET_START: OnceLock<CachedMethod> = OnceLock::new();
static TARGET_STOP: OnceLock<CachedMethod> = OnceLock::new();

pub(crate) fn initialize(env: &mut JNIEnv<'_>) {
  let events_listener_target =
    initialize_class(env, "io/bitdrift/capture/IEventsListenerTarget", None);
  initialize_method_handle(
    env,
    &events_listener_target.class,
    "start",
    "()V",
    &TARGET_START,
  );
  initialize_method_handle(
    env,
    &events_listener_target.class,
    "stop",
    "()V",
    &TARGET_STOP,
  );
}

//
// ListenerTargetHandler
//

define_object_wrapper!(ListenerTargetHandler);

unsafe impl Send for ListenerTargetHandler {}
unsafe impl Sync for ListenerTargetHandler {}

impl bd_logger::EventsListenerTarget for ListenerTargetHandler {
  fn start(&self) {
    bd_client_common::error::with_handle_unexpected(
      || {
        self.execute(|e, target| {
          TARGET_START
            .get()
            .unwrap()
            .call_method(e, target, ReturnType::Primitive(Primitive::Void), &[])
            .map(|_| ())
        })
      },
      "events listener target_handler: start",
    );
  }

  fn stop(&self) {
    bd_client_common::error::with_handle_unexpected(
      || {
        self.execute(|e, target| {
          TARGET_STOP
            .get()
            .unwrap()
            .call_method(e, target, ReturnType::Primitive(Primitive::Void), &[])
            .map(|_| ())
        })
      },
      "events listener target_handler: stop",
    );
  }
}
