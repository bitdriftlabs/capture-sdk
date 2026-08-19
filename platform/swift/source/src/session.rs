// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

#[cfg(test)]
#[path = "./session_test.rs"]
mod tests;

use crate::ffi::{self, make_nsstring};
use anyhow::bail;
use bd_session::configuration::Callbacks;
use bd_session::{Strategy, StrategyWithWorker};
use objc::runtime::Object;
use std::path::Path;
use std::sync::Arc;
use time::Duration;

const MAX_DURATION_SECONDS_EXCLUSIVE: f64 = 9_223_372_036_854_775_808.0;

//
// SessionConfiguration
//

#[allow(clippy::non_send_fields_in_send_ty)]
pub(crate) struct SessionConfiguration {
  swift_object: objc::rc::StrongPtr,
}

unsafe impl Sync for SessionConfiguration {}
unsafe impl Send for SessionConfiguration {}

impl SessionConfiguration {
  pub(crate) fn new(swift_object: *mut Object) -> Self {
    Self {
      swift_object: unsafe { objc::rc::StrongPtr::retain(swift_object) },
    }
  }

  pub(crate) fn create(self, sdk_directory: &Path) -> anyhow::Result<StrategyWithWorker> {
    Ok(Strategy::configuration(
      sdk_directory,
      self.initial_session_id(),
      self.inactivity_timeout()?,
      Arc::new(self),
      Arc::new(bd_time::SystemTimeProvider {}),
    ))
  }

  fn initial_session_id(&self) -> Option<String> {
    objc::rc::autoreleasepool(|| unsafe {
      let session_id: *const Object = msg_send![*self.swift_object, initialSessionID];
      (!session_id.is_null())
        .then(|| ffi::nsstring_into_string(session_id).ok())
        .flatten()
    })
  }

  fn inactivity_timeout(&self) -> anyhow::Result<Option<Duration>> {
    objc::rc::autoreleasepool(|| unsafe {
      let seconds: f64 = msg_send![*self.swift_object, inactivityTimeoutSeconds];
      timeout_from_seconds(seconds)
    })
  }
}

fn timeout_from_seconds(seconds: f64) -> anyhow::Result<Option<Duration>> {
  if seconds.is_finite() && seconds < 0.0 {
    return Ok(None);
  }

  if !seconds.is_finite() || seconds >= MAX_DURATION_SECONDS_EXCLUSIVE {
    bail!("session inactivity timeout must be finite and representable");
  }

  Ok(Some(Duration::seconds_f64(seconds)))
}

impl Callbacks for SessionConfiguration {
  fn session_id_changed(&self, session_id: &str) {
    objc::rc::autoreleasepool(|| unsafe {
      let Ok(session_id) = make_nsstring(session_id) else {
        return;
      };
      let () = msg_send![*self.swift_object, sessionIDChanged:*session_id];
    });
  }
}
