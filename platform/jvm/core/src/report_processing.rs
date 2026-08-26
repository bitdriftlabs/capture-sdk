// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

use crate::jni::{CachedMethod, JValueWrapper, initialize_method_handle};
use bd_client_common::error::InvariantError;
use bd_proto::flatbuffers::report::bitdrift_public::fbs::issue_reporting::v_1::{
  AppBuildNumber,
  AppBuildNumberArgs,
  AppMetricsArgs,
  Architecture,
  DeviceMetricsArgs,
  MemoryPressureLevel,
  OSBuild,
  OSBuildArgs,
  Platform,
  Timestamp,
};
use flatbuffers::FlatBufferBuilder;
use jni::JNIEnv;
use jni::objects::{JObject, JString};
use jni::signature::{Primitive, ReturnType};
use jni::sys::{jint, jlong};
use platform_shared::javascript_error::{
  AppMetadata,
  DeviceMetadata,
  persist_javascript_error_report,
};
use platform_shared::metadata::Mobile;
use std::io::{Seek, Write};
use std::sync::OnceLock;

const BUFFER_SIZE: i32 = 8192;
static INPUT_STREAM_READ: OnceLock<CachedMethod> = OnceLock::new();
static CLIENT_ATTRS_OS_BRAND: OnceLock<CachedMethod> = OnceLock::new();
static CLIENT_ATTRS_SUPPORTED_ABIS: OnceLock<CachedMethod> = OnceLock::new();
static CLIENT_ATTRS_LOCALE_COUNTRY_CODE: OnceLock<CachedMethod> = OnceLock::new();

//
// AndroidReportContext
//

/// Dependencies shared by Android report builders.
pub(crate) struct AndroidReportContext<'a, 'env_local, 'metadata, 'attributes, 'object> {
  pub env: &'a mut JNIEnv<'env_local>,
  pub metadata: &'metadata Mobile,
  pub attributes: &'attributes JObject<'object>,
}

//
// AnrReport
//

/// Per-report input used to build an Android ANR report.
pub(crate) struct AnrReport<'a, 'local> {
  pub source_stream: Option<&'a JObject<'local>>,
  pub timestamp_millis: jlong,
  pub destination: &'a str,
  pub running_state: Option<&'a str>,
  pub app_exit_description: Option<&'a str>,
  pub memory_pressure_level: jint,
  pub is_file_size_optimization_enabled: bool,
}

//
// JavaScriptErrorReport
//

/// Per-report input used to build an Android JavaScript error report.
pub(crate) struct JavaScriptErrorReport<'a> {
  pub error_name: &'a str,
  pub error_message: &'a str,
  pub stack_trace: &'a str,
  pub is_fatal: bool,
  pub engine: &'a str,
  pub debugger_id: &'a str,
  pub timestamp_millis: jlong,
  pub destination: &'a str,
  pub sdk_version: &'a str,
}

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
    "getOsBrand",
    "()Ljava/lang/String;",
    &CLIENT_ATTRS_OS_BRAND,
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
    "getLocaleCountryCode",
    "()Ljava/lang/String;",
    &CLIENT_ATTRS_LOCALE_COUNTRY_CODE,
  )?;

  Ok(())
}

pub(crate) fn persist_anr(
  context: &mut AndroidReportContext<'_, '_, '_, '_, '_>,
  report: &AnrReport<'_, '_>,
) -> anyhow::Result<()> {
  let mut builder = FlatBufferBuilder::new();
  let source_file = report
    .source_stream
    .map(|stream| read_stream_to_file(context.env, stream))
    .transpose()?;
  let source_memmap = source_file
    .as_ref()
    .map(|file| unsafe { memmap2::Mmap::map(file) })
    .transpose()?;
  let source_view = source_memmap
    .as_ref()
    .map(bd_report_parsers::MemmapView::new);
  let timestamp = Timestamp::new(
    u64::try_from(report.timestamp_millis / 1_000).unwrap_or_default(),
    u32::try_from((report.timestamp_millis % 1_000) * 1_000).unwrap_or_default(),
  );
  let mut device_info = build_device_metrics(context, &mut builder, &timestamp)?;
  let mut app_info = build_app_metrics(context, &mut builder, report)?;
  let report_offset = bd_report_parsers::android::build_anr_from_app_exit(
    &mut builder,
    &mut app_info,
    &mut device_info,
    source_view,
    report.app_exit_description,
    report.is_file_size_optimization_enabled,
  );

  builder.finish(report_offset, None);
  std::fs::write(report.destination, builder.finished_data())?;
  log::trace!("persisted report from {}", report.timestamp_millis);
  Ok(())
}

pub(crate) fn persist_javascript_error(
  context: &mut AndroidReportContext<'_, '_, '_, '_, '_>,
  report: &JavaScriptErrorReport<'_>,
) -> anyhow::Result<()> {
  let debug_id = if report.debugger_id.is_empty() {
    None
  } else {
    Some(report.debugger_id)
  };

  let timestamp_seconds = u64::try_from(report.timestamp_millis / 1_000).unwrap_or_default();
  let timestamp_nanos =
    u32::try_from((report.timestamp_millis % 1_000) * 1_000).unwrap_or_default();

  let os_brand = read_string(context.env, context.attributes, &CLIENT_ATTRS_OS_BRAND).ok();
  let architecture =
    context
      .metadata
      .android_static_fields()
      .map_or(Architecture::Unknown, |fields| {
        match fields.architecture.as_str() {
          "arm64" | "aarch64" => Architecture::arm64,
          "x86_64" => Architecture::x86_64,
          _ => Architecture::Unknown,
        }
      });
  let cpu_abis = read_string_list(
    context.env,
    context.attributes,
    &CLIENT_ATTRS_SUPPORTED_ABIS,
  )
  .ok();

  let device_metadata = DeviceMetadata {
    manufacturer: context
      .metadata
      .android_static_fields()
      .map(|fields| fields.manufacturer.clone()),
    model: Some(context.metadata.model.clone()),
    os_version: context.metadata.os_version.clone(),
    os_brand,
    architecture: Some(architecture),
    cpu_abis,
  };

  let app_metadata = AppMetadata {
    app_id: context.metadata.app_id.clone(),
    app_version: context.metadata.app_version.clone(),
    version_code: context
      .metadata
      .android_static_fields()
      .map(|fields| fields.app_version_code),
  };

  persist_javascript_error_report(
    report.error_name,
    report.error_message,
    report.stack_trace,
    report.is_fatal,
    debug_id,
    timestamp_seconds,
    timestamp_nanos,
    Platform::Android,
    "io.bitdrift.capture-android",
    report.sdk_version,
    report.destination,
    device_metadata,
    app_metadata,
    report.engine,
  )?;

  Ok(())
}

fn build_device_metrics<'fbb>(
  context: &mut AndroidReportContext<'_, '_, '_, '_, '_>,
  builder: &mut FlatBufferBuilder<'fbb>,
  timestamp: &'fbb Timestamp,
) -> anyhow::Result<DeviceMetricsArgs<'fbb>> {
  let os_brand = read_string(context.env, context.attributes, &CLIENT_ATTRS_OS_BRAND)
    .map_err(|e| anyhow::anyhow!("failed to parse brand: {e}"))?;
  let os_version = context
    .metadata
    .os_version
    .as_deref()
    .ok_or_else(|| anyhow::anyhow!("missing static os version"))?;
  let manufacturer = context
    .metadata
    .android_static_fields()
    .ok_or_else(|| anyhow::anyhow!("missing Android static metadata"))?
    .manufacturer
    .as_str();
  let os_build = OSBuildArgs {
    brand: Some(builder.create_string(&os_brand)),
    version: Some(builder.create_string(os_version)),
    ..Default::default()
  };
  Ok(DeviceMetricsArgs {
    manufacturer: Some(builder.create_string(manufacturer)),
    model: Some(builder.create_string(&context.metadata.model)),
    os_build: Some(OSBuild::create(builder, &os_build)),
    time: Some(timestamp),
    ..Default::default()
  })
}

fn build_app_metrics<'fbb>(
  context: &mut AndroidReportContext<'_, '_, '_, '_, '_>,
  builder: &mut FlatBufferBuilder<'fbb>,
  report: &AnrReport<'_, '_>,
) -> anyhow::Result<AppMetricsArgs<'fbb>> {
  let version_code = context
    .metadata
    .android_static_fields()
    .ok_or_else(|| anyhow::anyhow!("missing Android static metadata"))?
    .app_version_code;
  let build_number = Some(AppBuildNumber::create(
    builder,
    &AppBuildNumberArgs {
      version_code,
      ..Default::default()
    },
  ));
  let app_id = context
    .metadata
    .app_id
    .as_deref()
    .ok_or_else(|| anyhow::anyhow!("missing static app id"))?;
  let app_version = context
    .metadata
    .app_version
    .as_deref()
    .ok_or_else(|| anyhow::anyhow!("missing static app version"))?;
  // failable/optional value
  let region_format = read_string(
    context.env,
    context.attributes,
    &CLIENT_ATTRS_LOCALE_COUNTRY_CODE,
  )
  .ok();
  Ok(AppMetricsArgs {
    app_id: Some(builder.create_string(app_id)),
    version: Some(builder.create_string(app_version)),
    build_number,
    region_format: region_format.map(|s| builder.create_string(&s)),
    running_state: report.running_state.map(|s| builder.create_string(s)),
    memory_pressure_level: MemoryPressureLevel(
      i8::try_from(report.memory_pressure_level).unwrap_or(0),
    ),
    ..Default::default()
  })
}

fn read_string(
  env: &mut JNIEnv<'_>,
  attributes: &JObject<'_>,
  method: &OnceLock<CachedMethod>,
) -> anyhow::Result<String> {
  let value = method
    .get()
    .ok_or(InvariantError::Invariant)?
    .call_method(env, attributes, ReturnType::Object, &[])?
    .l()?;

  let value = JString::from(value);
  Ok(
    unsafe { env.get_string_unchecked(&value)? }
      .to_string_lossy()
      .to_string(),
  )
}

fn read_string_list(
  env: &mut JNIEnv<'_>,
  attributes: &JObject<'_>,
  method: &OnceLock<CachedMethod>,
) -> anyhow::Result<Vec<String>> {
  let list_obj = method
    .get()
    .ok_or(InvariantError::Invariant)?
    .call_method(env, attributes, ReturnType::Object, &[])?
    .l()?;

  let list = jni::objects::JList::from_env(env, &list_obj)?;
  let size = list.size(env)?;
  let mut result = Vec::with_capacity(size.max(0).try_into().unwrap_or(0));

  for i in 0 .. size {
    if let Some(item) = list.get(env, i)? {
      let string_obj: jni::objects::JString<'_> = item.into();
      let string_val = unsafe { env.get_string_unchecked(&string_obj)? };
      result.push(string_val.to_string_lossy().to_string());
    }
  }

  Ok(result)
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
