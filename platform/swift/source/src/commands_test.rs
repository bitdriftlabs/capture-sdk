// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

#![allow(clippy::assertions_on_constants, clippy::unwrap_used)]

use super::*;
use bd_time::TimeDurationExt;
use objc::declare::ClassDecl;
use objc::runtime::{Class, Sel};
use std::future::poll_fn;
use std::task::Poll;
use time::Duration;

static COMMAND_TARGET_CLASS: LazyLock<&Class> = LazyLock::new(|| {
  let mut class = ClassDecl::new("CaptureCommandCancellationTestTarget", class!(NSObject)).unwrap();
  for name in [
    "requestID",
    "cancelledID",
    "cancelCount",
    "pendingOnCancel",
    "completeImmediately",
  ] {
    class.add_ivar::<u64>(name);
  }
  unsafe {
    class.add_method(
      sel!(executeCommand:arguments:requestID:),
      execute_command as extern "C" fn(&mut Object, Sel, *mut Object, *mut Object, u64),
    );
    class.add_method(
      sel!(cancelCommand:),
      cancel_command as extern "C" fn(&mut Object, Sel, u64),
    );
  }
  class.register()
});

extern "C" fn execute_command(
  target: &mut Object,
  _: Sel,
  _: *mut Object,
  _: *mut Object,
  request_id: u64,
) {
  unsafe {
    target.set_ivar("requestID", request_id);
    if *target.get_ivar::<u64>("completeImmediately") != 0 {
      complete_taken(
        request_id,
        take_completion(request_id).unwrap(),
        result_from_platform_completion(true, &HashMap::new(), None, None, None, None),
      );
    }
  }
}

extern "C" fn cancel_command(target: &mut Object, _: Sel, request_id: u64) {
  unsafe {
    target.set_ivar("cancelledID", request_id);
    target.set_ivar("cancelCount", *target.get_ivar::<u64>("cancelCount") + 1);
    target.set_ivar(
      "pendingOnCancel",
      u64::from(COMPLETIONS.lock().contains_key(&request_id)),
    );
  }
}

fn command_target() -> StrongPtr {
  autoreleasepool(|| unsafe { StrongPtr::new(msg_send![*COMMAND_TARGET_CLASS, new]) })
}

fn command_invocation() -> CommandInvocation {
  CommandInvocation {
    command_id: None,
    registered_command_id: "test".into(),
    arguments: HashMap::new(),
    session_id: "session".into(),
  }
}

#[tokio::test]
async fn dropping_execute_removes_completion_before_cancelling_objective_c_request() {
  let object = command_target();
  let target = Target::new(*object);
  let mut execution = Box::pin(target.execute(command_invocation()));
  poll_fn(|context| {
    assert!(execution.as_mut().poll(context).is_pending());
    Poll::Ready(())
  })
  .await;
  let request_id = unsafe { *(**object).get_ivar::<u64>("requestID") };
  assert!(COMPLETIONS.lock().contains_key(&request_id));

  drop(execution);

  assert!(take_completion(request_id).is_none());
  unsafe {
    assert_eq!(*(**object).get_ivar::<u64>("cancelledID"), request_id);
    assert_eq!(*(**object).get_ivar::<u64>("cancelCount"), 1);
    assert_eq!(*(**object).get_ivar::<u64>("pendingOnCancel"), 0);
  }
}

#[tokio::test]
async fn normal_immediate_completion_disarms_objective_c_cancellation() {
  let object = command_target();
  unsafe { (**object).set_ivar("completeImmediately", 1_u64) };
  let target = Target::new(*object);

  assert!(matches!(
    target.execute(command_invocation()).await,
    CommandResult::Completed { .. }
  ));

  unsafe {
    assert_eq!(*(**object).get_ivar::<u64>("cancelCount"), 0);
    assert!(take_completion(*(**object).get_ivar::<u64>("requestID")).is_none());
  }
}

#[tokio::test]
async fn dropping_execute_cancels_even_when_completion_sender_was_already_taken() {
  let object = command_target();
  let target = Target::new(*object);
  let mut execution = Box::pin(target.execute(command_invocation()));
  poll_fn(|context| {
    assert!(execution.as_mut().poll(context).is_pending());
    Poll::Ready(())
  })
  .await;
  let request_id = unsafe { *(**object).get_ivar::<u64>("requestID") };
  let sender = take_completion(request_id).unwrap();

  drop(execution);
  assert!(
    sender
      .send(result_from_platform_completion(
        true,
        &HashMap::new(),
        None,
        None,
        None,
        None
      ))
      .is_err()
  );
  unsafe { assert_eq!(*(**object).get_ivar::<u64>("cancelCount"), 1) };
}

#[tokio::test]
async fn queued_completion_does_not_disarm_a_dropped_execution() {
  let object = command_target();
  let target = Target::new(*object);
  let mut execution = Box::pin(target.execute(command_invocation()));
  poll_fn(|context| {
    assert!(execution.as_mut().poll(context).is_pending());
    Poll::Ready(())
  })
  .await;
  let request_id = unsafe { *(**object).get_ivar::<u64>("requestID") };
  complete_taken(
    request_id,
    take_completion(request_id).unwrap(),
    result_from_platform_completion(true, &HashMap::new(), None, None, None, None),
  );

  drop(execution);

  assert!(take_completion(request_id).is_none());
  unsafe { assert_eq!(*(**object).get_ivar::<u64>("cancelCount"), 1) };
}

#[tokio::test]
async fn closed_completion_channel_cancels_objective_c_request() {
  let object = command_target();
  let target = Target::new(*object);
  let mut execution = Box::pin(target.execute(command_invocation()));
  poll_fn(|context| {
    assert!(execution.as_mut().poll(context).is_pending());
    Poll::Ready(())
  })
  .await;
  let request_id = unsafe { *(**object).get_ivar::<u64>("requestID") };
  drop(take_completion(request_id));

  assert!(matches!(execution.await, CommandResult::Failed { .. }));
  assert!(take_completion(request_id).is_none());
  unsafe {
    assert_eq!(*(**object).get_ivar::<u64>("cancelledID"), request_id);
    assert_eq!(*(**object).get_ivar::<u64>("cancelCount"), 1);
    assert_eq!(*(**object).get_ivar::<u64>("pendingOnCancel"), 0);
  }
}

#[tokio::test(start_paused = true)]
async fn rust_timeout_cancels_objective_c_request() {
  let object = command_target();
  let target = Target::new(*object);
  let mut execution = Box::pin(Duration::seconds(5).timeout(target.execute(command_invocation())));
  poll_fn(|context| {
    assert!(execution.as_mut().poll(context).is_pending());
    Poll::Ready(())
  })
  .await;
  let request_id = unsafe { *(**object).get_ivar::<u64>("requestID") };

  tokio::time::advance(Duration::seconds(5).unsigned_abs()).await;
  assert!(execution.await.is_err());

  assert!(take_completion(request_id).is_none());
  unsafe {
    assert_eq!(*(**object).get_ivar::<u64>("cancelledID"), request_id);
    assert_eq!(*(**object).get_ivar::<u64>("cancelCount"), 1);
    assert_eq!(*(**object).get_ivar::<u64>("pendingOnCancel"), 0);
  }
}

#[tokio::test(start_paused = true)]
async fn ready_completion_at_timeout_boundary_does_not_cancel_objective_c_request() {
  let object = command_target();
  let target = Target::new(*object);
  let mut execution = Box::pin(Duration::seconds(5).timeout(target.execute(command_invocation())));
  poll_fn(|context| {
    assert!(execution.as_mut().poll(context).is_pending());
    Poll::Ready(())
  })
  .await;
  let request_id = unsafe { *(**object).get_ivar::<u64>("requestID") };
  complete_taken(
    request_id,
    take_completion(request_id).unwrap(),
    result_from_platform_completion(true, &HashMap::new(), None, None, None, None),
  );
  tokio::time::advance(Duration::seconds(5).unsigned_abs()).await;

  assert!(matches!(
    execution.await,
    Ok(CommandResult::Completed { .. })
  ));
  unsafe { assert_eq!(*(**object).get_ivar::<u64>("cancelCount"), 0) };
}

#[tokio::test]
async fn aborting_rust_task_cancels_objective_c_request() {
  let object = command_target();
  let target = Target::new(*object);
  let (started_tx, started_rx) = oneshot::channel();
  let execution = tokio::spawn(async move {
    let mut execution = target.execute(command_invocation());
    poll_fn(|context| {
      assert!(execution.as_mut().poll(context).is_pending());
      Poll::Ready(())
    })
    .await;
    let request_id = unsafe { *(**target.swift_object).get_ivar::<u64>("requestID") };
    started_tx.send(request_id).unwrap();
    execution.await
  });
  let request_id = started_rx.await.unwrap();

  execution.abort();
  assert!(execution.await.unwrap_err().is_cancelled());

  assert!(take_completion(request_id).is_none());
  unsafe {
    assert_eq!(*(**object).get_ivar::<u64>("cancelledID"), request_id);
    assert_eq!(*(**object).get_ivar::<u64>("cancelCount"), 1);
  }
}

#[test]
fn command_argument_dictionary_preserves_name_type_and_value() {
  autoreleasepool(|| {
    let value = make_nsstring("include_stack_traces").unwrap();
    let argument = make_command_argument("argument_name", ARGUMENT_TYPE_STRING, &value).unwrap();

    let name_key = make_nsstring(ARGUMENT_NAME_KEY).unwrap();
    let type_key = make_nsstring(ARGUMENT_TYPE_KEY).unwrap();
    let value_key = make_nsstring(ARGUMENT_VALUE_KEY).unwrap();
    let name: *const Object = unsafe { msg_send![*argument, objectForKey: *name_key] };
    let argument_type: *const Object = unsafe { msg_send![*argument, objectForKey: *type_key] };
    let stored_value: *const Object = unsafe { msg_send![*argument, objectForKey: *value_key] };

    assert_eq!(
      unsafe { nsstring_into_string(name) }.unwrap(),
      "argument_name"
    );
    let stored_argument_type: usize = unsafe { msg_send![argument_type, unsignedIntegerValue] };
    assert_eq!(stored_argument_type, ARGUMENT_TYPE_STRING);
    assert_eq!(
      unsafe { nsstring_into_string(stored_value) }.unwrap(),
      "include_stack_traces"
    );
  });
}

#[tokio::test]
async fn completion_with_attachment_returns_completed_result() {
  let request_id = NEXT_REQUEST_ID.fetch_add(1, Ordering::Relaxed);
  let (sender, receiver) = oneshot::channel();
  COMPLETIONS.lock().insert(request_id, sender);

  let context = HashMap::from([
    ("memory_bytes".to_string(), "42".to_string()),
    ("filename".to_string(), "caller-name".to_string()),
    ("app_id".to_string(), "caller-app".to_string()),
    ("platform".to_string(), "caller-platform".to_string()),
    ("_build_number".to_string(), "caller-build".to_string()),
  ]);
  complete_taken(
    request_id,
    take_completion(request_id).unwrap(),
    result_from_platform_completion(
      true,
      &context,
      Some(vec![1, 2, 3]),
      Some("application/octet-stream".to_string()),
      None,
      None,
    ),
  );

  let Ok(result) = receiver.await else {
    assert!(false, "expected command completion");
    return;
  };
  let CommandResult::Completed { fields, attachment } = result else {
    assert!(false, "expected completed command result");
    return;
  };
  assert_eq!(
    fields
      .into_iter()
      .map(|(key, value)| (key.to_string(), value.as_str().unwrap().to_string()))
      .collect::<HashMap<_, _>>(),
    context
  );
  let Some(attachment) = attachment else {
    assert!(false, "expected attachment");
    return;
  };
  assert_eq!(
    attachment.content_type.as_deref(),
    Some("application/octet-stream")
  );
  let UploadSource::Bytes(bytes) = attachment.source else {
    assert!(false, "expected bytes attachment source");
    return;
  };
  assert_eq!(bytes, vec![1, 2, 3]);
}

#[tokio::test]
async fn failed_completion_with_unknown_code_returns_other_error_and_context() {
  let request_id = NEXT_REQUEST_ID.fetch_add(1, Ordering::Relaxed);
  let (sender, receiver) = oneshot::channel();
  COMPLETIONS.lock().insert(request_id, sender);

  complete_taken(
    request_id,
    take_completion(request_id).unwrap(),
    result_from_platform_completion(
      false,
      &HashMap::from([("reason".to_string(), "unsupported".to_string())]),
      None,
      None,
      Some("unsupported_command"),
      Some("Unsupported command".to_string()),
    ),
  );

  let Ok(result) = receiver.await else {
    assert!(false, "expected command completion");
    return;
  };
  let CommandResult::Failed { error, fields } = result else {
    assert!(false, "expected failed command result");
    return;
  };
  assert_eq!(error, CommandError::Other("Unsupported command".into()));
  assert_eq!(fields.len(), 1);
}

#[tokio::test]
async fn conversion_failure_completes_the_pending_request() {
  let request_id = NEXT_REQUEST_ID.fetch_add(1, Ordering::Relaxed);
  let (sender, receiver) = oneshot::channel();
  COMPLETIONS.lock().insert(request_id, sender);

  complete_taken(
    request_id,
    take_completion(request_id).unwrap(),
    failed_result(
      CommandError::HandlerFailed(
        "failed to convert command completion: simulated allocation failure".into(),
      ),
      &HashMap::new(),
    ),
  );

  assert!(matches!(
    receiver.await.unwrap(),
    CommandResult::Failed { error, fields }
      if error == CommandError::HandlerFailed(
        "failed to convert command completion: simulated allocation failure".into(),
      ) && fields.is_empty()
  ));
}
