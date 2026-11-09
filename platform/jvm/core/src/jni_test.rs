// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

use super::{failed_command_completion_result, initialize_class};
use crate::test_jvm::with_env;
use anyhow::{Result, anyhow};
use bd_logger::{CommandError, CommandResult};

#[test]
fn initialize_class_resolves_standard_java_class() -> Result<()> {
  with_env(|env| -> Result<()> {
    initialize_class(env, "java/util/HashMap", None)?;
    Ok(())
  })
}

#[test]
fn initialize_class_clears_failed_lookup_exception() -> Result<()> {
  with_env(|env| -> Result<()> {
    assert!(initialize_class(env, "example/DoesNotExist", None).is_err());
    assert!(!env.exception_check()?);
    Ok(())
  })
}

#[test]
fn failed_command_completion_clears_pending_jni_exception() -> Result<()> {
  with_env(|env| -> Result<()> {
    env.throw_new(
      "java/lang/IllegalArgumentException",
      "failed to convert command completion",
    )?;

    let error = anyhow!("simulated allocation failure");
    let result = failed_command_completion_result(env, &error);

    assert!(matches!(
      result,
      CommandResult::Failed { error, .. }
        if error == CommandError::HandlerFailed(
          "failed to convert command completion: simulated allocation failure".into()
        )
    ));
    assert!(!env.exception_check()?);
    Ok(())
  })
}
