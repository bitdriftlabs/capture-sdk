// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

//! Bridges the Rust core's `ProfilingTarget` to the Kotlin `IProfilingTarget`. Each capture gets a
//! request ID; Kotlin answers through `completeStackSample`, which resolves the stored completion.

use crate::define_object_wrapper;
use crate::jni::{CachedMethod, initialize_class, initialize_method_handle};
use bd_client_common::error::InvariantError;
use bd_error_reporter::reporter::with_handle_unexpected;
use bd_logger::{StackSampleCompletion, StackSampleError, StackSampleOutcome, StackSampleRequest};
use jni::JNIEnv;
use jni::objects::JValue;
use jni::signature::{Primitive, ReturnType};
use jni::sys::jvalue;
use std::collections::HashMap;
use std::path::PathBuf;
use std::sync::{LazyLock, Mutex, OnceLock};

static TARGET_CAPTURE_STACK_SAMPLE: OnceLock<CachedMethod> = OnceLock::new();
static TARGET_CANCEL_STACK_SAMPLE: OnceLock<CachedMethod> = OnceLock::new();
static PENDING_STACK_SAMPLES: LazyLock<Mutex<HashMap<u64, StackSampleCompletion>>> =
  LazyLock::new(Mutex::default);

pub(crate) fn initialize(env: &mut JNIEnv<'_>) -> anyhow::Result<()> {
  let profiling_target =
    initialize_class(env, "io/bitdrift/capture/profiling/IProfilingTarget", None)?;
  initialize_method_handle(
    env,
    &profiling_target.class,
    "captureStackSample",
    "(JJIILjava/lang/String;)V",
    &TARGET_CAPTURE_STACK_SAMPLE,
  )?;
  initialize_method_handle(
    env,
    &profiling_target.class,
    "cancelStackSample",
    "(J)V",
    &TARGET_CANCEL_STACK_SAMPLE,
  )?;
  Ok(())
}

/// Resolves the capture identified by `request_id`. A late or unknown ID is dropped: the core
/// already reported that command's outcome.
pub(crate) fn complete_stack_sample(
  request_id: u64,
  result: Result<StackSampleOutcome, StackSampleError>,
) {
  let completion = PENDING_STACK_SAMPLES
    .lock()
    .ok()
    .and_then(|mut pending| pending.remove(&request_id));
  let Some(completion) = completion else {
    log::debug!("ignoring completion for unknown stack sample {request_id}");
    return;
  };
  completion(result);
}

/// Builds the result Kotlin reported: a path on success, otherwise the profiler's error.
pub(crate) fn stack_sample_result(
  path: Option<String>,
  error_code: i32,
  error_name: Option<String>,
  error_message: Option<String>,
) -> Result<StackSampleOutcome, StackSampleError> {
  match path {
    Some(path) if !path.is_empty() => Ok(StackSampleOutcome {
      path: PathBuf::from(path),
    }),
    _ => Err(StackSampleError {
      code: error_code,
      name: error_name.unwrap_or_else(|| "unknown".to_string()),
      message: error_message
        .unwrap_or_else(|| "the platform did not produce a stack sampling trace".to_string()),
    }),
  }
}

//
// TargetHandler
//

define_object_wrapper!(TargetHandler);

unsafe impl Send for TargetHandler {}
unsafe impl Sync for TargetHandler {}

impl bd_logger::ProfilingTarget for TargetHandler {
  fn capture_stack_sample(&self, request: StackSampleRequest, completion: StackSampleCompletion) {
    let Ok(mut pending) = PENDING_STACK_SAMPLES.lock() else {
      completion(Err(StackSampleError {
        code: 0,
        name: "interrupted".to_string(),
        message: "stack sampling completion state is unavailable".to_string(),
      }));
      return;
    };
    pending.insert(request.request_id, completion);
    drop(pending);

    let result = self.execute(|env, target| {
      let tag = env.new_string(&request.tag)?;
      TARGET_CAPTURE_STACK_SAMPLE
        .get()
        .ok_or(InvariantError::Invariant)?
        .call_method(
          env,
          target,
          ReturnType::Primitive(Primitive::Void),
          &[
            jvalue {
              j: request.request_id.cast_signed(),
            },
            jvalue {
              j: request
                .duration
                .whole_milliseconds()
                .try_into()
                .unwrap_or(i64::MAX),
            },
            jvalue {
              i: request.frequency_hz.cast_signed(),
            },
            jvalue {
              i: request.buffer_size_kb.cast_signed(),
            },
            JValue::Object(&tag).as_jni(),
          ],
        )
        .map(|_| ())
    });
    if let Err(error) = result {
      log::warn!("failed to request a stack sample: {error}");
      complete_stack_sample(
        request.request_id,
        Err(StackSampleError {
          code: 0,
          name: "bridge_failed".to_string(),
          message: error.to_string(),
        }),
      );
    }
  }

  fn cancel_stack_sample(&self, request_id: u64) {
    with_handle_unexpected(
      || {
        self.execute(|env, target| {
          TARGET_CANCEL_STACK_SAMPLE
            .get()
            .ok_or(InvariantError::Invariant)?
            .call_method(
              env,
              target,
              ReturnType::Primitive(Primitive::Void),
              &[jvalue {
                j: request_id.cast_signed(),
              }],
            )
            .map(|_| ())
        })
      },
      "profiling target_handler: cancel stack sample",
    );
  }
}
