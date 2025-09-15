// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

#![deny(
  clippy::expect_used,
  clippy::panic,
  clippy::todo,
  clippy::unimplemented,
  clippy::unreachable,
  clippy::unwrap_used
)]

pub mod error;
pub mod feature_flags;
pub mod ffi_wrapper;
pub mod metadata;

use bd_error_reporter::reporter::handle_unexpected;
use bd_logger::{
  log_level,
  AnnotatedLogField,
  LogFieldKind,
  LogType,
  LoggerBuilder,
  ReportProcessingSession,
};
use bd_runtime::runtime::Snapshot;
use parking_lot::Once;
use std::future::Future;
use std::ops::Deref;
use std::pin::Pin;
use std::sync::Arc;

// Use the generic FFI ID system for LoggerId
crate::ffi_id_for!(LoggerHolder, LoggerId);

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

    // Start the logger runtime using the defaults provided by the logger builder.
    handle_unexpected(LoggerBuilder::run_logger_runtime(future), "logger runtime");
  }

  /// Consumes the logger and returns the raw pointer to it. This effectively leaks the object, so
  /// in order to avoid leaks the caller must ensure that the `destroy` is called with the returned
  /// value.
  pub fn into_raw<'a>(self) -> LoggerId<'a> {
    unsafe { LoggerId::from_raw(Box::into_raw(Box::new(self)) as i64) }
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
        log::warn!("dropping app launch TTI log: reported TTI is negative: {duration_ms}");
        return;
      }

      let fields = [(
        "_duration_ms".into(),
        AnnotatedLogField {
          value: duration_ms.to_string().into(),
          kind: LogFieldKind::Ootb,
        },
      )]
      .into();

      self.log(
        log_level::INFO,
        LogType::Lifecycle,
        "AppLaunchTTI".into(),
        fields,
        [].into(),
        None,
        bd_logger::Block::No,
        bd_logger::CaptureSession::default(),
      );
    });
  }

  pub fn process_crash_reports(&mut self, session: ReportProcessingSession) {
    if let Err(e) = self.logger.process_crash_reports(session) {
      log::error!("failed to process crash reports: {e}");
    }
  }

  pub fn log_screen_view(&self, screen_name: String) {
    let fields = [(
      "_screen_name".into(),
      AnnotatedLogField::new_ootb(screen_name),
    )]
    .into();

    self.log(
      log_level::INFO,
      LogType::UX,
      "ScreenView".into(),
      fields,
      [].into(),
      None,
      bd_logger::Block::No,
      bd_logger::CaptureSession::default(),
    );
  }
}
