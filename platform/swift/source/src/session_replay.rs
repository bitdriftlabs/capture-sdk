// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

use objc::rc::autoreleasepool;
use objc::runtime::Object;
use parking_lot::Mutex;
use std::collections::HashMap;
use std::sync::LazyLock;
use std::sync::atomic::{AtomicU64, Ordering};

static NEXT_DEVICE_COMMAND_SCREENSHOT_REQUEST_ID: AtomicU64 = AtomicU64::new(1);
static DEVICE_COMMAND_SCREENSHOT_COMPLETIONS: LazyLock<
  Mutex<HashMap<u64, bd_logger::DeviceCommandScreenshotCompletion>>,
> = LazyLock::new(Mutex::default);

pub fn complete_device_command_screenshot(request_id: u64, screenshot: Option<Vec<u8>>) {
  let completion = DEVICE_COMMAND_SCREENSHOT_COMPLETIONS
    .lock()
    .remove(&request_id);
  let Some(completion) = completion else {
    log::debug!("ignoring completion for unknown device command screenshot {request_id}");
    return;
  };

  completion(match screenshot {
    Some(screenshot) if !screenshot.is_empty() => Ok(screenshot),
    _ => Err("platform did not produce a screenshot".to_string()),
  });
}

#[allow(clippy::non_send_fields_in_send_ty)]
pub struct Target {
  swift_object: objc::rc::StrongPtr,
}

unsafe impl Send for Target {}
unsafe impl Sync for Target {}

impl Target {
  #[allow(clippy::not_unsafe_ptr_arg_deref)]
  pub fn new(swift_object: *mut Object) -> Self {
    Self {
      swift_object: unsafe { objc::rc::StrongPtr::retain(swift_object) },
    }
  }
}

impl bd_logger::SessionReplayTarget for Target {
  fn capture_screen(&self) {
    autoreleasepool(|| {
      let () = unsafe { msg_send![*self.swift_object, captureScreen] };
    });
  }

  fn capture_device_command_screenshot(
    &self,
    completion: bd_logger::DeviceCommandScreenshotCompletion,
  ) {
    let request_id = NEXT_DEVICE_COMMAND_SCREENSHOT_REQUEST_ID.fetch_add(1, Ordering::Relaxed);
    DEVICE_COMMAND_SCREENSHOT_COMPLETIONS
      .lock()
      .insert(request_id, completion);

    autoreleasepool(|| {
      let () = unsafe { msg_send![*self.swift_object, captureDeviceCommandScreenshot: request_id] };
    });
  }
}
