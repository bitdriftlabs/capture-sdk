// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

use bd_logger::{AnnotatedLogField, AnnotatedLogFields, LogFieldKind, LogFieldValue};
use logger::RustLogger;

mod logger;

struct LogField {
  key: String,
  value: String,
}

struct Logger {
  inner: RustLogger,
}

#[ctor::ctor]
fn init_logging() {
  bd_log::SwapLogger::initialize();
}

impl Logger {
  #[allow(clippy::needless_pass_by_value)]
  fn new(
    api_key: String,
    api_address: String,
    sdk_directory: String,
    app_id: String,
    app_version: String,
    platform: String,
    platform_version: String,
    locale: String,
  ) -> anyhow::Result<Self> {
    Ok(Self {
      inner: RustLogger::new(
        api_key,
        &api_address,
        sdk_directory,
        app_id,
        app_version,
        platform,
        platform_version,
        locale,
      )?,
    })
  }

  fn session_id(&self) -> anyhow::Result<String> {
    Ok(self.inner.session_id())
  }

  pub fn device_id(&self) -> anyhow::Result<String> {
    Ok(self.inner.device_id())
  }

  pub fn log(&self, level: u32, message: String, fields: Vec<LogField>) -> anyhow::Result<()> {
    let fields: AnnotatedLogFields = fields
      .into_iter()
      .map(|field| AnnotatedLogField {
        kind: LogFieldKind::Custom,
        field: bd_logger::LogField {
          key: field.key,
          value: LogFieldValue::String(field.value),
        },
      })
      .collect();

    tracing::trace!("Logging message: {level} {message} with fields {fields:?}");

    // Safety: We know that the level corresponds to a valid log_level due to us controlling the
    // call site and only passing 0-4 integer values.
    #[allow(clippy::cast_possible_truncation, clippy::cast_sign_loss)]
    self.inner.log(level, message, fields);

    Ok(())
  }
}

#[cxx::bridge]
mod ffi {
  extern "Rust" {
    type Logger;
    type LogField;

    fn new_logger(
      api_key: String,
      api_address: String,
      sdk_directory: String,
      app_id: String,
      app_version: String,
      platform: String,
      platform_version: String,
      locale: String,
    ) -> Result<Box<Logger>>;

    fn session_id(self: &Logger) -> Result<String>;
    fn device_id(self: &Logger) -> Result<String>;
    fn log(self: &Logger, level: u32, message: String, fields: Vec<LogField>) -> Result<()>;
  }
}

fn new_logger(
  api_key: String,
  api_address: String,
  sdk_directory: String,
  app_id: String,
  app_version: String,
  platform: String,
  platform_version: String,
  locale: String,
) -> anyhow::Result<Box<Logger>> {
  Ok(Box::new(Logger::new(
    api_key,
    api_address,
    sdk_directory,
    app_id,
    app_version,
    platform,
    platform_version,
    locale,
  )?))
}
