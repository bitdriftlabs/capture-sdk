// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

#![allow(clippy::unwrap_used)]

use super::*;
use bd_artifact_upload::UploadSource;
use bd_proto::protos::logging::payload::{ArrayData, BinaryData, MapData};
use bd_time::TimeDurationExt;
use futures_util::poll;
use jni::objects::{JClass, JObject, JObjectArray, JString};
use std::sync::{Arc, Once};
use time::Duration;

fn handler() -> anyhow::Result<JniCommandHandler> {
  crate::test_jvm::with_env(|env| {
    static INITIALIZE: Once = Once::new();
    INITIALIZE.call_once(|| initialize(env).unwrap());
    let dispatcher = env.new_object(
      "io/bitdrift/capture/commands/ICommandDispatcher$Recording",
      "()V",
      &[],
    )?;
    Ok(JniCommandHandler::new(env, dispatcher)?)
  })
}

fn invocation() -> CommandInvocation {
  CommandInvocation {
    command_id: None,
    registered_command_id: "test".into(),
    arguments: HashMap::new(),
    session_id: "session".into(),
  }
}

fn dispatched_id(handler: &JniCommandHandler) -> u64 {
  handler
    .dispatcher
    .execute(|env, object| {
      Ok(
        env
          .get_field(object, "dispatched", "J")?
          .j()?
          .cast_unsigned(),
      )
    })
    .unwrap()
}

fn assert_cancelled(handler: &JniCommandHandler, invocation_id: u64) {
  handler
    .dispatcher
    .execute(|env, object| {
      assert_eq!(
        env
          .get_field(object, "cancelled", "J")?
          .j()?
          .cast_unsigned(),
        invocation_id
      );
      assert_eq!(env.get_field(object, "cancelCount", "I")?.i()?, 1);
      assert!(!env.exception_check()?);
      Ok(())
    })
    .unwrap();
  assert!(!pending_invocations().contains_key(&invocation_id));
}

#[tokio::test]
async fn dropping_execution_removes_sender_and_calls_real_jni_cancel_method() {
  let handler = handler().unwrap();
  let mut execution = handler.execute(invocation());
  assert!(poll!(&mut execution).is_pending());
  let id = dispatched_id(&handler);
  assert!(pending_invocations().contains_key(&id));

  drop(execution);

  assert_cancelled(&handler, id);
  // A late callback is discarded before attempting to convert even malformed JNI arguments.
  crate::test_jvm::with_env(|env| -> anyhow::Result<()> {
    crate::jni::Java_io_bitdrift_capture_CaptureJniLibrary_completeCommand(
      unsafe { env.unsafe_clone() },
      JClass::from(JObject::null()),
      id.cast_signed(),
      JString::from(JObject::null()),
      JString::from(JObject::null()),
      JObjectArray::from(JObject::null()),
      JObject::null(),
      JString::from(JObject::null()),
      JString::from(JObject::null()),
    );
    assert!(!env.exception_check()?);
    Ok(())
  })
  .unwrap();
}

#[tokio::test]
async fn normal_completion_disarms_jni_cancellation() {
  let handler = handler().unwrap();
  let mut execution = handler.execute(invocation());
  assert!(poll!(&mut execution).is_pending());
  let id = dispatched_id(&handler);
  complete_taken_invocation(
    id,
    take_invocation(id).unwrap(),
    completed_result(LogFields::default(), None),
  );

  assert!(matches!(execution.await, CommandResult::Completed { .. }));
  assert!(!pending_invocations().contains_key(&id));
  handler
    .dispatcher
    .execute(|env, object| {
      assert_eq!(env.get_field(object, "cancelCount", "I")?.i()?, 0);
      Ok(())
    })
    .unwrap();
}

#[tokio::test]
async fn cancellation_after_sender_is_taken_still_cancels_platform_and_ignores_late_result() {
  let handler = handler().unwrap();
  let mut execution = handler.execute(invocation());
  assert!(poll!(&mut execution).is_pending());
  let id = dispatched_id(&handler);
  let sender = take_invocation(id).unwrap();

  drop(execution);
  complete_taken_invocation(id, sender, completed_result(LogFields::default(), None));

  assert_cancelled(&handler, id);
}

#[tokio::test]
async fn losing_completion_sender_cancels_platform_instead_of_leaking_work() {
  let handler = handler().unwrap();
  let mut execution = handler.execute(invocation());
  assert!(poll!(&mut execution).is_pending());
  let id = dispatched_id(&handler);

  drop(take_invocation(id).unwrap());

  assert!(matches!(execution.await, CommandResult::Failed { .. }));
  assert_cancelled(&handler, id);
}

#[tokio::test(start_paused = true)]
async fn rust_timeout_calls_jni_cancel_method() {
  let handler = handler().unwrap();
  let mut execution = Box::pin(Duration::seconds(5).timeout(handler.execute(invocation())));
  assert!(poll!(&mut execution).is_pending());
  let id = dispatched_id(&handler);

  tokio::time::advance(Duration::seconds(5).unsigned_abs()).await;
  assert!(execution.await.is_err());

  assert_cancelled(&handler, id);
}

#[tokio::test(start_paused = true)]
async fn ready_completion_at_timeout_boundary_does_not_call_jni_cancel() {
  let handler = handler().unwrap();
  let mut execution = Box::pin(Duration::seconds(5).timeout(handler.execute(invocation())));
  assert!(poll!(&mut execution).is_pending());
  let id = dispatched_id(&handler);
  complete_taken_invocation(
    id,
    take_invocation(id).unwrap(),
    completed_result(LogFields::default(), None),
  );
  tokio::time::advance(Duration::seconds(5).unsigned_abs()).await;

  assert!(matches!(
    execution.await,
    Ok(CommandResult::Completed { .. })
  ));
  handler
    .dispatcher
    .execute(|env, object| {
      assert_eq!(env.get_field(object, "cancelCount", "I")?.i()?, 0);
      Ok(())
    })
    .unwrap();
}

#[tokio::test]
async fn aborting_rust_task_calls_jni_cancel_method() {
  let handler = Arc::new(handler().unwrap());
  let task_handler = handler.clone();
  let (started_tx, started_rx) = oneshot::channel();
  let execution = tokio::spawn(async move {
    let mut execution = task_handler.execute(invocation());
    assert!(poll!(&mut execution).is_pending());
    started_tx.send(dispatched_id(&task_handler)).unwrap();
    execution.await
  });
  let id = started_rx.await.unwrap();

  execution.abort();
  assert!(execution.await.unwrap_err().is_cancelled());

  assert_cancelled(&handler, id);
}

#[tokio::test]
async fn cancellation_only_removes_its_own_pending_invocation() {
  let handler = handler().unwrap();
  let mut first = handler.execute(invocation());
  let mut second = handler.execute(invocation());
  assert!(poll!(&mut first).is_pending());
  let first_id = dispatched_id(&handler);
  assert!(poll!(&mut second).is_pending());
  let second_id = dispatched_id(&handler);

  drop(first);

  assert_cancelled(&handler, first_id);
  complete_taken_invocation(
    second_id,
    take_invocation(second_id).unwrap(),
    completed_result(LogFields::default(), None),
  );
  assert!(matches!(second.await, CommandResult::Completed { .. }));
}

#[tokio::test]
async fn cancellation_jni_exception_does_not_panic_or_retain_pending_sender() {
  let handler = handler().unwrap();
  handler
    .dispatcher
    .execute(|env, object| {
      env.set_field(object, "throwOnCancel", "Z", JValue::Bool(1))?;
      Ok(())
    })
    .unwrap();
  let mut execution = handler.execute(invocation());
  assert!(poll!(&mut execution).is_pending());
  let id = dispatched_id(&handler);

  drop(execution);

  assert_cancelled(&handler, id);
}

#[tokio::test]
async fn dispatch_failure_cleans_up_and_cancels_partially_dispatched_invocation() {
  let handler = handler().unwrap();
  handler
    .dispatcher
    .execute(|env, object| {
      env.set_field(object, "throwOnDispatch", "Z", JValue::Bool(1))?;
      Ok(())
    })
    .unwrap();

  assert!(matches!(
    handler.execute(invocation()).await,
    CommandResult::Failed { .. }
  ));

  assert_cancelled(&handler, dispatched_id(&handler));
}

fn data(data_type: Data_type) -> Data {
  Data {
    data_type: Some(data_type),
    ..Default::default()
  }
}

fn binary(payload: Vec<u8>) -> Data {
  data(Data_type::BinaryData(BinaryData {
    payload,
    ..Default::default()
  }))
}

// Arguments reach the handler with the type and value the backend sent.

#[test]
fn binary_argument_bytes_reach_the_platform_unchanged() {
  // Not valid UTF-8 on purpose: a JPEG header, a NUL and a lone continuation byte.
  let payload = vec![0xFF, 0xD8, 0xFF, 0x00, 0x80];

  let delivered = platform_argument("image", binary(payload.clone())).unwrap();

  assert_eq!(
    delivered,
    PlatformArgument::Binary(payload),
    "binary payload was altered on its way to the handler"
  );
}

#[test]
fn integer_and_string_arguments_stay_distinguishable() {
  // The backend can send `0` as a number or as the text "0"; the handler must be able to tell.
  let number = platform_argument("n", data(Data_type::IntData(0))).unwrap();
  let text = platform_argument("n", data(Data_type::StringData("0".into()))).unwrap();

  assert_ne!(number, text, "numeric argument lost its type");
  assert_eq!(number, PlatformArgument::UnsignedInteger(0));
  assert_eq!(text, PlatformArgument::Text("0".into()));
}

#[test]
fn bool_and_string_arguments_stay_distinguishable() {
  let flag = platform_argument("f", data(Data_type::BoolData(true))).unwrap();
  let text = platform_argument("f", data(Data_type::StringData("true".into()))).unwrap();

  assert_ne!(flag, text, "bool argument lost its type");
  assert_eq!(flag, PlatformArgument::Bool(true));
}

#[test]
fn signed_and_double_arguments_keep_their_exact_values() {
  assert_eq!(
    platform_argument("i", data(Data_type::SintData(i64::MIN))).unwrap(),
    PlatformArgument::SignedInteger(i64::MIN)
  );
  assert_eq!(
    platform_argument("u", data(Data_type::IntData(u64::MAX))).unwrap(),
    PlatformArgument::UnsignedInteger(u64::MAX)
  );
  assert_eq!(
    platform_argument("d", data(Data_type::DoubleData(0.1))).unwrap(),
    PlatformArgument::Decimal(0.1)
  );
}

#[test]
fn nested_arguments_are_rejected_rather_than_silently_emptied() {
  // A nested value must fail the invocation rather than reach the handler as an empty value.
  let map = platform_argument("m", data(Data_type::MapData(MapData::default())));
  let array = platform_argument("a", data(Data_type::ArrayData(ArrayData::default())));
  let empty = platform_argument("e", Data::default());

  assert_eq!(
    map.unwrap_err(),
    "argument m has an unsupported nested value"
  );
  assert_eq!(
    array.unwrap_err(),
    "argument a has an unsupported nested value"
  );
  assert_eq!(empty.unwrap_err(), "argument e has no value");
}

#[test]
fn one_bad_argument_fails_the_whole_invocation_before_dispatch() {
  let arguments: HashMap<String, Data> = [
    ("ok".to_string(), data(Data_type::StringData("fine".into()))),
    (
      "bad".to_string(),
      data(Data_type::MapData(MapData::default())),
    ),
  ]
  .into();

  let error = platform_arguments(arguments).unwrap_err();

  assert_eq!(error, "argument bad has an unsupported nested value");
}

#[test]
fn type_codes_match_the_kotlin_and_ios_contract() {
  assert_eq!(PlatformArgument::Text(String::new()).type_code(), 0);
  assert_eq!(PlatformArgument::Binary(Vec::new()).type_code(), 1);
  assert_eq!(PlatformArgument::UnsignedInteger(0).type_code(), 2);
  assert_eq!(PlatformArgument::Decimal(0.0).type_code(), 3);
  assert_eq!(PlatformArgument::SignedInteger(0).type_code(), 4);
  assert_eq!(PlatformArgument::Bool(false).type_code(), 5);
}

#[test]
fn completed_attachment_preserves_result_context_and_payload() {
  let context: LogFields = [
    ("filename".into(), "caller-name".into()),
    ("platform".into(), "caller-platform".into()),
    ("app_id".into(), "caller-app".into()),
    ("_app_version_code".into(), "caller-build".into()),
    ("custom".into(), "caller-value".into()),
  ]
  .into();
  let result = completed_result(
    context.clone(),
    Some(PlatformAttachment {
      source: PlatformAttachmentSource::Bytes(vec![0xFF, 0xD8]),
      content_type: "image/jpeg".to_string(),
    }),
  );

  let CommandResult::Completed {
    attachment: Some(attachment),
    fields,
  } = result
  else {
    panic!("expected a completed result with an attachment");
  };
  assert_eq!(fields, context);
  assert_eq!(attachment.content_type.as_deref(), Some("image/jpeg"));
  let UploadSource::Bytes(bytes) = attachment.source else {
    panic!("expected attachment bytes");
  };
  assert_eq!(bytes, vec![0xFF, 0xD8]);
}

#[test]
fn completed_result_without_attachment_has_no_attachment_state() {
  let result = completed_result(LogFields::default(), None);

  let CommandResult::Completed { attachment, .. } = result else {
    panic!("expected a completed result");
  };
  assert!(attachment.is_none());
}

#[tokio::test]
async fn conversion_failure_completes_the_pending_invocation() {
  let invocation_id = NEXT_INVOCATION_ID.fetch_add(1, Ordering::Relaxed);
  let (sender, receiver) = oneshot::channel();
  pending_invocations().insert(invocation_id, sender);

  complete_taken_invocation(
    invocation_id,
    take_invocation(invocation_id).unwrap(),
    failed_result(CommandError::HandlerFailed(
      "failed to convert command completion: simulated allocation failure".into(),
    )),
  );

  assert!(matches!(
    receiver.await.unwrap(),
    CommandResult::Failed { error, fields }
      if error == CommandError::HandlerFailed(
        "failed to convert command completion: simulated allocation failure".into(),
      ) && fields.is_empty()
  ));
}

#[test]
fn malformed_jni_completion_completes_and_clears_exception() -> anyhow::Result<()> {
  crate::test_jvm::with_env(|env| -> anyhow::Result<()> {
    let invocation_id = NEXT_INVOCATION_ID.fetch_add(1, Ordering::Relaxed);
    let (sender, mut receiver) = oneshot::channel();
    pending_invocations().insert(invocation_id, sender);

    crate::jni::Java_io_bitdrift_capture_CaptureJniLibrary_completeCommand(
      unsafe { env.unsafe_clone() },
      JClass::from(JObject::null()),
      invocation_id.cast_signed(),
      JString::from(JObject::null()),
      JString::from(JObject::null()),
      JObjectArray::from(JObject::null()),
      JObject::null(),
      JString::from(JObject::null()),
      JString::from(JObject::null()),
    );

    assert!(!env.exception_check()?);
    assert!(matches!(
      receiver.try_recv()?,
      CommandResult::Failed { error, fields }
        if matches!(
          &error,
          CommandError::HandlerFailed(message)
            if message.starts_with("failed to convert command completion:")
        ) && fields.is_empty()
    ));
    Ok(())
  })
}

#[test]
fn late_completion_deletes_the_file_the_platform_handed_over() {
  let directory = tempfile::tempdir().unwrap();
  let path = directory.path().join("trace.perfetto");
  std::fs::write(&path, b"trace").unwrap();
  let (sender, receiver) = oneshot::channel();
  // The core stopped waiting (timeout or cancellation) before the platform reported.
  drop(receiver);

  complete_taken_invocation(
    7,
    sender,
    CommandResult::Completed {
      fields: LogFields::default(),
      attachment: Some(CommandAttachment {
        source: UploadSource::Path(path.clone()),
        content_type: None,
      }),
    },
  );

  assert!(
    !path.exists(),
    "a result nobody uploads must not leave its file behind"
  );
}
