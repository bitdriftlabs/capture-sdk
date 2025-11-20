// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

use bd_proto::flatbuffers::report::bitdrift_public::fbs::issue_reporting::v_1::{
  self, Architecture, Platform,
};
use bd_report_parsers::javascript::{
  build_javascript_error_report_to_file, JavaScriptAppMetrics, JavaScriptDeviceMetrics,
};

/// Parse JavaScript engine string to enum
pub fn parse_javascript_engine(engine: &str) -> v_1::JavaScriptEngine {
  match engine.to_lowercase().as_str() {
    "hermes" => v_1::JavaScriptEngine::Hermes,
    "jsc" | "javascriptcore" => v_1::JavaScriptEngine::JavaScriptCore,
    _ => v_1::JavaScriptEngine::UnknownJsEngine,
  }
}

/// Device metadata for JavaScript error reports
#[allow(elided_lifetimes_in_paths)]
pub struct DeviceMetadata {
  pub manufacturer: Option<String>,
  pub model: Option<String>,
  pub os_version: Option<String>,
  pub os_brand: Option<String>,
  pub architecture: Option<Architecture>,
  pub cpu_abis: Option<Vec<String>>,
}

/// App metadata for JavaScript error reports
pub struct AppMetadata {
  pub app_id: Option<String>,
  pub app_version: Option<String>,
  pub version_code: Option<i64>,
}

/// Build and persist a JavaScript error report
pub fn persist_javascript_error_report(
  error_name: &str,
  error_message: &str,
  stack_trace: &str,
  is_fatal: bool,
  debug_id: Option<&str>,
  timestamp_seconds: u64,
  timestamp_nanos: u32,
  platform: Platform,
  sdk_id: &str,
  sdk_version: &str,
  destination_path: &str,
  device_metadata: DeviceMetadata,
  app_metadata: AppMetadata,
  engine: &str,
) -> anyhow::Result<()> {
  let javascript_engine = parse_javascript_engine(engine);

  let device_metrics = JavaScriptDeviceMetrics {
    platform,
    manufacturer: device_metadata.manufacturer,
    model: device_metadata.model,
    os_version: device_metadata.os_version,
    os_brand: device_metadata.os_brand,
    os_kernversion: None,
    architecture: device_metadata.architecture,
    cpu_abis: device_metadata.cpu_abis,
  };

  let app_metrics = JavaScriptAppMetrics {
    app_id: app_metadata.app_id,
    version: app_metadata.app_version,
    version_code: app_metadata.version_code,
    javascript_engine,
  };

  build_javascript_error_report_to_file(
    error_name,
    error_message,
    stack_trace,
    is_fatal,
    debug_id,
    timestamp_seconds,
    timestamp_nanos,
    &device_metrics,
    &app_metrics,
    sdk_id,
    sdk_version,
    destination_path,
  )?;

  Ok(())
}
