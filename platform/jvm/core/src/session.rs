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
use bd_session::{Strategy, StrategyWithWorker, configuration};
use jni::JNIEnv;
use jni::signature::{Primitive, ReturnType};
use std::path::Path;
use std::sync::{Arc, OnceLock};

// Cached method IDs

static SESSION_CONFIGURATION_INACTIVITY_TIMEOUT_MILLISECONDS: OnceLock<CachedMethod> =
  OnceLock::new();
static SESSION_STRATEGY_INITIAL_SESSION_ID: OnceLock<CachedMethod> = OnceLock::new();
static SESSION_STRATEGY_SESSION_ID_CHANGED: OnceLock<CachedMethod> = OnceLock::new();

pub(crate) fn initialize(env: &mut JNIEnv<'_>) -> anyhow::Result<()> {
  let session_configuration = initialize_class(
    env,
    "io/bitdrift/capture/providers/session/SessionStrategyConfiguration",
    None,
  )?;
  initialize_method_handle(
    env,
    &session_configuration.class,
    "initialSessionId",
    "()Ljava/lang/String;",
    &SESSION_STRATEGY_INITIAL_SESSION_ID,
  )?;
  initialize_method_handle(
    env,
    &session_configuration.class,
    "inactivityTimeoutMilliseconds",
    "()J",
    &SESSION_CONFIGURATION_INACTIVITY_TIMEOUT_MILLISECONDS,
  )?;
  initialize_method_handle(
    env,
    &session_configuration.class,
    "sessionIdChanged",
    "(Ljava/lang/String;)V",
    &SESSION_STRATEGY_SESSION_ID_CHANGED,
  )?;
  Ok(())
}

define_object_wrapper!(SessionStrategyConfigurationHandle);

impl SessionStrategyConfigurationHandle {
  pub(crate) fn create(
    &self,
    callbacks: Arc<Self>,
    sdk_directory: &Path,
  ) -> anyhow::Result<StrategyWithWorker> {
    self.execute(|e, session_strategy_configuration| {
      let initial_session_id = SESSION_STRATEGY_INITIAL_SESSION_ID
        .get()
        .ok_or(InvariantError::Invariant)?
        .call_method(e, session_strategy_configuration, ReturnType::Object, &[])?
        .l()?;
      let initial_session_id = if initial_session_id.is_null() {
        None
      } else {
        let initial_session_id = initial_session_id.into();
        Some(unsafe { e.get_string_unchecked(&initial_session_id)? }.into())
      };
      let inactivity_timeout_milliseconds = SESSION_CONFIGURATION_INACTIVITY_TIMEOUT_MILLISECONDS
        .get()
        .ok_or(InvariantError::Invariant)?
        .call_method(
          e,
          session_strategy_configuration,
          ReturnType::Primitive(Primitive::Long),
          &[],
        )?
        .j()?;
      Ok(Strategy::configuration(
        sdk_directory,
        initial_session_id,
        (inactivity_timeout_milliseconds >= 0)
          .then(|| time::Duration::milliseconds(inactivity_timeout_milliseconds)),
        callbacks,
        Arc::new(bd_time::SystemTimeProvider {}),
      ))
    })
  }
}

impl configuration::Callbacks for SessionStrategyConfigurationHandle {
  fn session_id_changed(&self, session_id: &str) {
    with_handle_unexpected(
      || {
        self.execute(|e, session_configuration| {
          let session_id = e.new_string(session_id)?;

          SESSION_STRATEGY_SESSION_ID_CHANGED
            .get()
            .ok_or(InvariantError::Invariant)?
            .call_method(
              e,
              session_configuration,
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
