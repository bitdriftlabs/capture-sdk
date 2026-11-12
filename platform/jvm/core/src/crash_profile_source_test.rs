// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

#![allow(clippy::unwrap_used)]

use super::Java_io_bitdrift_capture_CaptureJniLibrary_encodeCrashProfileSource as encode;
use bd_error_reporter::reporter::{Reporter, UnexpectedErrorHandler};
use bd_proto::protos::client::artifact::CrashProfileSource;
use jni::objects::{JByteArray, JObject};
use protobuf::Message as _;
use std::borrow::Cow;
use std::collections::HashMap;
use std::sync::Arc;
use std::sync::atomic::{AtomicUsize, Ordering};
use uuid::Uuid;

#[test]
fn jni_byte_array_contains_all_source_facts_and_a_fresh_non_nil_run_identity() {
  crate::test_jvm::with_env(|env| {
    let encode = || -> anyhow::Result<CrashProfileSource> {
      let array = encode(
        unsafe { env.unsafe_clone() },
        JObject::null().into(),
        123,
        7,
        1_000_000_000,
        1_800_000_000_000_000_000,
        2_000_000_000,
      );
      assert!(!array.is_null());
      let bytes = env.convert_byte_array(unsafe { JByteArray::from_raw(array) })?;
      assert!(!env.exception_check()?);
      Ok(CrashProfileSource::parse_from_bytes(&bytes)?)
    };
    let first = encode()?;
    let second = encode()?;
    assert_eq!(first.pid, 123);
    assert_eq!(first.boot_count, 7);
    assert_eq!(first.process_start_elapsed_ns, 1_000_000_000);
    assert_eq!(first.wall_ns, 1_800_000_000_000_000_000);
    assert_eq!(first.elapsed_ns, 2_000_000_000);
    assert!(!Uuid::from_slice(&first.run_id)?.is_nil());
    assert!(!Uuid::from_slice(&second.run_id)?.is_nil());
    assert_ne!(first.run_id, second.run_id);
    Ok(())
  })
  .unwrap();
}

#[test]
fn jni_rejects_negative_unsigned_facts_without_leaving_a_java_exception() {
  struct ExpectedErrors(AtomicUsize);
  impl Reporter for ExpectedErrors {
    fn report(
      &self,
      _message: &str,
      _details: &Option<String>,
      _headers: &HashMap<Cow<'_, str>, Cow<'_, str>>,
    ) {
      self.0.fetch_add(1, Ordering::Relaxed);
    }
  }
  let errors = Arc::new(ExpectedErrors(AtomicUsize::new(0)));
  UnexpectedErrorHandler::with_reporter(errors.clone(), || {
    crate::test_jvm::with_env(|env| {
      for (pid, boot, start, elapsed) in
        [(-1, 7, 1, 2), (1, -1, 1, 2), (1, 7, -1, 2), (1, 7, 1, -1)]
      {
        let result = encode(
          unsafe { env.unsafe_clone() },
          JObject::null().into(),
          pid,
          boot,
          start,
          100,
          elapsed,
        );
        assert!(result.is_null());
        assert!(!env.exception_check()?);
      }
      Ok(())
    })
  })
  .unwrap();
  assert_eq!(errors.0.load(Ordering::Relaxed), 4);
}
