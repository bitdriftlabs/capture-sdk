// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

use crate::jni::{initialize_method_handle, CachedMethod, JValueWrapper};
use bd_client_common::error::InvariantError;
use bd_error_reporter::reporter::with_handle_unexpected;
use bd_proto::flatbuffers::report::bitdrift_public::fbs::issue_reporting::v_1::{
  AppBuildNumber,
  AppBuildNumberArgs,
  AppMetricsArgs,
  DeviceMetricsArgs,
  OSBuild,
  OSBuildArgs,
};
use flatbuffers::FlatBufferBuilder;
use jni::objects::{JObject, JString};
use jni::signature::{Primitive, ReturnType};
use jni::sys::jlong;
use jni::JNIEnv;
use std::sync::OnceLock;

const BUFFER_SIZE: i32 = 8192;
static INPUT_STREAM_READ: OnceLock<CachedMethod> = OnceLock::new();

pub(crate) fn initialize(env: &mut JNIEnv<'_>) -> anyhow::Result<()> {
  initialize_method_handle(
    env,
    "java/io/InputStream",
    "read",
    "([B)I",
    &INPUT_STREAM_READ,
  )?;

  Ok(())
}

pub(crate) fn report_anr(
  mut env: JNIEnv<'_>,
  stream: &JObject<'_>,
  destination: &JString<'_>,
  manufacturer: &JString<'_>,
  model: &JString<'_>,
  os_version: &JString<'_>,
  os_brand: &JString<'_>,
  app_id: &JString<'_>,
  app_version: &JString<'_>,
  version_code: jlong,
) {
  with_handle_unexpected(
    || -> anyhow::Result<()> {
      let mut stream_bytes = vec![];
      read_stream(&mut env, stream, &mut stream_bytes)?;
      let stream_slice = stream_bytes.as_slice();
      let input = std::str::from_utf8(unsafe {
        &*(std::ptr::from_ref::<[i8]>(stream_slice as &[i8]) as *const [u8])
      })?;
      let mut builder = FlatBufferBuilder::new();
      let destination = unsafe { env.get_string_unchecked(destination) }
        .map_err(|e| anyhow::anyhow!("failed to parse destination: {e}"))?
        .to_string_lossy()
        .to_string();
      let manufacturer = unsafe { env.get_string_unchecked(manufacturer) }
        .map_err(|e| anyhow::anyhow!("failed to parse manufacturer: {e}"))?
        .to_string_lossy()
        .to_string();
      let model = unsafe { env.get_string_unchecked(model) }
        .map_err(|e| anyhow::anyhow!("failed to parse model: {e}"))?
        .to_string_lossy()
        .to_string();
      let os_brand = unsafe { env.get_string_unchecked(os_brand) }
        .map_err(|e| anyhow::anyhow!("failed to parse os_brand: {e}"))?
        .to_string_lossy()
        .to_string();
      let os_version = unsafe { env.get_string_unchecked(os_version) }
        .map_err(|e| anyhow::anyhow!("failed to parse os_version: {e}"))?
        .to_string_lossy()
        .to_string();
      let os_build = OSBuildArgs {
        brand: Some(builder.create_string(&os_brand)),
        version: Some(builder.create_string(&os_version)),
        ..Default::default()
      };
      let mut device_info = DeviceMetricsArgs {
        manufacturer: Some(builder.create_string(&manufacturer)),
        model: Some(builder.create_string(&model)),
        os_build: Some(OSBuild::create(&mut builder, &os_build)),
        ..Default::default()
      };
      let build_number = Some(AppBuildNumber::create(
        &mut builder,
        &AppBuildNumberArgs {
          version_code,
          ..Default::default()
        },
      ));
      let app_id = unsafe { env.get_string_unchecked(app_id) }
        .map_err(|e| anyhow::anyhow!("failed to parse app_id: {e}"))?
        .to_string_lossy()
        .to_string();
      let app_version = unsafe { env.get_string_unchecked(app_version) }
        .map_err(|e| anyhow::anyhow!("failed to parse app_version: {e}"))?
        .to_string_lossy()
        .to_string();
      let mut app_info = AppMetricsArgs {
        app_id: Some(builder.create_string(&app_id)),
        version: Some(builder.create_string(&app_version)),
        build_number,
        ..Default::default()
      };
      let mut event_time = None;
      let (_, report_offset) = bd_report_parsers::android::build_anr(
        &mut builder,
        &mut app_info,
        &mut device_info,
        &mut event_time,
        input,
      )?;

      builder.finish(report_offset, None);
      let contents = builder.finished_data();
      std::fs::write(destination, contents)?;
      Ok(())
    },
    "jni process ANR",
  );
}

fn read_stream(
  env: &mut JNIEnv<'_>,
  stream: &JObject<'_>,
  contents: &mut Vec<i8>,
) -> anyhow::Result<()> {
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
    #[allow(clippy::cast_sign_loss)]
    contents.extend_from_slice(&buffer_elements[.. bytes_read as usize]);
  }
  contents.push(0);
  Ok(())
}
