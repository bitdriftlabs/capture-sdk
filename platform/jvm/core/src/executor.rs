// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

#[cfg(test)]
#[path = "./executor_test.rs"]
mod tests;

use anyhow::anyhow;
use jni::objects::{Global, JObject, JString};
use jni::{Env, JavaVM, jni_sig, jni_str};

/// Checks whether there is an active exception in the provided JNI environment, clearing it and
/// returning an error if so.
pub fn check_exception(env: &mut Env<'_>) -> anyhow::Result<Option<String>> {
  let Some(exception) = env.exception_occurred() else {
    return Ok(None);
  };

  env.exception_clear();

  let exception_string = env
    .call_method(
      exception,
      jni_str!("toString"),
      jni_sig!("()Ljava/lang/String;"),
      &[],
    )?
    .l()?;
  let exception_string = env.cast_local::<JString<'_>>(exception_string)?;

  let rust_string = exception_string.try_to_string(env)?;

  Ok(Some(rust_string))
}

//
// ObjectHandle
//

/// A wrapper around a global reference to a `JObject`, allowing for method calls to be made
/// against said object. A global reference can outlive the current scope, allowing for the Java
/// reference to be used from anywhere.
pub struct ObjectHandle {
  // A global reference to a Java object. A global reference is necessary in order to
  // provide a reference that can be passed between threads.
  object: Global<JObject<'static>>,

  /// JVM handle used to attach the calling thread before invoking methods on the Java object.
  java_vm: JavaVM,
}

impl ObjectHandle {
  pub fn new(env: &Env<'_>, object: JObject<'_>) -> jni::errors::Result<Self> {
    Ok(Self {
      object: env.new_global_ref(object)?,
      java_vm: env.get_java_vm()?,
    })
  }

  pub fn execute<R, F>(&self, f: F) -> anyhow::Result<R>
  where
    F: for<'a> FnOnce(&mut Env<'a>, &JObject<'static>) -> anyhow::Result<R>,
  {
    self.java_vm.attach_current_thread(|env| {
      let rval = f(env, self.object.as_obj());

      // Calls made through an attached thread usually originate on the event-loop thread
      // (elsewhere we have direct Env access), so clear out the
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

/// Defines a wrapper around an `ObjectHandle` for a specific Java object type. This allows for the
/// creation of a new global reference to the Java object, and provides deref implementations to
/// allow for method calls to be made against the underlying `ObjectHandle`.
///
/// These wrappers are used to provide a type-safe way to interact with specific Java objects,
/// ensuring that the correct methods are called on the correct object types. as well to provide a
/// convenient way to implement traits.
#[macro_export]
macro_rules! define_object_wrapper {
  ($name:ident) => {
    pub struct $name(pub $crate::executor::ObjectHandle);

    impl $name {
      pub fn new_global(
        env: &jni::Env<'_>,
        object: jni::objects::JObject<'_>,
      ) -> jni::errors::Result<Self> {
        $crate::executor::ObjectHandle::new(env, object).map(|handle| Self(handle))
      }
    }

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
