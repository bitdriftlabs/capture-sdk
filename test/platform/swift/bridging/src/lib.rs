// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

#[macro_use]
extern crate objc;

use bd_error_reporter::reporter::Reporter;
use bd_logger::StringOrBytes;
use bd_test_helpers::config_helper::make_benchmarking_configuration_with_workflows_update;
use bd_test_helpers::test_api_server::{Event, ExpectedStreamEvent};
use objc::runtime::Object;
use objc_foundation::{INSString, NSString};
use platform_test_helpers::EventCallback;
use protobuf::Message;
use std::collections::HashMap;
use std::ffi::CStr;
use std::os::raw::c_char;
use swift_bridge::bridge::SwiftErrorReporter;
use swift_bridge::ffi::make_nsstring;
use swift_bridge::key_value_storage::UserDefaultsStorage;
use time::Duration;

#[ctor::ctor]
fn setup() {
  bd_test_helpers::test_global_init();
}

#[no_mangle]
extern "C" fn test_null_termination(reporter: *mut Object) {
  let swift_reporter = unsafe { SwiftErrorReporter::new(reporter) };

  let long_string = "aaaaaaaaaaaaa";
  swift_reporter.report(&long_string[.. 1], &None, &HashMap::new());
}

#[no_mangle]
extern "C" fn create_benchmarking_configuration(dir_path: *const c_char) {
  let c_path: &CStr = unsafe { CStr::from_ptr(dir_path) };
  let str_path: &str = c_path.to_str().unwrap();

  let config = make_benchmarking_configuration_with_workflows_update();

  let encoded_config = config.write_to_bytes().unwrap();
  std::fs::write(str_path.to_owned() + "/config.pb", encoded_config).unwrap();
}

/// Wrapper around `ContinuationWrapper`.
struct Continuation {
  object: objc::rc::StrongPtr,
}

impl Continuation {
  // Safety: The caller must ensure that the pointer is a valid pointer to a `ContinationWrapper`
  // Objective-C object.
  unsafe fn new(object: *mut Object) -> Self {
    Self {
      object: objc::rc::StrongPtr::retain(object),
    }
  }
}

impl std::fmt::Debug for Continuation {
  fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result {
    f.debug_struct("Continuation").finish()
  }
}

unsafe impl Sync for Continuation {}
#[allow(clippy::non_send_fields_in_send_ty)]
unsafe impl Send for Continuation {}

impl EventCallback<i32> for Continuation {
  fn triggered(&mut self, value: i32) {
    unsafe { msg_send![*self.object, resumeWithValue: value] }
  }

  fn timeout(&mut self) {
    let message = NSString::from_str("timeout");

    unsafe { msg_send![*self.object, failWithValue: message] }
  }
}

// TODO(snowp): Unclear how we'd model a void continuation so just pretend like everything is a i32
// continuation until we need more types.
impl EventCallback<()> for Continuation {
  fn triggered(&mut self, _value: ()) {
    unsafe { msg_send![*self.object, resumeWithValue: 0] }
  }

  fn timeout(&mut self) {
    let message = NSString::from_str("timeout");

    unsafe { msg_send![*self.object, failWithValue: message] }
  }
}

#[no_mangle]
unsafe extern "C" fn next_test_api_stream(continuation: *mut Object) {
  platform_test_helpers::with_expected_server(|h| {
    h.enqueue_expected_event(
      Event::StreamCreation(Box::new(Continuation::new(continuation))),
      Duration::seconds(5),
    );
  });
}

#[no_mangle]
unsafe extern "C" fn test_stream_received_handshake(stream_id: i32, continuation: *mut Object) {
  platform_test_helpers::with_expected_server(|h| {
    h.enqueue_expected_event(
      Event::StreamEvent(
        stream_id,
        ExpectedStreamEvent::Handshake {
          matcher: None,
          sleep_mode: false,
        },
        Box::new(Continuation::new(continuation)),
      ),
      Duration::seconds(5),
    );
  });
}

#[no_mangle]
unsafe extern "C" fn test_stream_closed(
  stream_id: i32,
  wait_time_ms: u64,
  continuation: *mut Object,
) {
  platform_test_helpers::with_expected_server(|h| {
    h.enqueue_expected_event(
      Event::StreamEvent(
        stream_id,
        ExpectedStreamEvent::Closed,
        Box::new(Continuation::new(continuation)),
      ),
      Duration::milliseconds(wait_time_ms.try_into().unwrap()),
    );
  });
}

/// Helper for validating the expected output from the end to end test in LoggerTest.swift.
macro_rules! set_string {
  ($log:ident, $msg:ident, $rust_str:expr) => {
    let () = msg_send![$log, $msg: make_nsstring($rust_str)];
  };
}

unsafe fn make_nsdata(s: &[u8]) -> *mut Object {
  let data_cls = class!(NSData);

  msg_send![data_cls, dataWithBytes:s.as_ptr() length:s.len()]
}

/// Waits for up to 5 seconds for the active test server to receive a log upload, populating the
/// provided `UploadedLog` object with the log details.
#[no_mangle]
#[allow(clippy::cast_possible_wrap)]
unsafe extern "C" fn next_uploaded_log(uploaded_log: *mut Object) -> bool {
  platform_test_helpers::with_expected_server(|h| {
    // If we don't get a log within 5s, return false and end immediately.
    let Some(log_request) = h.blocking_next_log_upload() else {
      return false;
    };

    // If we use this function without configuring a batch size of 1 we might end up missing log,
    // so fail here.
    assert_eq!(
      log_request.logs().len(),
      1,
      "batch size must be configured to 1"
    );

    let received_log = &log_request.logs()[0];

    let () = msg_send![uploaded_log, setLogLevel:(received_log.log_level())];
    let () = msg_send![uploaded_log, setLogType:(received_log.log_type())];

    set_string!(uploaded_log, setMessage, received_log.message());

    set_string!(uploaded_log, setSessionID, received_log.session_id());

    for (key, value) in received_log.typed_fields() {
      let key = make_nsstring(&key);

      match value {
        StringOrBytes::String(s) => {
          let value = make_nsstring(&s);

          let () = msg_send![uploaded_log, addStringFieldWithKey:key value:value];
        },
        StringOrBytes::SharedString(s) => {
          let value = make_nsstring(&s);

          let () = msg_send![uploaded_log, addStringFieldWithKey:key value:value];
        },
        StringOrBytes::Bytes(s) => {
          let value = make_nsdata(&s);

          let () = msg_send![uploaded_log, addBinaryFieldWithKey:key value:value];
        },
      }
    }

    true
  })
}

#[no_mangle]
unsafe extern "C" fn run_key_value_storage_test() {
  let storage = UserDefaultsStorage::default();
  platform_test_helpers::run_key_value_storage_tests(&storage);
}

#[no_mangle]
unsafe extern "C" fn run_resource_utilization_target_test(target: *mut Object) {
  let target = swift_bridge::resource_utilization::Target::new(target);
  platform_test_helpers::run_resource_utilization_target_tests(&target);
}

#[no_mangle]
unsafe extern "C" fn run_session_replay_target_test(target: *mut Object) {
  let target = swift_bridge::session_replay::Target::new(target);
  platform_test_helpers::run_session_replay_target_tests(&target);
}

#[no_mangle]
unsafe extern "C" fn run_events_listener_target_test(target: *mut Object) {
  let target = swift_bridge::events::Target::new(target);
  platform_test_helpers::run_events_listener_target_tests(&target);
}
