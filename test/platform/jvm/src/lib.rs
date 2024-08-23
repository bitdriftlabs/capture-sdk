// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

use assert_matches::assert_matches;
use bd_client_common::error::Reporter;
use bd_client_common::fb::root_as_log;
use bd_proto::flatbuffers::buffer_log::bitdrift_public::fbs::logging::v_1::Data;
use bd_test_helpers::runtime::ValueKind;
use bd_test_helpers::test_api_server::{ExpectedStreamEvent, HandshakeMatcher};
use capture::events::ListenerTargetHandler as EventsListenerTargetHandler;
use capture::executor::ObjectHandle;
use capture::jni::{ErrorReporterHandle, JValueWrapper};
use capture::key_value_storage::PreferencesHandle;
use capture::new_global;
use capture::resource_utilization::TargetHandler as ResourceUtilizationTargetHandler;
use jni::objects::{JClass, JMap, JObject, JString};
use jni::sys::{jint, jlong};
use jni::JNIEnv;
use platform_shared::LoggerId;
use platform_test_helpers::{
  await_api_server_stream_closed,
  await_configuration_ack,
  await_next_api_stream,
  configure_aggressive_continuous_uploads,
  send_configuration_update,
  start_test_api_server,
  stop_test_api_server,
};
use std::collections::HashMap;
use time::Duration;

// See call site for explanation.
#[no_mangle]
#[allow(clippy::missing_const_for_fn)]
pub extern "C" fn link_hack_for_test() {}

#[no_mangle]
pub extern "C" fn Java_io_bitdrift_capture_CaptureTestJniLibrary_startTestApiServer(
  _env: JNIEnv<'_>,
  _class: JClass<'_>,
  ping_interval: jint,
) -> jint {
  start_test_api_server(false, ping_interval)
}

#[ctor::ctor]
fn setup() {
  bd_log::SwapLogger::initialize();
  bd_test_helpers::test_global_init();
}

#[no_mangle]
pub extern "C" fn Java_io_bitdrift_capture_CaptureTestJniLibrary_stopTestApiServer(
  _env: JNIEnv<'_>,
  _class: JClass<'_>,
) {
  stop_test_api_server();
}

#[no_mangle]
pub extern "C" fn Java_io_bitdrift_capture_CaptureTestJniLibrary_awaitNextApiStream(
  _env: JNIEnv<'_>,
  _class: JClass<'_>,
) -> jint {
  await_next_api_stream()
}

#[no_mangle]
pub extern "C" fn Java_io_bitdrift_capture_CaptureTestJniLibrary_awaitApiServerReceivedHandshake(
  mut env: JNIEnv<'_>,
  _class: JClass<'_>,
  stream_id: jint,
  client_attributes: JObject<'_>,
  client_attribute_keys_to_ignore: JObject<'_>,
) -> bool {
  let expected_attributes = if client_attributes.is_null() {
    None
  } else {
    let attributes_map = env.get_map(&client_attributes).unwrap();

    let mut iterator = attributes_map.iter(&mut env).unwrap();

    let mut rust_attributes = HashMap::new();

    while let Some((key, value)) = iterator.next(&mut env).unwrap() {
      rust_attributes.insert(
        env.get_string(&key.into()).unwrap().into(),
        env.get_string(&value.into()).unwrap().into(),
      );
    }

    Some(rust_attributes)
  };

  let expected_attribute_keys_to_ignore = if client_attribute_keys_to_ignore.is_null() {
    None
  } else {
    let attributes_list = env.get_list(&client_attribute_keys_to_ignore).unwrap();

    let mut iterator = attributes_list.iter(&mut env).unwrap();

    let mut rust_keys = vec![];

    while let Some(value) = iterator.next(&mut env).unwrap() {
      rust_keys.push(env.get_string(&value.into()).unwrap().into());
    }

    Some(rust_keys)
  };

  platform_test_helpers::with_expected_server(|h| {
    h.await_event_with_timeout(
      stream_id,
      ExpectedStreamEvent::Handshake(
        HandshakeMatcher {
          attributes: expected_attributes,
          attribute_keys_to_ignore: expected_attribute_keys_to_ignore,
        }
        .into(),
      ),
      Duration::seconds(5),
    )
  })
}

#[no_mangle]
pub extern "C" fn Java_io_bitdrift_capture_CaptureTestJniLibrary_awaitApiServerStreamClosed(
  _env: JNIEnv<'_>,
  _class: JClass<'_>,
  stream_id: jint,
  wait_time_ms: jint,
) -> bool {
  await_api_server_stream_closed(stream_id, wait_time_ms.into())
}

#[no_mangle]
#[rustfmt::skip]
pub extern "C" fn Java_io_bitdrift_capture_CaptureTestJniLibrary_configureAggressiveContinuousUploads(
  _env: JNIEnv<'_>,
  _class: JClass<'_>,
  stream_id: jint,
) {
  configure_aggressive_continuous_uploads(stream_id);
}

#[allow(clippy::cast_possible_wrap)]
#[no_mangle]
pub extern "C" fn Java_io_bitdrift_capture_CaptureTestJniLibrary_nextUploadedLog<'a>(
  mut env: JNIEnv<'a>,
  _class: JClass<'_>,
) -> JObject<'a> {
  platform_test_helpers::with_expected_server(|h| {
    let log_request = h.blocking_next_log_upload().expect("expected log upload");
    let log = root_as_log(&log_request.logs[0]).expect("invalid flatbuffer");

    #[allow(clippy::option_if_let_else)]
    let message: JObject<'_> = if let Some(string_data) = log.message_as_string_data() {
      env.new_string(string_data.data()).unwrap().into()
    } else {
      JObject::null()
    };

    // TODO(Augustyniak): Extract the logic below into a helper function.
    let fields = env.new_object("java/util/HashMap", "()V", &[]).unwrap();
    log.fields().unwrap().iter().fold(
      JMap::from_env(&mut env, &fields).unwrap(),
      |fields, field| {
        let key = env.new_string(field.key()).unwrap();

        let value = match field.value_type() {
          Data::string_data => {
            let value = env
              .new_string(field.value_as_string_data().unwrap().data())
              .unwrap();

            let class = env
              .find_class("io/bitdrift/capture/providers/FieldValue$StringField")
              .unwrap();

            let constructor_id = env
              .get_method_id(&class, "<init>", "(Ljava/lang/String;)V")
              .unwrap();

            unsafe {
              env.new_object_unchecked(
                class,
                constructor_id,
                &[JValueWrapper::Object(value.into()).into()],
              )
            }
            .unwrap()
          },
          Data::binary_data => {
            let value = env
              .byte_array_from_slice(field.value_as_binary_data().unwrap().data().bytes())
              .unwrap();

            let class = env
              .find_class("io/bitdrift/capture/providers/FieldValue$BinaryField")
              .unwrap();

            let constructor_id = env
              .get_method_id(&class, "<init>", "(Ljava/lang/Object;)V")
              .unwrap();

            unsafe {
              env.new_object_unchecked(
                class,
                constructor_id,
                &[JValueWrapper::Object(value.into()).into()],
              )
            }
            .unwrap()
          },
          _ => {
            panic!("field with unexpected value_type")
          },
        };

        _ = fields.put(&mut env, &key, &value);

        fields
      },
    );

    let session_id = log
      .session_id()
      .map(|s| env.new_string(s))
      .unwrap()
      .unwrap();

    let timestamp = {
      let timestamp = log.timestamp().unwrap();
      let timestamp = chrono::DateTime::from_timestamp_nanos(
        timestamp.seconds() * 1_000_000_000 + <i32 as Into<i64>>::into(timestamp.nanos()),
      );
      let timestamp =
        chrono::DateTime::to_rfc3339_opts(&timestamp, chrono::SecondsFormat::AutoSi, true);
      env.new_string(timestamp).unwrap()
    };

    let class = env.find_class("io/bitdrift/capture/UploadedLog").unwrap();
    let constructor_id = env
      .get_method_id(
        &class,
        "<init>",
        "(ILjava/lang/String;Ljava/util/Map;Ljava/lang/String;Ljava/lang/String;)V",
      )
      .unwrap();

    unsafe {
      env
        .new_object_unchecked(
          class,
          constructor_id,
          &[
            JValueWrapper::I32(log.log_level().try_into().unwrap()).into(),
            JValueWrapper::Object(message).into(),
            JValueWrapper::Object(fields).into(),
            JValueWrapper::Object(session_id.into()).into(),
            JValueWrapper::Object(timestamp.into()).into(),
          ],
        )
        .unwrap()
    }
  })
}

#[no_mangle]
pub extern "C" fn Java_io_bitdrift_capture_CaptureTestJniLibrary_sendConfigurationUpdate(
  _env: JNIEnv<'_>,
  _class: JClass<'_>,
  stream_id: jint,
) {
  send_configuration_update(stream_id);
}

#[no_mangle]
pub extern "C" fn Java_io_bitdrift_capture_CaptureTestJniLibrary_awaitConfigurationAck(
  _env: JNIEnv<'_>,
  _class: JClass<'_>,
  stream_id: jint,
) {
  await_configuration_ack(stream_id);
}

#[no_mangle]
pub extern "C" fn Java_io_bitdrift_capture_CaptureTestJniLibrary_sendErrorMessage(
  mut env: JNIEnv<'_>,
  _class: JClass<'_>,
  message: JString<'_>,
  error_reporter: JObject<'_>,
) {
  let message: String = env
    .get_string(&message)
    .expect("failed to get java string")
    .into();
  let reporter = new_global!(ErrorReporterHandle, &mut env, error_reporter).unwrap();

  reporter.report(&message, &None, &HashMap::new());
}

#[no_mangle]
pub extern "C" fn Java_io_bitdrift_capture_CaptureTestJniLibrary_runExceptionHandlingTest(
  env: JNIEnv<'_>,
  class: JClass<'_>,
) {
  let handle = ObjectHandle::new(&env, class.into()).unwrap();
  let result = handle.execute(|e, _| Ok(e.find_class("doesntexist").map(|_| ())?));
  assert_matches!(result,
    Err(e) => assert_eq!(e.to_string(), "An unexpected error occurred: failed to execute Java method due to exception: java.lang.NoClassDefFoundError: doesntexist"));
}

#[no_mangle]
pub extern "C" fn Java_io_bitdrift_capture_CaptureTestJniLibrary_runLargeUploadTest(
  _env: JNIEnv<'_>,
  _class: JClass<'_>,
  logger: jlong,
) {
  platform_test_helpers::run_large_upload_test(unsafe { LoggerId::from_raw(logger) });
}

#[no_mangle]
pub extern "C" fn Java_io_bitdrift_capture_CaptureTestJniLibrary_runKeyValueStorageTest(
  mut env: JNIEnv<'_>,
  _class: JClass<'_>,
  preferences: JObject<'_>,
) {
  let storage = new_global!(PreferencesHandle, &mut env, preferences).unwrap();
  platform_test_helpers::run_key_value_storage_tests(&storage);
}

#[no_mangle]
pub extern "C" fn Java_io_bitdrift_capture_CaptureTestJniLibrary_runResourceUtilizationTargetTest(
  mut env: JNIEnv<'_>,
  _class: JClass<'_>,
  target: JObject<'_>,
) {
  let target = new_global!(ResourceUtilizationTargetHandler, &mut env, target).unwrap();
  platform_test_helpers::run_resource_utilization_target_tests(&target);
}

#[no_mangle]
pub extern "C" fn Java_io_bitdrift_capture_CaptureTestJniLibrary_runEventsListenerTargetTest(
  mut env: JNIEnv<'_>,
  _class: JClass<'_>,
  target: JObject<'_>,
) {
  let target = new_global!(EventsListenerTargetHandler, &mut env, target).unwrap();
  platform_test_helpers::run_events_listener_target_tests(&target);
}


#[no_mangle]
pub extern "C" fn Java_io_bitdrift_capture_CaptureTestJniLibrary_disableRuntimeFeature(
  mut env: JNIEnv<'_>,
  _class: JClass<'_>,
  stream_id: jint,
  feature: JString<'_>,
) {
  platform_test_helpers::with_expected_server(|h| {
    h.blocking_stream_action(
      stream_id,
      bd_test_helpers::test_api_server::StreamAction::SendRuntime(
        bd_test_helpers::runtime::make_update(
          vec![(
            env.get_string(&feature).unwrap().to_str().unwrap(),
            ValueKind::Bool(false),
          )],
          "disabled".to_string(),
        ),
      ),
    );

    let (_, ack) = h.blocking_next_runtime_ack();

    assert!(ack.nack.is_none());
  });
}
