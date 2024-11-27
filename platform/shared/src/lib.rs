// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

pub mod error;
pub mod metadata;

include!(concat!(env!("OUT_DIR"), "/version.rs"));

use bd_client_common::error::handle_unexpected;
use bd_logger::{log_level, AnnotatedLogField, LogField, LogFieldKind, LogType};
use bd_runtime::runtime::Snapshot;
use parking_lot::Once;
use std::future::Future;
use std::ops::Deref;
use std::pin::Pin;
use std::sync::Arc;


/// This is the logger ID that is passed to the platform code. It is a typed wrapper around an i64
/// that encodes the pointer to the `LoggerHolder` object.
///
/// We use a fixed size representation to ensure that we can always fit a pointer in it regardless
/// of the platform (assuming 32 or 64 bit pointers).
///
/// We use a signed type since the JVM doesn't support unsigned, though it doesn't matter too much
/// since it's really just an opaque 64 bit value.
///
/// Thanks to the captured lifetime, it is not possible to send or outside of the current function
/// scope when provided as `LoggerId<'_>` (note that `LoggerId<'static>` bypasses this .
///
/// ```compile_fail
/// fn f(logger: platform_shared::LoggerId<'_>) {
///   std::thread::spawn(move || {
///     logger.logger_holder().shutdown(false);
///   });
/// }
/// ```
///
/// This means that it is relatively safe to use `LoggerId<'_>` in FFI functions, as it is treated
/// exactly like a `i64` in terms of memory layout and passing it around but we get an automatic
/// conversion to a `LoggerHolder` when we provide it over FFI. As the FFI calls are fundamentally
/// unsafe, this seems no worse than passing i64 and doing an unsafe call at the start of the
/// function.
#[repr(transparent)]
pub struct LoggerId<'a> {
  value: i64,
  // A fake lifetime to allow us to link a non-static lifetime to the type, which allows us to
  // limit its usage outside of the current function scope.
  _lifetime: std::marker::PhantomData<&'a ()>,
}

impl LoggerId<'_> {
  /// Creates a new `LoggerId` from a raw pointer to a `LoggerHolder`. Use this in cases where you
  /// need a manual conversion from an i64 to a `LoggerId`.
  ///
  /// # Safety
  /// The provided pointer *must* be a valid pointer to a `LoggerHolder` object.
  #[must_use]
  pub const unsafe fn from_raw(value: i64) -> Self {
    Self {
      value,
      _lifetime: std::marker::PhantomData,
    }
  }

  /// Returns a reference to the `LoggerHolder` object that this `LoggerId` represents. This is
  /// safe because all instances of `LoggerId` are assumed to wrap a valid `LoggerHolder` object.
  const fn logger_holder(&self) -> &LoggerHolder {
    unsafe { &*(self.value as *const LoggerHolder) }
  }
}

impl Deref for LoggerId<'_> {
  type Target = LoggerHolder;

  fn deref(&self) -> &Self::Target {
    self.logger_holder()
  }
}

pub type LoggerFuture =
  Pin<Box<dyn Future<Output = anyhow::Result<()>> + 'static + std::marker::Send>>;

//
// LoggerHolder
//

/// A wrapper around the logger and a handle to it. The platform code passes a pointer to this
/// struct as the logger ID, allowing the ffi functions to cast this to the correct type and access
/// these fields to support the logging API.
pub struct LoggerHolder {
  logger: bd_logger::Logger,
  handle: bd_logger::LoggerHandle,
  future: parking_lot::Mutex<Option<LoggerFuture>>,
  app_launch_tti_log: Once,
}

impl Deref for LoggerHolder {
  type Target = bd_logger::LoggerHandle;

  fn deref(&self) -> &Self::Target {
    &self.handle
  }
}

impl LoggerHolder {
  pub fn new(logger: bd_logger::Logger, future: LoggerFuture) -> Self {
    let handle = logger.new_logger_handle();
    Self {
      logger,
      handle,
      future: parking_lot::Mutex::new(Some(future)),
      app_launch_tti_log: Once::new(),
    }
  }

  pub fn start(&self) {
    let Some(future) = self.future.lock().take() else {
      return;
    };

    std::thread::spawn(move || {
      tokio::runtime::Builder::new_current_thread()
        .enable_all()
        .build()
        .unwrap()
        .block_on(async {
          handle_unexpected(future.await, "logger top level run loop");
        });
    });
  }

  /// Consumes the logger and returns the raw pointer to it. This effectively leaks the object, so
  /// in order to avoid leaks the caller must ensure that the `destroy` is called with the returned
  /// value.
  pub fn into_raw<'a>(self) -> LoggerId<'a> {
    LoggerId {
      value: Box::into_raw(Box::new(self)) as i64,
      _lifetime: std::marker::PhantomData,
    }
  }

  /// Shuts down the logger, blocking until the logger has finished shutdown if `block` is true.
  pub fn shutdown(&self, block: bool) {
    self.logger.shutdown(block);
  }

  /// Returns a snapshot of the runtime state of the logger.
  pub fn runtime_snapshot(&self) -> Arc<Snapshot> {
    self.logger.runtime_snapshot()
  }

  /// Given a valid logger ID, destroys the logger and frees the memory associated with it.
  ///
  /// # Safety
  /// The provided id *must* correspond to the pointer of a valid `LoggerHolder` as returned by
  /// `into_raw`. This function *cannot* be called multiple times for the same id.
  pub unsafe fn destroy(id: i64) {
    let holder = Box::from_raw(id as *mut Self);
    holder.shutdown(false);
    drop(holder);
  }

  /// Logs an out-of-the-box app launch TTI log event. The method should be called only once.
  /// Consecutive calls have not effect.
  pub fn log_app_launch_tti(&self, duration: time::Duration) {
    self.app_launch_tti_log.call_once(|| {
      let duration_ms = duration.as_seconds_f64() * 1_000f64;
      if duration_ms < 0.0 {
        log::warn!(
          "dropping app launch TTI log: reported TTI is negative: {}",
          duration_ms
        );
        return;
      }

      let fields = vec![AnnotatedLogField {
        field: LogField {
          key: "_duration_ms".into(),
          value: duration_ms.to_string().into(),
        },
        kind: LogFieldKind::Ootb,
      }];

      self.log(
        log_level::INFO,
        LogType::Lifecycle,
        "AppLaunchTTI".into(),
        fields,
        vec![],
        None,
        false,
      );
    });
  }
}

impl<'a> From<LoggerId<'a>> for i64 {
  fn from(logger: LoggerId<'a>) -> Self {
    logger.value
  }
}
