// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

use assert_matches::assert_matches;
use bd_error_reporter::reporter::Reporter;
use bd_logger::DataValue;
use bd_test_helpers::runtime::ValueKind;
use bd_test_helpers::test_api_server::{ExpectedStreamEvent, HandshakeMatcher, StreamHandle};
use capture_core::events::ListenerTargetHandler as EventsListenerTargetHandler;
use capture_core::executor::ObjectHandle;
use capture_core::jni::{ErrorReporterHandle, JValueWrapper};
use capture_core::key_value_storage::PreferencesHandle;
use capture_core::new_global;
use capture_core::resource_utilization::TargetHandler as ResourceUtilizationTargetHandler;
use capture_core::session_replay::TargetHandler as SessionReplayTargetHandler;
use jni::objects::{JClass, JMap, JObject, JString};
use jni::sys::{jint, jlong, jobject};
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
use time::format_description::well_known::Rfc3339;
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
    StreamHandle::from_stream_id(stream_id, h).await_event_with_timeout(
      ExpectedStreamEvent::Handshake {
        matcher: Some(HandshakeMatcher {
          attributes: expected_attributes,
          attribute_keys_to_ignore: expected_attribute_keys_to_ignore,
        }),
        sleep_mode: false,
      },
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
  mut env: JNIEnv<'_>,
  _class: JClass<'_>,
  stream_id: jint,
) {
  let result = configure_aggressive_continuous_uploads(stream_id);
  if let Err(e) = result {
    env
      .throw_new("java/lang/AssertionError", e.to_string())
      .expect("failed to throw AssertionError");
  }
}

#[allow(clippy::cast_possible_wrap)]
#[no_mangle]
pub extern "C" fn Java_io_bitdrift_capture_CaptureTestJniLibrary_nextUploadedLog<'a>(
  mut env: JNIEnv<'a>,
  _class: JClass<'_>,
) -> JObject<'a> {
  platform_test_helpers::with_expected_server(|h| {
    let log_request = h.blocking_next_log_upload().expect("expected log upload");
    let log = &log_request.logs()[0];

    let message: JObject<'_> = match log.typed_message() {
      DataValue::String(s) => env.new_string(&s).unwrap().into(),
      DataValue::SharedString(s) => env.new_string(&*s).unwrap().into(),
      DataValue::StaticString(s) => env.new_string(s).unwrap().into(),
      DataValue::Bytes(_)
      | DataValue::Boolean(_)
      | DataValue::U64(_)
      | DataValue::I64(_)
      | DataValue::Double(_)
      | DataValue::Map(_) => JObject::null(),
    };

    // TODO(Augustyniak): Extract the logic below into a helper function.
    let fields = env.new_object("java/util/HashMap", "()V", &[]).unwrap();
    log.typed_fields().iter().fold(
      JMap::from_env(&mut env, &fields).unwrap(),
      |fields, (key, value)| {
        let key = env.new_string(key).unwrap();

        let value = match value {
          DataValue::String(s) => {
            let value = env.new_string(s).unwrap();

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
          DataValue::SharedString(s) => {
            let value = env.new_string(&**s).unwrap();

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
          DataValue::StaticString(s) => {
            let value = env.new_string(s).unwrap();

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
          DataValue::Bytes(b) => {
            let value = env.byte_array_from_slice(b).unwrap();

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
          DataValue::Boolean(b) => {
            let value = env.new_string(b.to_string()).unwrap();

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
          DataValue::U64(n) => {
            let value = env.new_string(n.to_string()).unwrap();

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
          DataValue::I64(n) => {
            let value = env.new_string(n.to_string()).unwrap();

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
          DataValue::Double(n) => {
            let value = env.new_string(n.to_string()).unwrap();

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
          // Map values are serialized as debug strings for test purposes
          DataValue::Map(m) => {
            let value = env.new_string(format!("{m:?}")).unwrap();

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
        };

        _ = fields.put(&mut env, &key, &value);

        fields
      },
    );

    let session_id = env.new_string(log.session_id()).unwrap();

    let timestamp = {
      let timestamp = &log.timestamp().format(&Rfc3339).unwrap();
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
  mut env: JNIEnv<'_>,
  _class: JClass<'_>,
  stream_id: jint,
) {
  let result = await_configuration_ack(stream_id);
  if let Err(e) = result {
    env
      .throw_new("java/lang/AssertionError", e.to_string())
      .expect("failed to throw AssertionError");
  }
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
  Err(e) => {
      assert_eq!(e.to_string(), "An unexpected error occurred: failed to execute Java \
          method due to exception: java.lang.NoClassDefFoundError: doesntexist");
  });
}

#[no_mangle]
pub extern "C" fn Java_io_bitdrift_capture_CaptureTestJniLibrary_runLargeUploadTest(
  mut env: JNIEnv<'_>,
  _class: JClass<'_>,
  logger: jlong,
) {
  let result = platform_test_helpers::run_large_upload_test(unsafe { LoggerId::from_raw(logger) });
  if let Err(e) = result {
    env
      .throw_new("java/lang/AssertionError", e.to_string())
      .expect("failed to throw AssertionError");
  }
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
pub extern "C" fn Java_io_bitdrift_capture_CaptureTestJniLibrary_runSessionReplayTargetTest(
  mut env: JNIEnv<'_>,
  _class: JClass<'_>,
  target: JObject<'_>,
) {
  let target = new_global!(SessionReplayTargetHandler, &mut env, target).unwrap();
  platform_test_helpers::run_session_replay_target_tests(&target);
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
    StreamHandle::from_stream_id(stream_id, h).blocking_stream_action(
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

#[no_mangle]
pub extern "C" fn Java_io_bitdrift_capture_CaptureTestJniLibrary_nextUploadedArtifact(
  mut env: JNIEnv<'_>,
  _class: JClass<'_>,
) -> jobject {
  let artifact_request = platform_test_helpers::with_expected_server(|h| {
    h.blocking_next_artifact_upload()
      .expect("expected artifact upload")
  });

  let contents = artifact_request.contents;
  let feature_flags = artifact_request.feature_flags;
  let session_id = artifact_request.session_id;

  // Create the byte array for contents
  let contents_array = env.byte_array_from_slice(&contents).unwrap();

  // Create a HashMap for feature flags
  let hash_map = env.new_object("java/util/HashMap", "()V", &[]).unwrap();

  // Populate the HashMap with feature flags
  for flag in feature_flags {
    let key_str = env.new_string(&flag.name).unwrap();
    #[allow(clippy::option_if_let_else)]
    let value_obj = if let Some(variant) = &flag.variant {
      env.new_string(variant).unwrap().into()
    } else {
      JObject::null()
    };

    env
      .call_method(
        &hash_map,
        "put",
        "(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;",
        &[(&key_str).into(), (&value_obj).into()],
      )
      .unwrap();
  }

  // Create the session ID string
  let session_id_str = env.new_string(session_id).unwrap();

  // Create the UploadedArtifact object
  let artifact = env
    .new_object(
      "io/bitdrift/capture/UploadedArtifact",
      "([BLjava/util/Map;Ljava/lang/String;)V",
      &[
        (&contents_array).into(),
        (&hash_map).into(),
        (&session_id_str).into(),
      ],
    )
    .unwrap();

  artifact.into_raw()
}
