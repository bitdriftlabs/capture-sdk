// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

use anyhow::anyhow;
use jni::objects::{GlobalRef, JObject};
use jni::JNIEnv;
use std::sync::Arc;

/// Checks whether there is an active exception via the provided executor, clearing it and
/// returning an error if so.
pub fn check_exception(env: &mut JNIEnv<'_>) -> anyhow::Result<Option<String>> {
  let exception = env.exception_occurred()?;

  if exception.is_null() {
    return Ok(None);
  }

  env.exception_clear()?;

  let exception_string = &env
    .call_method(exception, "toString", "()Ljava/lang/String;", &[])?
    .l()?
    .into();

  let rust_string = env.get_string(exception_string)?;

  Ok(Some(rust_string.into()))
}

//
// ObjectHandle
//

/// A wrapper around a global reference to a `JObject`, allowing for method calls to be made
/// against said object. A global reference can outlive the current scope, allowing for the Java
/// reference to be used from anywhere.
#[derive(Clone)]
pub struct ObjectHandle {
  // A global reference to a Java object. A global reference is necessary in order to
  // provide a reference that can be passed between threads.
  object: GlobalRef,

  /// A handle to an executor which can be used to execute method calls on the Java object. This
  /// is used to ensure that the thread we are executing the Java methods on are attached.
  executor: jni::Executor,
}

impl ObjectHandle {
  pub fn new(env: &JNIEnv<'_>, object: JObject<'_>) -> jni::errors::Result<Self> {
    Ok(Self {
      object: env.new_global_ref(object)?,
      executor: jni::Executor::new(Arc::new(env.get_java_vm()?)),
    })
  }

  pub fn execute<R, F>(&self, f: F) -> anyhow::Result<R>
  where
    F: for<'a> FnOnce(&mut JNIEnv<'a>, &JObject<'a>) -> anyhow::Result<R>,
  {
    self.executor.with_attached(|env| {
      let rval = f(env, self.object.as_obj());

      // When executing Java calls through an Executor we're most likely making a call from the
      // event loop thread (anywhere else we'd have direct JNIEnv access), so clear out the
      // exception here. This ensures that there is no active exception on the thread, which means
      // that if this error results in the event loop stopping the thread won't exit with an
      // active exception, avoiding crash detectors like Bugsnag from flagging it as a
      // crash.
      rval.map_err(move |e| {
        let maybe_exception = check_exception(env);
        // Since this is in the error handling flow we just debug assert instead of using the
        // error handling helper, as that would result in recursion.
        debug_assert!(maybe_exception.is_ok());

        let e = if let Ok(Some(exception)) = maybe_exception {
          anyhow!("failed to execute Java method due to exception: {exception}")
        } else {
          anyhow!("failed to execute Java method: {e}")
        };

        anyhow!("An unexpected error occurred: {e}")
      })
    })
  }
}

impl std::fmt::Debug for ObjectHandle {
  fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result {
    f.debug_struct("ObjectHandle").finish()
  }
}

/// Defines all Java object wrappers. All of these types are a phantom type tagged `ObjectWrapper`,
/// which allows constructing them either as local or as global objects.
#[macro_export]
macro_rules! define_object_wrapper {
  ($name:ident) => {
    #[derive(Clone)]
    pub struct $name(pub $crate::executor::ObjectHandle);

    impl std::ops::Deref for $name {
      type Target = $crate::executor::ObjectHandle;

      fn deref(&self) -> &Self::Target {
        return &self.0;
      }
    }

    impl std::ops::DerefMut for $name {
      fn deref_mut(&mut self) -> &mut Self::Target {
        return &mut self.0;
      }
    }
  };
}

#[macro_export]
macro_rules! new_global {
  ($object_type:ident, $env:expr, $object:expr) => {
    $crate::executor::ObjectHandle::new($env, $object).map(|executor| $object_type(executor))
  };
}
