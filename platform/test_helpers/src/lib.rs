// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

use anyhow::bail;
use bd_key_value::Storage;
use bd_logger::{log_level, Block, CaptureSession, LogMessage};
use bd_proto::flatbuffers::buffer_log::bitdrift_public::fbs::logging::v_1::Log;
use bd_proto::protos::client::api::configuration_update::StateOfTheWorld;
use bd_proto::protos::config::v1::config::buffer_config::Type;
use bd_proto::protos::config::v1::config::BufferConfigList;
use bd_proto::protos::logging::payload::LogType;
use bd_runtime::runtime::FeatureFlag;
use bd_test_helpers::config_helper::make_workflow_matcher_matching_everything_except_internal_logs;
use bd_test_helpers::runtime::{make_update, ValueKind};
use bd_test_helpers::test_api_server::ExpectedStreamEvent;
use bd_test_helpers::{config_helper, test_api_server};
use config_helper::{
  configuration_update,
  make_benchmarking_configuration_update,
  make_benchmarking_configuration_with_workflows_update,
  make_buffer_matcher_matching_everything,
  make_buffer_matcher_matching_everything_except_internal_logs,
  make_configuration_update_with_workflow_flushing_buffer,
  BufferConfigBuilder,
};
use platform_shared::LoggerId;
use std::ffi::{c_char, CString};
use std::sync::{LazyLock, Mutex, MutexGuard};
use test_api_server::{
  default_configuration_update,
  start_server,
  ServerHandle,
  StreamAction,
  StreamHandle,
};
pub use test_api_server::{EventCallback, StreamEvent};
use time::Duration;

/// Helper to convert an `anyhow::Result` to a C string for FFI error reporting.
/// Returns null on success, or an error string on failure.
/// The returned pointer must be freed with `test_helpers_free_string`.
fn result_to_c_error(result: anyhow::Result<()>) -> *const c_char {
  match result {
    Ok(()) => std::ptr::null(),
    Err(e) => CString::new(e.to_string())
      .expect("error message should not contain null bytes")
      .into_raw(),
  }
}

/// Frees a string allocated by test helper functions.
///
/// # Safety
/// The pointer must be a valid pointer returned by one of the test helper functions,
/// or null (in which case this is a no-op).
#[no_mangle]
pub unsafe extern "C" fn test_helpers_free_string(ptr: *mut c_char) {
  if !ptr.is_null() {
    drop(unsafe { CString::from_raw(ptr) });
  }
}

// A global handle for managing the test server. Alternatively we could return a
// handle from start_test_api_server, but this seems a bit simpler for basic testing.
static HANDLE: LazyLock<Mutex<Option<Box<ServerHandle>>>> = LazyLock::new(Mutex::default);

fn expected_server_handle() -> MutexGuard<'static, Option<Box<ServerHandle>>> {
  HANDLE.lock().expect("failed to lock mutex")
}

pub fn with_expected_server<F, T>(f: F) -> T
where
  F: FnOnce(&mut ServerHandle) -> T,
{
  let mut l = expected_server_handle();

  f(&mut *l.as_mut().expect("test server not started"))
}

//
// Per-instance server handle functions for test isolation.
// These functions allow each test to have its own isolated server instance,
// avoiding race conditions when tests run in parallel.
//

/// Creates a new test API server instance and returns an opaque handle.
/// The caller is responsible for calling `destroy_test_api_server_instance` to clean up.
#[no_mangle]
pub extern "C" fn create_test_api_server_instance(
  tls: bool,
  ping_interval: i32,
) -> *mut ServerHandle {
  let ping = if ping_interval < 0 {
    None
  } else {
    Some(Duration::milliseconds(ping_interval.into()))
  };
  let server = start_server(tls, ping);
  Box::into_raw(server)
}

/// Returns the port number for a server instance.
///
/// # Safety
/// The handle must be a valid pointer returned by `create_test_api_server_instance`.
#[no_mangle]
pub unsafe extern "C" fn server_instance_port(handle: *mut ServerHandle) -> i32 {
  let handle = unsafe { &*handle };
  handle.port.into()
}

/// Destroys a test API server instance.
///
/// # Safety
/// The handle must be a valid pointer returned by `create_test_api_server_instance`
/// and must not be used after this call.
#[no_mangle]
pub unsafe extern "C" fn destroy_test_api_server_instance(handle: *mut ServerHandle) {
  if !handle.is_null() {
    drop(unsafe { Box::from_raw(handle) });
  }
}

/// Waits for the next API stream on a specific server instance.
/// Returns the stream ID or -1 on timeout.
///
/// # Safety
/// The handle must be a valid pointer returned by `create_test_api_server_instance`.
#[no_mangle]
pub unsafe extern "C" fn server_instance_await_next_stream(handle: *mut ServerHandle) -> i32 {
  let handle = unsafe { &*handle };
  handle.blocking_next_stream().map_or(-1, |s| s.id())
}

fn server_instance_wait_for_handshake_impl(
  handle: &ServerHandle,
  stream_id: i32,
) -> anyhow::Result<()> {
  if !StreamHandle::from_stream_id(stream_id, handle).await_event_with_timeout(
    ExpectedStreamEvent::Created(Some("test!".to_string())),
    Duration::seconds(5),
  ) {
    bail!("Handshake with test API key timed out after 5s (stream_id={stream_id})",);
  }
  Ok(())
}

/// Waits for a handshake with the test API key on a specific server instance.
/// Returns null on success, or an error string on failure.
///
/// # Safety
/// The handle must be a valid pointer returned by `create_test_api_server_instance`.
#[no_mangle]
pub unsafe extern "C" fn server_instance_wait_for_handshake(
  handle: *mut ServerHandle,
  stream_id: i32,
) -> *const c_char {
  let handle = unsafe { &*handle };
  result_to_c_error(server_instance_wait_for_handshake_impl(handle, stream_id))
}

fn server_instance_await_handshake_impl(
  handle: &ServerHandle,
  stream_id: i32,
) -> anyhow::Result<()> {
  if !StreamHandle::from_stream_id(stream_id, handle).await_event_with_timeout(
    ExpectedStreamEvent::Handshake {
      matcher: None,
      sleep_mode: false,
    },
    Duration::seconds(15),
  ) {
    bail!("Handshake timed out after 15s (stream_id={stream_id})");
  }
  Ok(())
}

/// Waits for the server to receive a handshake on a specific stream.
/// Returns null on success, or an error string on failure.
///
/// # Safety
/// The handle must be a valid pointer returned by `create_test_api_server_instance`.
#[no_mangle]
pub unsafe extern "C" fn server_instance_await_handshake(
  handle: *mut ServerHandle,
  stream_id: i32,
) -> *const c_char {
  let handle = unsafe { &*handle };
  result_to_c_error(server_instance_await_handshake_impl(handle, stream_id))
}

/// Waits for a stream to close on a specific server instance.
/// Returns true if the stream closed within the timeout, false otherwise.
///
/// # Safety
/// The handle must be a valid pointer returned by `create_test_api_server_instance`.
#[no_mangle]
pub unsafe extern "C" fn server_instance_await_stream_closed(
  handle: *mut ServerHandle,
  stream_id: i32,
  wait_time_ms: i64,
) -> bool {
  let handle = unsafe { &*handle };
  StreamHandle::from_stream_id(stream_id, handle).await_event_with_timeout(
    ExpectedStreamEvent::Closed,
    Duration::milliseconds(wait_time_ms),
  )
}

/// # Safety
/// Handle must be valid.
#[no_mangle]
pub unsafe extern "C" fn server_instance_send_configuration(
  handle: *mut ServerHandle,
  stream_id: i32,
) {
  let handle = unsafe { &*handle };
  StreamHandle::from_stream_id(stream_id, handle).blocking_stream_action(
    StreamAction::SendConfiguration(default_configuration_update()),
  );
}

fn server_instance_await_configuration_ack_impl(
  handle: &mut ServerHandle,
  stream_id: i32,
) -> anyhow::Result<()> {
  let (updated_stream_id, _) = handle.blocking_next_configuration_ack();
  if stream_id != updated_stream_id {
    bail!("Configuration ack stream_id mismatch: expected {stream_id}, got {updated_stream_id}",);
  }
  Ok(())
}

/// Returns null on success, or an error string on failure.
///
/// # Safety
/// Handle must be valid.
#[no_mangle]
pub unsafe extern "C" fn server_instance_await_configuration_ack(
  handle: *mut ServerHandle,
  stream_id: i32,
) -> *const c_char {
  let handle = unsafe { &mut *handle };
  result_to_c_error(server_instance_await_configuration_ack_impl(
    handle, stream_id,
  ))
}

pub fn server_instance_configure_aggressive_uploads_impl(
  handle: &mut ServerHandle,
  stream_id: i32,
) -> anyhow::Result<()> {
  let stream = StreamHandle::from_stream_id(stream_id, handle);

  if !stream.await_event_with_timeout(
    ExpectedStreamEvent::Handshake {
      matcher: None,
      sleep_mode: false,
    },
    Duration::milliseconds(2000),
  ) {
    bail!("Handshake timed out after 2s (stream_id={stream_id})");
  }

  stream.blocking_stream_action(StreamAction::SendRuntime(make_update(
    vec![
      (
        bd_runtime::runtime::platform_events::ListenerEnabledFlag::path(),
        ValueKind::Bool(true),
      ),
      (
        bd_runtime::runtime::log_upload::BatchSizeFlag::path(),
        ValueKind::Int(1),
      ),
      (
        bd_runtime::runtime::log_upload::RetryBackoffMaxFlag::path(),
        ValueKind::Int(1),
      ),
      (
        bd_runtime::runtime::log_upload::RetryBackoffInitialFlag::path(),
        ValueKind::Int(1),
      ),
      (
        bd_runtime::runtime::api::InitialBackoffInterval::path(),
        ValueKind::Int(1),
      ),
      (
        bd_runtime::runtime::api::MaxBackoffInterval::path(),
        ValueKind::Int(10),
      ),
      (
        bd_runtime::runtime::debugging::InternalLoggingFlag::path(),
        ValueKind::Bool(true),
      ),
      (
        bd_runtime::runtime::resource_utilization::ResourceUtilizationEnabledFlag::path(),
        ValueKind::Bool(true),
      ),
      (
        bd_runtime::runtime::session_replay::PeriodicScreensEnabledFlag::path(),
        ValueKind::Bool(true),
      ),
      (
        bd_runtime::runtime::crash_reporting::Enabled::path(),
        ValueKind::Bool(true),
      ),
      (
        bd_runtime::runtime::state::UsePersistentStorage::path(),
        ValueKind::Bool(true),
      ),
    ],
    "base".to_string(),
  )));

  handle.blocking_next_runtime_ack();

  let configuration_update = make_configuration_update_with_workflow_flushing_buffer(
    "default",
    Type::CONTINUOUS,
    make_buffer_matcher_matching_everything_except_internal_logs(),
    make_workflow_matcher_matching_everything_except_internal_logs(),
  );

  stream.blocking_stream_action(StreamAction::SendConfiguration(configuration_update));

  handle.blocking_next_configuration_ack();
  Ok(())
}

/// Returns null on success, or an error string on failure.
///
/// # Safety
/// Handle must be valid.
#[no_mangle]
pub unsafe extern "C" fn server_instance_configure_aggressive_uploads(
  handle: *mut ServerHandle,
  stream_id: i32,
) -> *const c_char {
  let handle = unsafe { &mut *handle };
  result_to_c_error(server_instance_configure_aggressive_uploads_impl(
    handle, stream_id,
  ))
}

pub fn run_large_upload_test_impl(
  handle: &mut ServerHandle,
  logger_id: LoggerId<'_>,
) -> anyhow::Result<()> {
  let Some(stream) = handle.blocking_next_stream() else {
    bail!("Failed to get initial stream - client never connected");
  };

  if !stream.await_event_with_timeout(
    ExpectedStreamEvent::Handshake {
      matcher: None,
      sleep_mode: false,
    },
    Duration::milliseconds(800),
  ) {
    bail!(
      "Initial handshake timed out after 800ms (stream_id={})",
      stream.id()
    );
  }

  stream.blocking_stream_action(StreamAction::SendRuntime(make_update(
    vec![(
      bd_runtime::runtime::log_upload::BatchSizeBytesFlag::path(),
      ValueKind::Int(1024 * 1024 * 2),
    )],
    "base".to_string(),
  )));

  handle.blocking_next_runtime_ack();

  let extra_large_buffer_uploading_everything = configuration_update(
    "version",
    StateOfTheWorld {
      buffer_config_list: Some(BufferConfigList {
        buffer_config: vec![BufferConfigBuilder {
          name: "big buffer",
          buffer_type: Type::CONTINUOUS,
          filter: make_buffer_matcher_matching_everything().into(),
          non_volatile_size: 10_100_000,
          volatile_size: 10_000_000,
        }
        .build()],
        ..Default::default()
      })
      .into(),
      ..Default::default()
    },
  );

  stream.blocking_stream_action(StreamAction::SendConfiguration(
    extra_large_buffer_uploading_everything,
  ));

  handle.blocking_next_configuration_ack();

  for _ in 0 .. 22 {
    logger_id.log(
      log_level::DEBUG,
      LogType::NORMAL,
      LogMessage::Bytes(vec![0; 100_000].into()),
      [].into(),
      [].into(),
      None,
      Block::Yes(std::time::Duration::from_secs(1)),
      &CaptureSession::default(),
    );
  }

  let Some(log_upload) = handle.blocking_next_log_upload() else {
    bail!("Failed to receive log upload - no upload received after logging 22 large messages");
  };

  let log_count = log_upload.logs().len();
  if log_count != 21 {
    bail!("Expected 21 logs in upload, got {log_count}");
  }

  Ok(())
}

/// Returns null on success, or an error string on failure.
///
/// # Safety
/// Handle must be valid.
#[no_mangle]
pub unsafe extern "C" fn server_instance_run_large_upload_test(
  handle: *mut ServerHandle,
  logger_id: LoggerId<'_>,
) -> *const c_char {
  let handle = unsafe { &mut *handle };
  result_to_c_error(run_large_upload_test_impl(handle, logger_id))
}

#[no_mangle]
pub extern "C" fn start_test_api_server(tls: bool, ping_interval: i32) -> i32 {
  let ping = if ping_interval < 0 {
    None
  } else {
    Some(Duration::milliseconds(ping_interval.into()))
  };
  let server = start_server(tls, ping);
  let port = server.port;
  *expected_server_handle() = Some(server);

  port.into()
}

#[no_mangle]
pub extern "C" fn stop_test_api_server() {
  let mut l = expected_server_handle();
  *l = None;
}

#[no_mangle]
pub extern "C" fn await_next_api_stream() -> i32 {
  with_expected_server(|h| unsafe { server_instance_await_next_stream(h) })
}

#[no_mangle]
pub extern "C" fn wait_for_stream_with_test_api_key(stream_id: i32) {
  with_expected_server(|h| unsafe { server_instance_wait_for_handshake(h, stream_id) });
}

#[no_mangle]
pub extern "C" fn await_api_server_received_handshake(stream_id: i32) {
  with_expected_server(|h| unsafe { server_instance_await_handshake(h, stream_id) });
}

#[no_mangle]
pub extern "C" fn await_api_server_stream_closed(stream_id: i32, wait_time_ms: i64) -> bool {
  with_expected_server(|h| unsafe {
    server_instance_await_stream_closed(h, stream_id, wait_time_ms)
  })
}

#[no_mangle]
pub extern "C" fn send_configuration_update(stream_id: i32) {
  with_expected_server(|h| unsafe { server_instance_send_configuration(h, stream_id) });
}

#[no_mangle]
pub extern "C" fn configure_benchmarking_configuration(stream_id: i32) {
  with_expected_server(|h| {
    let stream = StreamHandle::from_stream_id(stream_id, h);
    stream.blocking_stream_action(StreamAction::SendConfiguration(
      make_benchmarking_configuration_update(),
    ));
    perform_benchmarking_runtime_update(&stream);
  });
}

#[no_mangle]
pub extern "C" fn configure_benchmarking_configuration_with_workflows(stream_id: i32) {
  with_expected_server(|h| {
    let stream = StreamHandle::from_stream_id(stream_id, h);
    stream.blocking_stream_action(StreamAction::SendConfiguration(
      make_benchmarking_configuration_with_workflows_update(),
    ));
    perform_benchmarking_runtime_update(&stream);
  });
}

fn perform_benchmarking_runtime_update(stream: &StreamHandle) {
  stream.blocking_stream_action(StreamAction::SendRuntime(make_update(
    vec![
      // Enable platform events listener.
      (
        bd_runtime::runtime::platform_events::ListenerEnabledFlag::path(),
        ValueKind::Bool(true),
      ),
      // Stats.
      (
        bd_runtime::runtime::stats::DirectStatFlushIntervalFlag::path(),
        ValueKind::Int(30),
      ),
      // Enable internal logs.
      (
        bd_runtime::runtime::debugging::InternalLoggingFlag::path(),
        ValueKind::Bool(true),
      ),
      (
        bd_runtime::runtime::log_upload::BatchSizeFlag::path(),
        ValueKind::Int(100),
      ),
      // Enable resource utilization logs.
      (
        bd_runtime::runtime::resource_utilization::ResourceUtilizationEnabledFlag::path(),
        ValueKind::Bool(true),
      ),
    ],
    "base".to_string(),
  )));
}

pub fn await_configuration_ack(stream_id: i32) -> anyhow::Result<()> {
  with_expected_server(|h| server_instance_await_configuration_ack_impl(h, stream_id))
}

pub fn configure_aggressive_continuous_uploads(stream_id: i32) -> anyhow::Result<()> {
  with_expected_server(|h| server_instance_configure_aggressive_uploads_impl(h, stream_id))
}

pub fn run_large_upload_test(logger_id: LoggerId<'_>) -> anyhow::Result<()> {
  with_expected_server(|h| run_large_upload_test_impl(h, logger_id))
}

#[must_use]
pub fn required_field<'a>(log: &'a Log<'_>, key: &str) -> &'a str {
  log
    .fields()
    .unwrap()
    .iter()
    .find(|field| field.key() == key)
    .unwrap_or_else(|| panic!("expected field for {key}"))
    .value_as_string_data()
    .unwrap()
    .data()
}

pub fn run_key_value_storage_tests(storage: &dyn Storage) {
  assert!(storage.get_string("key").unwrap().is_none());

  storage.set_string("key", "foo").unwrap();
  assert_eq!(Some("foo".to_string()), storage.get_string("key").unwrap());

  storage.delete("key").unwrap();
  assert!(storage.get_string("key").unwrap().is_none());
}

pub fn run_resource_utilization_target_tests(target: &dyn bd_logger::ResourceUtilizationTarget) {
  target.tick();
}

pub fn run_session_replay_target_tests(target: &dyn bd_logger::SessionReplayTarget) {
  target.capture_screen();
  target.capture_screenshot();
}

pub fn run_events_listener_target_tests(target: &dyn bd_logger::EventsListenerTarget) {
  target.start();
  target.stop();
}
