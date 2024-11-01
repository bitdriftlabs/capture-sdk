use crate::define_object_wrapper;
use crate::jni::{initialize_class, initialize_method_handle, CachedMethod};
use jni::signature::{Primitive, ReturnType};
use jni::JNIEnv;
use std::sync::OnceLock;

static TARGET_CAPTURE_SCREEN: OnceLock<CachedMethod> = OnceLock::new();
static TARGET_CAPTURE_SCREENSHOT: OnceLock<CachedMethod> = OnceLock::new();

pub(crate) fn initialize(env: &mut JNIEnv<'_>) {
  let session_replay_target =
    initialize_class(env, "io/bitdrift/capture/ISessionReplayTarget", None);
  initialize_method_handle(
    env,
    &session_replay_target.class,
    "captureScreen",
    "()V",
    &TARGET_CAPTURE_SCREEN,
  );
}

//
// TargetHandler
//

define_object_wrapper!(TargetHandler);

unsafe impl Send for TargetHandler {}
unsafe impl Sync for TargetHandler {}

impl bd_logger::SessionReplayTarget for TargetHandler {
  fn capture_screen(&self) {
    bd_client_common::error::with_handle_unexpected(
      || {
        self.execute(|e, target| {
          TARGET_CAPTURE_SCREEN
            .get()
            .unwrap()
            .call_method(e, target, ReturnType::Primitive(Primitive::Void), &[])
            .map(|_| ())
        })
      },
      "session replay target_handler: capture screen",
    );
  }

  fn capture_screenshot(&self) {
    bd_client_common::error::with_handle_unexpected(
      || {
        self.execute(|e, target| {
          TARGET_CAPTURE_SCREENSHOT
            .get()
            .unwrap()
            .call_method(e, target, ReturnType::Primitive(Primitive::Void), &[])
            .map(|_| ())
        })
      },
      "session replay target_handler: capture screenshot",
    );
  }
}
