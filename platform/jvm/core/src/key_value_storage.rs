// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

use crate::define_object_wrapper;
use crate::jni::{initialize_class, initialize_method_handle, CachedMethod, JValueWrapper};
use bd_client_common::error::InvariantError;
use jni::signature::{Primitive, ReturnType};
use jni::sys::JNI_TRUE;
use jni::JNIEnv;
use std::sync::OnceLock;

// Cached method IDs

static PREFERENCES_GET_STRING: OnceLock<CachedMethod> = OnceLock::new();
static PREFERENCES_SET_STRING: OnceLock<CachedMethod> = OnceLock::new();

pub(crate) fn initialize(env: &mut JNIEnv<'_>) -> anyhow::Result<()> {
  let preferences = initialize_class(env, "io/bitdrift/capture/IPreferences", None)?;
  initialize_method_handle(
    env,
    &preferences.class,
    "getString",
    "(Ljava/lang/String;)Ljava/lang/String;",
    &PREFERENCES_GET_STRING,
  )?;
  initialize_method_handle(
    env,
    &preferences.class,
    "setString",
    "(Ljava/lang/String;Ljava/lang/String;Z)V",
    &PREFERENCES_SET_STRING,
  )?;
  Ok(())
}

//
// PreferencesHandle
//

define_object_wrapper!(PreferencesHandle);

unsafe impl Sync for PreferencesHandle {}
unsafe impl Send for PreferencesHandle {}

impl bd_key_value::Storage for PreferencesHandle {
  fn get_string(&self, key: &str) -> anyhow::Result<Option<String>> {
    self.execute(|e, preferences| {
      let key = e.new_string(key)?;

      let value = PREFERENCES_GET_STRING
        .get()
        .ok_or(InvariantError::Invariant)?
        .call_method(
          e,
          preferences,
          ReturnType::Object,
          &[JValueWrapper::JObject(key.as_raw()).into()],
        )?
        .l()?;

      if value.as_ref().is_null() {
        return Ok(None);
      }

      Ok(
        unsafe { e.get_string_unchecked(&value.into())? }
          .to_str()
          .ok()
          .map(ToString::to_string),
      )
    })
  }

  fn set_string(&self, key: &str, value: &str) -> anyhow::Result<()> {
    self.execute(|e, preferences| {
      let key = e.new_string(key)?;
      let value = e.new_string(value)?;

      PREFERENCES_SET_STRING
        .get()
        .ok_or(InvariantError::Invariant)?
        .call_method(
          e,
          preferences,
          ReturnType::Primitive(Primitive::Void),
          &[
            JValueWrapper::JObject(key.as_raw()).into(),
            JValueWrapper::JObject(value.as_raw()).into(),
            JValueWrapper::Boolean(JNI_TRUE).into(),
          ],
        )
        .map(|_| ())
    })
  }

  fn delete(&self, key: &str) -> anyhow::Result<()> {
    self.execute(|e, preferences| {
      let key = e.new_string(key)?;

      PREFERENCES_SET_STRING
        .get()
        .ok_or(InvariantError::Invariant)?
        .call_method(
          e,
          preferences,
          ReturnType::Primitive(Primitive::Void),
          &[
            JValueWrapper::JObject(key.as_raw()).into(),
            JValueWrapper::JObject(core::ptr::null_mut()).into(),
            JValueWrapper::Boolean(JNI_TRUE).into(),
          ],
        )
        .map(|_| ())
    })
  }
}
