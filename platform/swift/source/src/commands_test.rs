// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

use super::*;

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

  complete(
    request_id,
    true,
    &HashMap::from([("memory_bytes".to_string(), "42".to_string())]),
    Some(vec![1, 2, 3]),
    Some("application/octet-stream".to_string()),
    Some("memory.bin".to_string()),
    None,
  );

  let Ok(result) = receiver.await else {
    assert!(false, "expected command completion");
    return;
  };
  let CommandResult::Completed { fields, attachment } = result else {
    assert!(false, "expected completed command result");
    return;
  };
  assert_eq!(fields.len(), 1);
  let Some(attachment) = attachment else {
    assert!(false, "expected attachment");
    return;
  };
  assert_eq!(
    attachment.content_type.as_deref(),
    Some("application/octet-stream")
  );
  assert_eq!(attachment.state.len(), 2);
  let UploadSource::Bytes(bytes) = attachment.source else {
    assert!(false, "expected bytes attachment source");
    return;
  };
  assert_eq!(bytes, vec![1, 2, 3]);
}

#[tokio::test]
async fn failed_completion_returns_error_and_context() {
  let request_id = NEXT_REQUEST_ID.fetch_add(1, Ordering::Relaxed);
  let (sender, receiver) = oneshot::channel();
  COMPLETIONS.lock().insert(request_id, sender);

  complete(
    request_id,
    false,
    &HashMap::from([("reason".to_string(), "unsupported".to_string())]),
    None,
    None,
    None,
    Some("Unsupported command".to_string()),
  );

  let Ok(result) = receiver.await else {
    assert!(false, "expected command completion");
    return;
  };
  let CommandResult::Failed { error, fields } = result else {
    assert!(false, "expected failed command result");
    return;
  };
  assert_eq!(error, "Unsupported command");
  assert_eq!(fields.len(), 1);
}
