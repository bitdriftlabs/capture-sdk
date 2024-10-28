use crate::define_object_wrapper;

//
// TargetHandler
//

define_object_wrapper!(TargetHandler);

unsafe impl Send for TargetHandler {}
unsafe impl Sync for TargetHandler {}

impl bd_logger::SessionReplayTarget for TargetHandler {
  fn capture_screen(&self) {}

  fn capture_screenshot(&self) {}
}
