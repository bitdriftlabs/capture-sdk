// capture-es - bitdrift's ES SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

use bd_logger::{AnnotatedLogField, AnnotatedLogFields, LogField, LogFieldKind, LogFieldValue};
use logger::RustLogger;
use napi::bindgen_prelude::{Buffer, Either4};
use napi::Result;
use napi_derive::napi;
use std::collections::HashMap;

mod logger;

#[cfg(test)]
mod tests;

#[napi]
pub struct Logger {
  inner: RustLogger,
}

#[napi]
impl Logger {
  #[napi(constructor)]
  pub fn new(
    api_key: String,
    api_address: String,
    sdk_directory: String,
    app_id: String,
    app_version: String,
    platform: String,
    platform_version: String,
    locale: String,
  ) -> Result<Self> {
    Ok(Self {
      inner: RustLogger::new(
        api_key,
        api_address,
        sdk_directory,
        app_id,
        app_version,
        platform,
        platform_version,
        locale,
      )?,
    })
  }

  #[napi(getter)]
  pub fn session_id(&self) -> Result<String> {
    Ok(self.inner.session_id())
  }

  #[napi(getter)]
  pub fn device_id(&self) -> Result<String> {
    Ok(self.inner.device_id())
  }

  #[napi]
  pub fn log(
    &self,
    level: u32,
    message: String,
    fields: HashMap<String, Either4<String, u32, bool, Buffer>>,
  ) -> Result<()> {
    let fields: AnnotatedLogFields = fields
      .into_iter()
      .map(|(key, value)| {
        let value: LogFieldValue = match value {
          Either4::A(value) => value.into(),
          Either4::B(value) => value.to_string().into(),
          Either4::C(value) => value.to_string().into(),
          Either4::D(value) => value.to_vec().into(),
        };

        AnnotatedLogField {
          kind: LogFieldKind::Custom,
          field: LogField { key, value },
        }
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
