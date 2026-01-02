// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

use crate::jni::{
  initialize_method_handle,
  AndroidLoggerHolder,
  AndroidLoggerId,
  CachedMethod,
  JValueWrapper,
};
use bd_client_common::error::InvariantError;
use bd_proto::flatbuffers::report::bitdrift_public::fbs::issue_reporting::v_1::{
  AppBuildNumber,
  AppBuildNumberArgs,
  AppMetricsArgs,
  DeviceMetricsArgs,
  OSBuild,
  OSBuildArgs,
  Platform,
  Timestamp,
};
use flatbuffers::FlatBufferBuilder;
use jni::objects::JObject;
use jni::signature::{Primitive, ReturnType};
use jni::sys::jlong;
use jni::JNIEnv;
use platform_shared::javascript_error::persist_javascript_error_report;
use std::io::{Seek, Write};
use std::sync::OnceLock;

const BUFFER_SIZE: i32 = 8192;
static INPUT_STREAM_READ: OnceLock<CachedMethod> = OnceLock::new();
static CLIENT_ATTRS_APP_ID: OnceLock<CachedMethod> = OnceLock::new();
static CLIENT_ATTRS_APP_VERSION: OnceLock<CachedMethod> = OnceLock::new();
static CLIENT_ATTRS_VERSIONCODE: OnceLock<CachedMethod> = OnceLock::new();
static CLIENT_ATTRS_MANUFACTURER: OnceLock<CachedMethod> = OnceLock::new();
static CLIENT_ATTRS_MODEL: OnceLock<CachedMethod> = OnceLock::new();
static CLIENT_ATTRS_OS_VERSION: OnceLock<CachedMethod> = OnceLock::new();
static CLIENT_ATTRS_OS_BRAND: OnceLock<CachedMethod> = OnceLock::new();
static CLIENT_ATTRS_OS_API_LEVEL: OnceLock<CachedMethod> = OnceLock::new();
static CLIENT_ATTRS_SUPPORTED_ABIS: OnceLock<CachedMethod> = OnceLock::new();
static CLIENT_ATTRS_ARCHITECTURE: OnceLock<CachedMethod> = OnceLock::new();
static CLIENT_ATTRS_LOCALE: OnceLock<CachedMethod> = OnceLock::new();

pub(crate) fn initialize(env: &mut JNIEnv<'_>) -> anyhow::Result<()> {
  initialize_method_handle(
    env,
    "java/io/InputStream",
    "read",
    "([B)I",
    &INPUT_STREAM_READ,
  )?;

  initialize_method_handle(
    env,
    "io/bitdrift/capture/attributes/IClientAttributes",
    "getAppId",
    "()Ljava/lang/String;",
    &CLIENT_ATTRS_APP_ID,
  )?;

  initialize_method_handle(
    env,
    "io/bitdrift/capture/attributes/IClientAttributes",
    "getAppVersion",
    "()Ljava/lang/String;",
    &CLIENT_ATTRS_APP_VERSION,
  )?;

  initialize_method_handle(
    env,
    "io/bitdrift/capture/attributes/IClientAttributes",
    "getAppVersionCode",
    "()J",
    &CLIENT_ATTRS_VERSIONCODE,
  )?;

  initialize_method_handle(
    env,
    "io/bitdrift/capture/attributes/IClientAttributes",
    "getManufacturer",
    "()Ljava/lang/String;",
    &CLIENT_ATTRS_MANUFACTURER,
  )?;

  initialize_method_handle(
    env,
    "io/bitdrift/capture/attributes/IClientAttributes",
    "getModel",
    "()Ljava/lang/String;",
    &CLIENT_ATTRS_MODEL,
  )?;

  initialize_method_handle(
    env,
    "io/bitdrift/capture/attributes/IClientAttributes",
    "getOsVersion",
    "()Ljava/lang/String;",
    &CLIENT_ATTRS_OS_VERSION,
  )?;

  initialize_method_handle(
    env,
    "io/bitdrift/capture/attributes/IClientAttributes",
    "getOsBrand",
    "()Ljava/lang/String;",
    &CLIENT_ATTRS_OS_BRAND,
  )?;

  initialize_method_handle(
    env,
    "io/bitdrift/capture/attributes/IClientAttributes",
    "getOsApiLevel",
    "()I",
    &CLIENT_ATTRS_OS_API_LEVEL,
  )?;

  initialize_method_handle(
    env,
    "io/bitdrift/capture/attributes/IClientAttributes",
    "getSupportedAbis",
    "()Ljava/util/List;",
    &CLIENT_ATTRS_SUPPORTED_ABIS,
  )?;

  initialize_method_handle(
    env,
    "io/bitdrift/capture/attributes/IClientAttributes",
    "getArchitecture",
    "()Ljava/lang/String;",
    &CLIENT_ATTRS_ARCHITECTURE,
  )?;

  initialize_method_handle(
    env,
    "io/bitdrift/capture/attributes/IClientAttributes",
    "getLocale",
    "()Ljava/lang/String;",
    &CLIENT_ATTRS_LOCALE,
  )?;

  Ok(())
}

pub(crate) fn persist_anr(
  env: &mut JNIEnv<'_>,
  logger: &AndroidLoggerHolder,
  source_stream: &JObject<'_>,
  timestamp_millis: jlong,
  destination: &str,
) -> anyhow::Result<()> {
  let mut builder = FlatBufferBuilder::new();
  let source_file = read_stream_to_file(env, source_stream)?;
  let source_memmap = unsafe { memmap2::Mmap::map(&source_file)? };
  let source_view = bd_report_parsers::MemmapView::new(&source_memmap);
  let timestamp = Timestamp::new(
    u64::try_from(timestamp_millis / 1_000).unwrap_or_default(),
    u32::try_from((timestamp_millis % 1_000) * 1_000).unwrap_or_default(),
  );
  let mut device_info = build_device_metrics(logger, &mut builder, &timestamp);
  let mut app_info = build_app_metrics(logger, &mut builder);
  let (_, report_offset) = bd_report_parsers::android::build_anr(
    &mut builder,
    &mut app_info,
    &mut device_info,
    source_view,
  )
  .map_err(|e| anyhow::anyhow!("failed to parse ANR report: {e}"))?;

  builder.finish(report_offset, None);
  std::fs::write(destination, builder.finished_data())?;
  log::trace!("persisted report from {timestamp_millis}");
  Ok(())
}

pub(crate) fn persist_javascript_error(
  logger_id: jlong,
  error_name: &str,
  error_message: &str,
  stack_trace: &str,
  is_fatal: bool,
  engine: &str,
  debug_id: &str,
  timestamp_millis: jlong,
  destination: &str,
  sdk_version: &str,
) -> anyhow::Result<()> {
  let logger = unsafe { AndroidLoggerId::from_raw(logger_id) };

  let debug_id = if debug_id.is_empty() {
    None
  } else {
    Some(debug_id)
  };

  let timestamp_seconds = u64::try_from(timestamp_millis / 1_000).unwrap_or_default();
  let timestamp_nanos = u32::try_from((timestamp_millis % 1_000) * 1_000).unwrap_or_default();

  persist_javascript_error_report(
    error_name,
    error_message,
    stack_trace,
    is_fatal,
    debug_id,
    timestamp_seconds,
    timestamp_nanos,
    Platform::Android,
    "io.bitdrift.capture-android",
    sdk_version,
    destination,
    logger.metadata.to_device_metadata(),
    logger.metadata.to_app_metadata(),
    engine,
  )?;

  Ok(())
}

fn build_device_metrics<'fbb>(
  logger: &AndroidLoggerHolder,
  builder: &mut FlatBufferBuilder<'fbb>,
  timestamp: &'fbb Timestamp,
) -> DeviceMetricsArgs<'fbb> {
  let os_build = OSBuildArgs {
    brand: Some(builder.create_string(&logger.metadata.os_brand)),
    version: Some(builder.create_string(&logger.metadata.os_version)),
    ..Default::default()
  };

  DeviceMetricsArgs {
    manufacturer: Some(builder.create_string(&logger.metadata.manufacturer)),
    model: Some(builder.create_string(&logger.metadata.mobile.model)),
    os_build: Some(OSBuild::create(builder, &os_build)),
    time: Some(timestamp),
    ..Default::default()
  }
}

fn build_app_metrics<'fbb>(
  logger: &AndroidLoggerHolder,
  builder: &mut FlatBufferBuilder<'fbb>,
) -> AppMetricsArgs<'fbb> {
  let build_number = Some(AppBuildNumber::create(
    builder,
    &AppBuildNumberArgs {
      version_code: logger.metadata.app_version_code,
      ..Default::default()
    },
  ));

  AppMetricsArgs {
    app_id: Some(builder.create_string(&logger.metadata.mobile.app_id)),
    version: Some(builder.create_string(&logger.metadata.mobile.app_version)),
    build_number,
    ..Default::default()
  }
}

fn read_stream_to_file(
  env: &mut JNIEnv<'_>,
  stream: &JObject<'_>,
) -> anyhow::Result<std::fs::File> {
  let mut file = tempfile::tempfile()?;
  let buffer = env.new_byte_array(BUFFER_SIZE)?;
  let reader = INPUT_STREAM_READ.get().ok_or(InvariantError::Invariant)?;

  loop {
    let bytes_read = reader
      .call_method(
        env,
        stream,
        ReturnType::Primitive(Primitive::Int),
        &[JValueWrapper::JObject(buffer.as_raw()).into()],
      )?
      .i()?;

    if bytes_read <= 0 {
      break;
    }

    let buffer_elements =
      unsafe { env.get_array_elements(&buffer, jni::objects::ReleaseMode::NoCopyBack)? };

    // Safety: `bytes_read` is already verified to by greater than zero
    #[allow(clippy::cast_sign_loss)]
    let byte_slice = &buffer_elements[.. bytes_read as usize];

    // Safety: conversion between i8 and u8 is inherently safe, as the types are
    // equal in size and in the perverse case that a file somehow contains a
    // negative byte (??), the sign bit would be interpreted as an additional
    // value bit instead. Conversion using `as` is also possible between the two
    // types directly though not through additional layers of references, as we
    // need here.
    //
    // For our purposes, encountering a negative byte means potential parsing
    // failure but not any catastrophic failure modes.
    let file_contents = unsafe { &*(std::ptr::from_ref(byte_slice) as *const [u8]) };
    let bytes_written = file.write(file_contents)?;
    if i32::try_from(bytes_written).unwrap_or_default() != bytes_read {
      anyhow::bail!("failed to write bytes read");
    }
  }
  file.seek(std::io::SeekFrom::Start(0))?;
  Ok(file)
}
