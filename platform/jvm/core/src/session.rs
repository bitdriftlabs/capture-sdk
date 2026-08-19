// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

use crate::define_object_wrapper;
use crate::jni::{CachedMethod, JValueWrapper, initialize_class, initialize_method_handle};
use bd_client_common::error::InvariantError;
use bd_error_reporter::reporter::with_handle_unexpected;
use bd_session::configuration;
use jni::JNIEnv;
use jni::signature::{Primitive, ReturnType};
use std::sync::OnceLock;

// Cached method IDs

static SESSION_CALLBACK_SESSION_ID_CHANGED: OnceLock<CachedMethod> = OnceLock::new();

pub(crate) fn initialize(env: &mut JNIEnv<'_>) -> anyhow::Result<()> {
  let session_callback = initialize_class(
    env,
    "io/bitdrift/capture/providers/session/SessionCallback",
    None,
  )?;
  initialize_method_handle(
    env,
    &session_callback.class,
    "sessionIdChanged",
    "(Ljava/lang/String;)V",
    &SESSION_CALLBACK_SESSION_ID_CHANGED,
  )?;
  Ok(())
}

define_object_wrapper!(SessionCallback);

impl configuration::Callbacks for SessionCallback {
  fn session_id_changed(&self, session_id: &str) {
    with_handle_unexpected(
      || {
        self.execute(|e, session_callback| {
          let session_id = e.new_string(session_id)?;

          SESSION_CALLBACK_SESSION_ID_CHANGED
            .get()
            .ok_or(InvariantError::Invariant)?
            .call_method(
              e,
              session_callback,
              ReturnType::Primitive(Primitive::Void),
              &[JValueWrapper::Object(session_id.into()).into()],
            )
            .map(|_| ())
        })
      },
      "jni: session_id_changed",
    );
  }
}
