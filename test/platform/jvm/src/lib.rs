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
use capture_core::resource_utilization::TargetHandler as ResourceUtilizationTargetHandler;
use capture_core::session_replay::TargetHandler as SessionReplayTargetHandler;
use jni::EnvUnowned;
use jni::objects::{JClass, JMap, JObject, JString};
use jni::sys::{jint, jlong, jobject};
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
use time::format_description::well_known::Rfc3339;

// See call site for explanation.
#[unsafe(no_mangle)]
#[allow(clippy::missing_const_for_fn)]
pub extern "C" fn link_hack_for_test() {}

#[unsafe(no_mangle)]
pub extern "C" fn Java_io_bitdrift_capture_CaptureTestJniLibrary_startTestApiServer(
  mut env: EnvUnowned<'_>,
  _class: JClass<'_>,
  ping_interval: jint,
) -> jint {
  env
    .with_env_no_catch(|_| -> jni::errors::Result<_> {
      Ok(start_test_api_server(false, ping_interval))
    })
    .resolve::<jni::errors::LogErrorAndDefault>()
}

#[ctor::ctor(unsafe)]
fn setup() {
  bd_test_helpers_core::test_global_init();
}

#[unsafe(no_mangle)]
pub extern "C" fn Java_io_bitdrift_capture_CaptureTestJniLibrary_stopTestApiServer(
  mut env: EnvUnowned<'_>,
  _class: JClass<'_>,
) {
  env
    .with_env_no_catch(|_| -> jni::errors::Result<()> {
      stop_test_api_server();

      Ok(())
    })
    .resolve::<jni::errors::LogErrorAndDefault>();
}

#[unsafe(no_mangle)]
pub extern "C" fn Java_io_bitdrift_capture_CaptureTestJniLibrary_awaitNextApiStream(
  mut env: EnvUnowned<'_>,
  _class: JClass<'_>,
) -> jint {
  env
    .with_env_no_catch(|_| -> jni::errors::Result<_> { Ok(await_next_api_stream()) })
    .resolve::<jni::errors::LogErrorAndDefault>()
}

#[unsafe(no_mangle)]
pub extern "C" fn Java_io_bitdrift_capture_CaptureTestJniLibrary_awaitApiServerReceivedHandshake(
  mut env: EnvUnowned<'_>,
  _class: JClass<'_>,
  stream_id: jint,
  client_attributes: JObject<'_>,
  client_attribute_keys_to_ignore: JObject<'_>,
) -> bool {
  env
    .with_env_no_catch(|env| -> jni::errors::Result<_> {
      let expected_attributes = if client_attributes.is_null() {
        None
      } else {
        let attributes_map = env.cast_local::<JMap<'_>>(client_attributes).unwrap();

        let mut iterator = attributes_map.iter(env).unwrap();

        let mut rust_attributes = HashMap::new();

        while let Some(entry) = iterator.next(env).unwrap() {
          let key = entry.key(env).unwrap();
          let value = entry.value(env).unwrap();
          rust_attributes.insert(
            env
              .cast_local::<JString<'_>>(key)
              .unwrap()
              .try_to_string(env)
              .unwrap(),
            env
              .cast_local::<JString<'_>>(value)
              .unwrap()
              .try_to_string(env)
              .unwrap(),
          );
        }

        Some(rust_attributes)
      };

      let expected_attribute_keys_to_ignore = if client_attribute_keys_to_ignore.is_null() {
        None
      } else {
        let attributes_list = env
          .cast_local::<jni::objects::JList<'_>>(client_attribute_keys_to_ignore)
          .unwrap();

        let iterator = attributes_list.iter(env).unwrap();

        let mut rust_keys = vec![];

        while let Some(value) = iterator.next(env).unwrap() {
          rust_keys.push(
            env
              .cast_local::<JString<'_>>(value)
              .unwrap()
              .try_to_string(env)
              .unwrap(),
          );
        }

        Some(rust_keys)
      };

      Ok(platform_test_helpers::with_expected_server(|h| {
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
      }))
    })
    .resolve::<jni::errors::LogErrorAndDefault>()
}

#[unsafe(no_mangle)]
pub extern "C" fn Java_io_bitdrift_capture_CaptureTestJniLibrary_awaitApiServerStreamClosed(
  mut env: EnvUnowned<'_>,
  _class: JClass<'_>,
  stream_id: jint,
  wait_time_ms: jint,
) -> bool {
  env
    .with_env_no_catch(|_| -> jni::errors::Result<_> {
      Ok(await_api_server_stream_closed(
        stream_id,
        wait_time_ms.into(),
      ))
    })
    .resolve::<jni::errors::LogErrorAndDefault>()
}

#[unsafe(no_mangle)]
#[rustfmt::skip]
pub extern "C" fn Java_io_bitdrift_capture_CaptureTestJniLibrary_configureAggressiveContinuousUploads(
  mut env: EnvUnowned<'_>,
  _class: JClass<'_>,
  stream_id: jint,
) {
  env
    .with_env_no_catch(|env| -> jni::errors::Result<()> {
      if let Err(e) = configure_aggressive_continuous_uploads(stream_id) {
        env
          .throw_new(
            jni::jni_str!("java/lang/AssertionError"),
            jni::strings::JNIString::new(e.to_string()),
          )
          .expect("failed to throw AssertionError");
      }
      Ok(())
    })
    .resolve::<jni::errors::LogErrorAndDefault>();
}

#[allow(clippy::cast_possible_wrap)]
#[unsafe(no_mangle)]
pub extern "C" fn Java_io_bitdrift_capture_CaptureTestJniLibrary_nextUploadedLog<'a>(
  mut env: EnvUnowned<'a>,
  _class: JClass<'_>,
) -> JObject<'a> {
  env
    .with_env_no_catch(|env| -> jni::errors::Result<_> {
      Ok(platform_test_helpers::with_expected_server(|h| {
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
          | DataValue::Map(_)
          | DataValue::Array(_) => JObject::null(),
        };

        // TODO(Augustyniak): Extract the logic below into a helper function.
        let fields = env
          .new_object(
            jni::jni_str!("java/util/HashMap"),
            jni::jni_sig!("()V"),
            &[],
          )
          .unwrap();
        log.typed_fields().iter().fold(
          env.new_cast_local_ref::<JMap<'_>>(&fields).unwrap(),
          |fields, (key, value)| {
            let key = env.new_string(key).unwrap();

            let value = match value {
              DataValue::String(s) => {
                let value = env.new_string(s).unwrap();

                let class = env
                  .find_class(jni::jni_str!(
                    "io/bitdrift/capture/providers/FieldValue$StringField"
                  ))
                  .unwrap();

                let constructor_id = env
                  .get_method_id(
                    &class,
                    jni::jni_str!("<init>"),
                    jni::jni_sig!("(Ljava/lang/String;)V"),
                  )
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
                  .find_class(jni::jni_str!(
                    "io/bitdrift/capture/providers/FieldValue$StringField"
                  ))
                  .unwrap();

                let constructor_id = env
                  .get_method_id(
                    &class,
                    jni::jni_str!("<init>"),
                    jni::jni_sig!("(Ljava/lang/String;)V"),
                  )
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
                  .find_class(jni::jni_str!(
                    "io/bitdrift/capture/providers/FieldValue$StringField"
                  ))
                  .unwrap();

                let constructor_id = env
                  .get_method_id(
                    &class,
                    jni::jni_str!("<init>"),
                    jni::jni_sig!("(Ljava/lang/String;)V"),
                  )
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
                  .find_class(jni::jni_str!(
                    "io/bitdrift/capture/providers/FieldValue$BinaryField"
                  ))
                  .unwrap();

                let constructor_id = env
                  .get_method_id(
                    &class,
                    jni::jni_str!("<init>"),
                    jni::jni_sig!("(Ljava/lang/Object;)V"),
                  )
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
                  .find_class(jni::jni_str!(
                    "io/bitdrift/capture/providers/FieldValue$StringField"
                  ))
                  .unwrap();

                let constructor_id = env
                  .get_method_id(
                    &class,
                    jni::jni_str!("<init>"),
                    jni::jni_sig!("(Ljava/lang/String;)V"),
                  )
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
                  .find_class(jni::jni_str!(
                    "io/bitdrift/capture/providers/FieldValue$StringField"
                  ))
                  .unwrap();

                let constructor_id = env
                  .get_method_id(
                    &class,
                    jni::jni_str!("<init>"),
                    jni::jni_sig!("(Ljava/lang/String;)V"),
                  )
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
                  .find_class(jni::jni_str!(
                    "io/bitdrift/capture/providers/FieldValue$StringField"
                  ))
                  .unwrap();

                let constructor_id = env
                  .get_method_id(
                    &class,
                    jni::jni_str!("<init>"),
                    jni::jni_sig!("(Ljava/lang/String;)V"),
                  )
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
                  .find_class(jni::jni_str!(
                    "io/bitdrift/capture/providers/FieldValue$StringField"
                  ))
                  .unwrap();

                let constructor_id = env
                  .get_method_id(
                    &class,
                    jni::jni_str!("<init>"),
                    jni::jni_sig!("(Ljava/lang/String;)V"),
                  )
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
              DataValue::Map(_) | DataValue::Array(_) => JObject::null(),
            };

            _ = fields.put(env, &key, &value);

            fields
          },
        );

        let session_id = env.new_string(log.session_id()).unwrap();

        let timestamp = {
          let timestamp = &log.timestamp().format(&Rfc3339).unwrap();
          env.new_string(timestamp).unwrap()
        };

        let class = env
          .find_class(jni::jni_str!("io/bitdrift/capture/UploadedLog"))
          .unwrap();
        let constructor_id = env
          .get_method_id(
            &class,
            jni::jni_str!("<init>"),
            jni::jni_sig!(
              "(ILjava/lang/String;Ljava/util/Map;Ljava/lang/String;Ljava/lang/String;)V"
            ),
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
      }))
    })
    .resolve::<jni::errors::LogErrorAndDefault>()
}

#[unsafe(no_mangle)]
pub extern "C" fn Java_io_bitdrift_capture_CaptureTestJniLibrary_sendConfigurationUpdate(
  mut env: EnvUnowned<'_>,
  _class: JClass<'_>,
  stream_id: jint,
) {
  env
    .with_env_no_catch(|_| -> jni::errors::Result<()> {
      send_configuration_update(stream_id);

      Ok(())
    })
    .resolve::<jni::errors::LogErrorAndDefault>();
}

#[unsafe(no_mangle)]
pub extern "C" fn Java_io_bitdrift_capture_CaptureTestJniLibrary_awaitConfigurationAck(
  mut env: EnvUnowned<'_>,
  _class: JClass<'_>,
  stream_id: jint,
) {
  env
    .with_env_no_catch(|env| -> jni::errors::Result<()> {
      let _: () = {
        let result = await_configuration_ack(stream_id);
        if let Err(e) = result {
          env
            .throw_new(
              jni::jni_str!("java/lang/AssertionError"),
              jni::strings::JNIString::new(e.to_string()),
            )
            .expect("failed to throw AssertionError");
        }
      };
      Ok(())
    })
    .resolve::<jni::errors::LogErrorAndDefault>();
}

#[unsafe(no_mangle)]
pub extern "C" fn Java_io_bitdrift_capture_CaptureTestJniLibrary_sendErrorMessage(
  mut env: EnvUnowned<'_>,
  _class: JClass<'_>,
  message: JString<'_>,
  error_reporter: JObject<'_>,
) {
  env
    .with_env_no_catch(|mut env| -> jni::errors::Result<()> {
      let message: String = message
        .try_to_string(env)
        .expect("failed to get java string");
      let reporter = ErrorReporterHandle::new_global(&env, error_reporter).unwrap();

      reporter.report(&message, &None, &HashMap::new());

      Ok(())
    })
    .resolve::<jni::errors::LogErrorAndDefault>();
}

#[unsafe(no_mangle)]
pub extern "C" fn Java_io_bitdrift_capture_CaptureTestJniLibrary_runExceptionHandlingTest(
  mut env: EnvUnowned<'_>,
  class: JClass<'_>,
) {
  env
    .with_env_no_catch(|env| -> jni::errors::Result<()> {
      let handle = ObjectHandle::new(env, class.into()).unwrap();
      let result =
        handle.execute(|e, _| Ok(e.find_class(jni::jni_str!("doesntexist")).map(|_| ())?));
      assert_matches!(result, Err(_));

      // jni 0.22 reports a missing class directly instead of retaining a pending Java exception.
      // Either representation is valid; ObjectHandle's contract is that it clears any exception
      // before returning to Java.
      assert!(!env.exception_check());

      Ok(())
    })
    .resolve::<jni::errors::LogErrorAndDefault>();
}

#[unsafe(no_mangle)]
pub extern "C" fn Java_io_bitdrift_capture_CaptureTestJniLibrary_runLargeUploadTest(
  mut env: EnvUnowned<'_>,
  _class: JClass<'_>,
  logger: jlong,
) {
  env
    .with_env_no_catch(|env| -> jni::errors::Result<()> {
      let _: () = {
        let result =
          platform_test_helpers::run_large_upload_test(unsafe { LoggerId::from_raw(logger) });
        if let Err(e) = result {
          env
            .throw_new(
              jni::jni_str!("java/lang/AssertionError"),
              jni::strings::JNIString::new(e.to_string()),
            )
            .expect("failed to throw AssertionError");
        }
      };
      Ok(())
    })
    .resolve::<jni::errors::LogErrorAndDefault>();
}

#[unsafe(no_mangle)]
pub extern "C" fn Java_io_bitdrift_capture_CaptureTestJniLibrary_runKeyValueStorageTest(
  mut env: EnvUnowned<'_>,
  _class: JClass<'_>,
  preferences: JObject<'_>,
) {
  env
    .with_env_no_catch(|mut env| -> jni::errors::Result<()> {
      let storage = PreferencesHandle::new_global(&env, preferences).unwrap();
      platform_test_helpers::run_key_value_storage_tests(&storage);

      Ok(())
    })
    .resolve::<jni::errors::LogErrorAndDefault>();
}

#[unsafe(no_mangle)]
pub extern "C" fn Java_io_bitdrift_capture_CaptureTestJniLibrary_runResourceUtilizationTargetTest(
  mut env: EnvUnowned<'_>,
  _class: JClass<'_>,
  target: JObject<'_>,
) {
  env
    .with_env_no_catch(|mut env| -> jni::errors::Result<()> {
      let target = ResourceUtilizationTargetHandler::new_global(&env, target).unwrap();
      platform_test_helpers::run_resource_utilization_target_tests(&target);

      Ok(())
    })
    .resolve::<jni::errors::LogErrorAndDefault>();
}

#[unsafe(no_mangle)]
pub extern "C" fn Java_io_bitdrift_capture_CaptureTestJniLibrary_runSessionReplayTargetTest(
  mut env: EnvUnowned<'_>,
  _class: JClass<'_>,
  target: JObject<'_>,
) {
  env
    .with_env_no_catch(|mut env| -> jni::errors::Result<()> {
      let target = SessionReplayTargetHandler::new_global(&env, target).unwrap();
      platform_test_helpers::run_session_replay_target_tests(&target);

      Ok(())
    })
    .resolve::<jni::errors::LogErrorAndDefault>();
}

#[unsafe(no_mangle)]
pub extern "C" fn Java_io_bitdrift_capture_CaptureTestJniLibrary_runEventsListenerTargetTest(
  mut env: EnvUnowned<'_>,
  _class: JClass<'_>,
  target: JObject<'_>,
) {
  env
    .with_env_no_catch(|mut env| -> jni::errors::Result<()> {
      let target = EventsListenerTargetHandler::new_global(&env, target).unwrap();
      platform_test_helpers::run_events_listener_target_tests(&target);

      Ok(())
    })
    .resolve::<jni::errors::LogErrorAndDefault>();
}

#[unsafe(no_mangle)]
pub extern "C" fn Java_io_bitdrift_capture_CaptureTestJniLibrary_disableRuntimeFeature(
  mut env: EnvUnowned<'_>,
  _class: JClass<'_>,
  stream_id: jint,
  feature: JString<'_>,
) {
  env
    .with_env_no_catch(|env| -> jni::errors::Result<()> {
      platform_test_helpers::with_expected_server(|h| {
        StreamHandle::from_stream_id(stream_id, h).blocking_stream_action(
          bd_test_helpers::test_api_server::StreamAction::SendRuntime(
            bd_test_helpers::runtime::make_update(
              vec![(&feature.try_to_string(env).unwrap(), ValueKind::Bool(false))],
              "disabled".to_string(),
            ),
          ),
        );

        let (_, ack) = h.blocking_next_runtime_ack();

        assert!(ack.nack.is_none());
      });

      Ok(())
    })
    .resolve::<jni::errors::LogErrorAndDefault>();
}

#[unsafe(no_mangle)]
pub extern "C" fn Java_io_bitdrift_capture_CaptureTestJniLibrary_nextUploadedArtifact(
  mut env: EnvUnowned<'_>,
  _class: JClass<'_>,
) -> jobject {
  env
    .with_env_no_catch(|env| -> jni::errors::Result<_> {
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
      let hash_map = env
        .new_object(
          jni::jni_str!("java/util/HashMap"),
          jni::jni_sig!("()V"),
          &[],
        )
        .unwrap();

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
            jni::jni_str!("put"),
            jni::jni_sig!("(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;"),
            &[(&key_str).into(), (&value_obj).into()],
          )
          .unwrap();
      }

      // Create the session ID string
      let session_id_str = env.new_string(session_id).unwrap();

      // Create the UploadedArtifact object
      let artifact = env
        .new_object(
          jni::jni_str!("io/bitdrift/capture/UploadedArtifact"),
          jni::jni_sig!("([BLjava/util/Map;Ljava/lang/String;)V"),
          &[
            (&contents_array).into(),
            (&hash_map).into(),
            (&session_id_str).into(),
          ],
        )
        .unwrap();

      Ok(artifact.into_raw())
    })
    .resolve::<jni::errors::LogErrorAndDefault>()
}
