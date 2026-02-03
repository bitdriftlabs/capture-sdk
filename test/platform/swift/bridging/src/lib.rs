// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

#[macro_use]
extern crate objc;

use bd_error_reporter::reporter::Reporter;
use bd_logger::DataValue;
use bd_test_helpers::config_helper::make_benchmarking_configuration_with_workflows_update;
use objc::runtime::Object;
use protobuf::Message;
use std::collections::HashMap;
use std::ffi::CStr;
use std::os::raw::c_char;
use swift_bridge::bridge::SwiftErrorReporter;
use swift_bridge::ffi::make_nsstring;
use swift_bridge::key_value_storage::UserDefaultsStorage;

#[path = "./conversion_tests.rs"]
mod conversion_tests;

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

/// Helper for validating the expected output from the end to end test in LoggerTest.swift.
macro_rules! set_string {
  ($log:ident, $msg:ident, $rust_str:expr) => {
    let () = msg_send![$log, $msg: make_nsstring($rust_str).unwrap()];
  };
}

unsafe fn make_nsdata(s: &[u8]) -> *mut Object {
  let data_cls = class!(NSData);

  msg_send![data_cls, dataWithBytes:s.as_ptr() length:s.len()]
}

/// Helper function to populate an UploadedLog object from a server handle.
/// Returns true if a log was received and populated, false on timeout.
#[allow(clippy::cast_possible_wrap)]
unsafe fn populate_uploaded_log_from_server(
  handle: &mut bd_test_helpers::test_api_server::ServerHandle,
  uploaded_log: *mut Object,
) -> bool {
  // If we don't get a log within 5s, return false and end immediately.
  let Some(log_request) = handle.blocking_next_log_upload() else {
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
    let key = make_nsstring(&key).unwrap();

    match value {
      DataValue::String(s) => {
        let value = make_nsstring(&s).unwrap();

        let () = msg_send![uploaded_log, addStringFieldWithKey:key value:value];
      },
      DataValue::SharedString(s) => {
        let value = make_nsstring(&s).unwrap();

        let () = msg_send![uploaded_log, addStringFieldWithKey:key value:value];
      },
      DataValue::StaticString(s) => {
        let value = make_nsstring(s).unwrap();

        let () = msg_send![uploaded_log, addStringFieldWithKey:key value:value];
      },
      DataValue::Bytes(s) => {
        let value = make_nsdata(&s);

        let () = msg_send![uploaded_log, addBinaryFieldWithKey:key value:value];
      },
      DataValue::Boolean(b) => {
        let value = make_nsstring(&b.to_string()).unwrap();

        let () = msg_send![uploaded_log, addStringFieldWithKey:key value:value];
      },
      DataValue::U64(n) => {
        let value = make_nsstring(&n.to_string()).unwrap();

        let () = msg_send![uploaded_log, addStringFieldWithKey:key value:value];
      },
      DataValue::I64(n) => {
        let value = make_nsstring(&n.to_string()).unwrap();

        let () = msg_send![uploaded_log, addStringFieldWithKey:key value:value];
      },
      DataValue::Double(n) => {
        let value = make_nsstring(&n.to_string()).unwrap();

        let () = msg_send![uploaded_log, addStringFieldWithKey:key value:value];
      },
    }
  }

  true
}

/// Waits for up to 5 seconds for the specified server instance to receive
/// a log upload, populating the provided `UploadedLog` object with the log details.
///
/// # Safety
/// The handle must be a valid pointer returned by `create_test_api_server_instance`.
#[no_mangle]
unsafe extern "C" fn server_instance_next_uploaded_log(
  handle: *mut bd_test_helpers::test_api_server::ServerHandle,
  uploaded_log: *mut Object,
) -> bool {
  let handle = unsafe { &mut *handle };
  populate_uploaded_log_from_server(handle, uploaded_log)
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
