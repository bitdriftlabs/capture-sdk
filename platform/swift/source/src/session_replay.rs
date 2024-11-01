use objc::rc::autoreleasepool;
use objc::runtime::Object;

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
