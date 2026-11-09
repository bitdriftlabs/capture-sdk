// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

use super::{ObjectHandle, check_exception};
use crate::test_jvm::with_env;
use anyhow::{Result, anyhow};
use jni::objects::JObject;

#[test]
fn check_exception_returns_message_and_clears_pending_exception() -> Result<()> {
  with_env(|env| -> Result<()> {
    env.throw_new("java/lang/IllegalArgumentException", "invalid input")?;

    let message = check_exception(env)?.ok_or_else(|| anyhow!("exception was not reported"))?;
    assert!(message.contains("IllegalArgumentException: invalid input"));
    assert!(!env.exception_check()?);
    Ok(())
  })
}

#[test]
fn object_handle_executes_on_another_attached_thread() -> Result<()> {
  let object_handle = with_env(|env| -> Result<ObjectHandle> {
    let value = env.new_string("host jvm")?;
    Ok(ObjectHandle::new(env, JObject::from(value))?)
  })?;

  let thread = std::thread::spawn(move || {
    object_handle.execute(|env, object| Ok(env.call_method(object, "length", "()I", &[])?.i()?))
  });
  let length = thread
    .join()
    .map_err(|_| anyhow!("native thread panicked"))??;

  assert_eq!(length, 8);
  Ok(())
}
