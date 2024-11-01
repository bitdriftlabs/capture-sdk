// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

use objc::rc::autoreleasepool;
use objc::runtime::Object;

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

  fn capture_screenshot(&self) {
    autoreleasepool(|| {
      let () = unsafe { msg_send![*self.swift_object, captureScreenshot] };
    });
  }
}
