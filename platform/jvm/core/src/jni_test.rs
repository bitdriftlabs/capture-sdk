// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

use super::{initialize_class, native_timestamp};
use crate::test_jvm::with_env;
use anyhow::Result;

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
fn native_timestamp_has_millisecond_precision() -> Result<()> {
  assert_eq!(native_timestamp()?.nanosecond() % 1_000_000, 0);
  Ok(())
}
