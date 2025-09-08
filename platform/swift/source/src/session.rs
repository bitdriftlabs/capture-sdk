// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

use crate::ffi::{self, make_nsstring};
use anyhow::bail;
use bd_session::activity_based::{
  Callbacks as ActivityBasedStrategyCallbacks,
  Strategy as ActivityBasedStrategy,
};
use bd_session::fixed::{Callbacks as FixedStrategyCallbacks, Strategy as FixedStrategy};
use bd_session::Strategy;
use objc::runtime::Object;
use std::sync::Arc;
use time::Duration;

#[allow(clippy::non_send_fields_in_send_ty)]
pub(crate) struct SessionStrategy {
  swift_object: objc::rc::StrongPtr,
}

unsafe impl Sync for SessionStrategy {}
unsafe impl Send for SessionStrategy {}

impl SessionStrategy {
  #[allow(dead_code)]
  pub(crate) fn new(swift_object: *mut Object) -> Self {
    Self {
      swift_object: unsafe { objc::rc::StrongPtr::retain(swift_object) },
    }
  }

  pub(crate) fn create(self, store: Arc<bd_session::Store>) -> anyhow::Result<Arc<Strategy>> {
    Ok(Arc::new(match self.session_strategy_type() {
      0 => Strategy::Fixed(FixedStrategy::new(store, Arc::new(self))),
      1 => {
        let inactivity_threshold_mins = self.inactivity_threshold_mins();
        Strategy::ActivityBased(ActivityBasedStrategy::new(
          Duration::minutes(inactivity_threshold_mins),
          store,
          Arc::new(self),
          Arc::new(bd_time::SystemTimeProvider {}),
        ))
      },
      _ => bail!("Invalid session strategy type"),
    }))
  }
}

impl SessionStrategy {
  fn session_strategy_type(&self) -> i64 {
    // Safety: Since we receive `SessionStrategyCallbacks` as a typed protocol, we know that it
    // responds to `sessionStrategyType` and will return an integer.
    objc::rc::autoreleasepool(|| unsafe { msg_send![*self.swift_object, sessionStrategyType] })
  }

  #[allow(clippy::unused_self)]
  fn inactivity_threshold_mins(&self) -> i64 {
    // Safety: Since we receive `SessionStrategyCallbacks` as a typed protocol, we know that it
    // responds to `inactivityThresholdMins` and will return an integer.
    objc::rc::autoreleasepool(|| unsafe { msg_send![*self.swift_object, inactivityThresholdMins] })
  }
}

impl FixedStrategyCallbacks for SessionStrategy {
  // Safety: Since we receive `SessionStrategyCallbacks` as a typed protocol, we know that it
  // responds to `generateSessionID` and will return an integer.
  fn generate_session_id(&self) -> anyhow::Result<String> {
    objc::rc::autoreleasepool(|| unsafe {
      let session_id: *const Object = msg_send![*self.swift_object, generateSessionID];
      ffi::nsstring_into_string(session_id)
    })
  }
}
impl ActivityBasedStrategyCallbacks for SessionStrategy {
  fn session_id_changed(&self, session_id: &str) {
    // Safety: Since we receive `ActivityBasedStrategyCallbacks` as a typed protocol, we know that
    // it responds to `sessionIdChanged:` and will take a NSString.
    objc::rc::autoreleasepool(|| unsafe {
      let Ok(session_id) = make_nsstring(session_id) else {
        return;
      };
      let () = msg_send![*self.swift_object, sessionIDChanged:*session_id];
    });
  }
}
