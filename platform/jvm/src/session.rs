// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

use crate::define_object_wrapper;
use crate::jni::{
  initialize_class,
  initialize_method_handle,
  CachedClass,
  CachedMethod,
  JValueWrapper,
};
use anyhow::anyhow;
use bd_client_common::error::with_handle_unexpected;
use bd_session::{activity_based, fixed, Strategy};
use jni::signature::{Primitive, ReturnType};
use jni::JNIEnv;
use std::sync::{Arc, OnceLock};
use time::Duration;

// Cached method IDs

static SESSION_STRATEGY_FIXED: OnceLock<CachedClass> = OnceLock::new();

static SESSION_STRATEGY_INACTIVITY_THRESHOLD_MINS: OnceLock<CachedMethod> = OnceLock::new();
static SESSION_STRATEGY_GENERATE_SESSION_ID: OnceLock<CachedMethod> = OnceLock::new();
static SESSION_STRATEGY_SESSION_ID_CHANGED: OnceLock<CachedMethod> = OnceLock::new();

pub(crate) fn initialize(env: &mut JNIEnv<'_>) {
  let session_strategy_fixed = initialize_class(
    env,
    "io/bitdrift/capture/providers/session/SessionStrategyConfiguration$Fixed",
    Some(&SESSION_STRATEGY_FIXED),
  );
  initialize_method_handle(
    env,
    &session_strategy_fixed.class,
    "generateSessionId",
    "()Ljava/lang/String;",
    &SESSION_STRATEGY_GENERATE_SESSION_ID,
  );

  let session_strategy_activity_based = initialize_class(
    env,
    "io/bitdrift/capture/providers/session/SessionStrategyConfiguration$ActivityBased",
    None,
  );
  initialize_method_handle(
    env,
    &session_strategy_activity_based.class,
    "inactivityThresholdMins",
    "()J",
    &SESSION_STRATEGY_INACTIVITY_THRESHOLD_MINS,
  );
  initialize_method_handle(
    env,
    &session_strategy_activity_based.class,
    "sessionIdChanged",
    "(Ljava/lang/String;)V",
    &SESSION_STRATEGY_SESSION_ID_CHANGED,
  );
}

define_object_wrapper!(SessionStrategyConfigurationHandle);

impl SessionStrategyConfigurationHandle {
  pub(crate) fn create(
    &self,
    callbacks: Arc<Self>,
    store: Arc<bd_key_value::Store>,
  ) -> anyhow::Result<Arc<Strategy>> {
    self.execute(|e, session_strategy_configuration| {
      Ok(Arc::new(
        if e.is_instance_of(
          session_strategy_configuration,
          &SESSION_STRATEGY_FIXED.get().unwrap().class,
        )? {
          Strategy::Fixed(fixed::Strategy::new(store, callbacks))
        } else {
          let inactivity_threshold_mins = SESSION_STRATEGY_INACTIVITY_THRESHOLD_MINS
            .get()
            .unwrap()
            .call_method(
              e,
              session_strategy_configuration,
              ReturnType::Primitive(Primitive::Long),
              &[],
            )
            .map_err(|e| anyhow!(e))?
            .j()?;

          Strategy::ActivityBased(activity_based::Strategy::new(
            Duration::minutes(inactivity_threshold_mins),
            store,
            callbacks,
            Arc::new(bd_time::SystemTimeProvider {}),
          ))
        },
      ))
    })
  }
}

impl fixed::Callbacks for SessionStrategyConfigurationHandle {
  fn generate_session_id(&self) -> anyhow::Result<String> {
    self.execute(|e, session_strategy_configuration| {
      let session_id = SESSION_STRATEGY_GENERATE_SESSION_ID
        .get()
        .unwrap()
        .call_method(e, session_strategy_configuration, ReturnType::Object, &[])?
        .l()?;

      unsafe { e.get_string_unchecked(&session_id.into())? }
        .to_str()
        .ok()
        .map(ToString::to_string)
        .ok_or_else(|| anyhow!("jni: generate_session_id failed to convert session_id to string"))
    })
  }
}

impl activity_based::Callbacks for SessionStrategyConfigurationHandle {
  fn session_id_changed(&self, session_id: &str) {
    with_handle_unexpected(
      || {
        self.execute(|e, session_configuration| {
          let session_id = e.new_string(session_id)?;

          SESSION_STRATEGY_SESSION_ID_CHANGED
            .get()
            .unwrap()
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
