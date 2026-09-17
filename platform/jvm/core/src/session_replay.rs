// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

use crate::define_object_wrapper;
use crate::jni::{CachedMethod, initialize_class, initialize_method_handle};
use bd_client_common::error::InvariantError;
use bd_error_reporter::reporter::with_handle_unexpected;
use jni::JNIEnv;
use jni::signature::{Primitive, ReturnType};
use jni::sys::{jlong, jvalue};
use std::collections::HashMap;
use std::sync::atomic::{AtomicU64, Ordering};
use std::sync::{LazyLock, Mutex, OnceLock};

static TARGET_CAPTURE_SCREEN: OnceLock<CachedMethod> = OnceLock::new();
static TARGET_CAPTURE_DEVICE_COMMAND_SCREENSHOT: OnceLock<CachedMethod> = OnceLock::new();
static NEXT_DEVICE_COMMAND_SCREENSHOT_REQUEST_ID: AtomicU64 = AtomicU64::new(1);
static DEVICE_COMMAND_SCREENSHOT_COMPLETIONS: LazyLock<
  Mutex<HashMap<u64, bd_logger::DeviceCommandScreenshotCompletion>>,
> = LazyLock::new(Mutex::default);

pub(crate) fn initialize(env: &mut JNIEnv<'_>) -> anyhow::Result<()> {
  let session_replay_target =
    initialize_class(env, "io/bitdrift/capture/ISessionReplayTarget", None)?;
  initialize_method_handle(
    env,
    &session_replay_target.class,
    "captureScreen",
    "()V",
    &TARGET_CAPTURE_SCREEN,
  )?;
  initialize_method_handle(
    env,
    &session_replay_target.class,
    "captureDeviceCommandScreenshot",
    "(J)V",
    &TARGET_CAPTURE_DEVICE_COMMAND_SCREENSHOT,
  )?;
  Ok(())
}

pub(crate) fn complete_device_command_screenshot(request_id: u64, screenshot: Option<Vec<u8>>) {
  let completion = DEVICE_COMMAND_SCREENSHOT_COMPLETIONS
    .lock()
    .ok()
    .and_then(|mut completions| completions.remove(&request_id));
  let Some(completion) = completion else {
    log::debug!("ignoring completion for unknown device command screenshot {request_id}");
    return;
  };

  completion(match screenshot {
    Some(screenshot) if !screenshot.is_empty() => Ok(screenshot),
    _ => Err("platform did not produce a screenshot".to_string()),
  });
}

//
// TargetHandler
//

define_object_wrapper!(TargetHandler);

unsafe impl Send for TargetHandler {}
unsafe impl Sync for TargetHandler {}

impl bd_logger::SessionReplayTarget for TargetHandler {
  fn capture_screen(&self) {
    with_handle_unexpected(
      || {
        self.execute(|e, target| {
          TARGET_CAPTURE_SCREEN
            .get()
            .ok_or(InvariantError::Invariant)?
            .call_method(e, target, ReturnType::Primitive(Primitive::Void), &[])
            .map(|_| ())
        })
      },
      "session replay target_handler: capture screen",
    );
  }

  fn capture_device_command_screenshot(
    &self,
    completion: bd_logger::DeviceCommandScreenshotCompletion,
  ) {
    let request_id = NEXT_DEVICE_COMMAND_SCREENSHOT_REQUEST_ID.fetch_add(1, Ordering::Relaxed);
    let Ok(mut completions) = DEVICE_COMMAND_SCREENSHOT_COMPLETIONS.lock() else {
      completion(Err(
        "remote screenshot completion state is unavailable".to_string(),
      ));
      return;
    };
    completions.insert(request_id, completion);
    drop(completions);

    let result = self.execute(|env, target| {
      TARGET_CAPTURE_DEVICE_COMMAND_SCREENSHOT
        .get()
        .ok_or(InvariantError::Invariant)?
        .call_method(
          env,
          target,
          ReturnType::Primitive(Primitive::Void),
          &[jvalue {
            j: request_id as jlong,
          }],
        )
        .map(|_| ())
    });
    if let Err(error) = result {
      log::warn!("failed to request remote screenshot capture: {error}");
      complete_device_command_screenshot(request_id, None);
    }
  }
}
