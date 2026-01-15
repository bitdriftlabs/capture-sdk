// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

use assert_matches::assert_matches;
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


#[no_mangle]
pub extern "C" fn create_test_api_server_instance(tls: bool, ping_interval: i32) -> *mut ServerHandle
{
  let ping = if ping_interval < 0 {
    None
  } else {
    Some(Duration::milliseconds(ping_interval.into()))
  };
  let server = start_server(tls, ping);
  Box::into_raw(server)
}

/// # Safety
/// Handle must be valid pointer from `create_test_api_server_instance`.
#[no_mangle]
pub unsafe extern "C" fn server_instance_port(handle: *mut ServerHandle) -> i32 {
  let handle = unsafe { &*handle };
  handle.port.into()
}

/// # Safety
/// Handle must be valid and not used after this call.
#[no_mangle]
pub unsafe extern "C" fn destroy_test_api_server_instance(handle: *mut ServerHandle) {
  if !handle.is_null() {
    drop(unsafe { Box::from_raw(handle) });
  }
}

/// # Safety
/// Handle must be valid.
#[no_mangle]
pub unsafe extern "C" fn server_instance_await_next_stream(handle: *mut ServerHandle) -> i32 {
  let handle = unsafe { &*handle };
  handle.blocking_next_stream().map_or(-1, |s| s.id())
}

/// # Safety
/// Handle must be valid.
#[no_mangle]
pub unsafe extern "C" fn server_instance_wait_for_handshake(
  handle: *mut ServerHandle,
  stream_id: i32,
) {
  let handle = unsafe { &*handle };
  assert!(
    StreamHandle::from_stream_id(stream_id, handle).await_event_with_timeout(
      ExpectedStreamEvent::Created(Some("test!".to_string())),
      Duration::seconds(5)
    )
  );
}

/// # Safety
/// Handle must be valid.
#[no_mangle]
pub unsafe extern "C" fn server_instance_await_handshake(
  handle: *mut ServerHandle,
  stream_id: i32,
) {
  let handle = unsafe { &*handle };
  assert!(
    StreamHandle::from_stream_id(stream_id, handle).await_event_with_timeout(
      ExpectedStreamEvent::Handshake {
        matcher: None,
        sleep_mode: false,
      },
      Duration::seconds(15)
    )
  );
}

/// # Safety
/// Handle must be valid.
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

/// # Safety
/// Handle must be valid.
#[no_mangle]
pub unsafe extern "C" fn server_instance_await_configuration_ack(
  handle: *mut ServerHandle,
  stream_id: i32,
) {
  let handle = unsafe { &mut *handle };
  let (updated_stream_id, _) = handle.blocking_next_configuration_ack();
  assert_eq!(stream_id, updated_stream_id);
}

/// # Safety
/// Handle must be valid.
#[no_mangle]
pub unsafe extern "C" fn server_instance_configure_aggressive_uploads(
  handle: *mut ServerHandle,
  stream_id: i32,
) {
  let handle = unsafe { &mut *handle };
  let stream = StreamHandle::from_stream_id(stream_id, handle);

  assert!(stream.await_event_with_timeout(
    ExpectedStreamEvent::Handshake {
      matcher: None,
      sleep_mode: false,
    },
    Duration::milliseconds(2000),
  ));

  StreamHandle::from_stream_id(stream_id, handle).blocking_stream_action(StreamAction::SendRuntime(
    make_update(
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
    ),
  ));

  handle.blocking_next_runtime_ack();

  let configuration_update = make_configuration_update_with_workflow_flushing_buffer(
    "default",
    Type::CONTINUOUS,
    make_buffer_matcher_matching_everything_except_internal_logs(),
    make_workflow_matcher_matching_everything_except_internal_logs(),
  );

  StreamHandle::from_stream_id(stream_id, handle)
    .blocking_stream_action(StreamAction::SendConfiguration(configuration_update));

  handle.blocking_next_configuration_ack();
}

/// # Safety
/// Handle must be valid.
#[no_mangle]
pub unsafe extern "C" fn server_instance_run_aggressive_upload_test(
  handle: *mut ServerHandle,
  logger_id: LoggerId<'_>,
) {
  let handle = unsafe { &mut *handle };
  let stream_id = handle.blocking_next_stream().map_or(-1, |s| s.id());
  assert_ne!(stream_id, -1);

  let stream = StreamHandle::from_stream_id(stream_id, handle);

  assert!(stream.await_event_with_timeout(
    ExpectedStreamEvent::Handshake {
      matcher: None,
      sleep_mode: false,
    },
    Duration::milliseconds(2000),
  ));

  StreamHandle::from_stream_id(stream_id, handle).blocking_stream_action(StreamAction::SendRuntime(
    make_update(
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
    ),
  ));

  handle.blocking_next_runtime_ack();

  let configuration_update = make_configuration_update_with_workflow_flushing_buffer(
    "default",
    Type::CONTINUOUS,
    make_buffer_matcher_matching_everything_except_internal_logs(),
    make_workflow_matcher_matching_everything_except_internal_logs(),
  );

  StreamHandle::from_stream_id(stream_id, handle)
    .blocking_stream_action(StreamAction::SendConfiguration(configuration_update));

  handle.blocking_next_configuration_ack();

  for _ in 0 .. 100 {
    logger_id.log(
      log_level::DEBUG,
      LogType::NORMAL,
      "hello".into(),
      [].into(),
      [].into(),
      None,
      bd_logger::Block::No,
      &CaptureSession::default(),
    );
  }

  for _ in 0 .. 10 {
    let upload = handle
      .blocking_next_log_upload()
      .expect("logs should be uploaded");

    assert_eq!(upload.logs().len(), 1);
  }
}

/// # Safety
/// Handle must be valid.
#[no_mangle]
pub unsafe extern "C" fn server_instance_run_large_upload_test(
  handle: *mut ServerHandle,
  logger_id: LoggerId<'_>,
) -> bool {
  let handle = unsafe { &mut *handle };
  let stream_id = handle.blocking_next_stream().map_or(-1, |s| s.id());
  if stream_id == -1 {
    return false;
  }

  if !StreamHandle::from_stream_id(stream_id, handle).await_event_with_timeout(
    ExpectedStreamEvent::Handshake {
      matcher: None,
      sleep_mode: false,
    },
    Duration::milliseconds(800),
  ) {
    return false;
  }

  StreamHandle::from_stream_id(stream_id, handle).blocking_stream_action(StreamAction::SendRuntime(
    make_update(
      vec![(
        bd_runtime::runtime::log_upload::BatchSizeBytesFlag::path(),
        ValueKind::Int(1024 * 1024 * 2),
      )],
      "base".to_string(),
    ),
  ));

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

  StreamHandle::from_stream_id(stream_id, handle).blocking_stream_action(
    StreamAction::SendConfiguration(extra_large_buffer_uploading_everything),
  );

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

  assert_matches!(handle.blocking_next_log_upload(), Some(log_upload) => {
      assert_eq!(log_upload.logs().len(), 21);
  });

  true
}

/// # Safety
/// Handle must be valid.
#[no_mangle]
pub unsafe extern "C" fn server_instance_run_aggressive_upload_with_stream_drops(
  handle: *mut ServerHandle,
  logger_id: LoggerId<'_>,
) -> bool {
  let handle = unsafe { &mut *handle };
  let stream_id = handle.blocking_next_stream().map_or(-1, |s| s.id());
  if stream_id == -1 {
    return false;
  }

  let stream = StreamHandle::from_stream_id(stream_id, handle);

  if !stream.await_event_with_timeout(
    ExpectedStreamEvent::Handshake {
      matcher: None,
      sleep_mode: false,
    },
    Duration::milliseconds(2000),
  ) {
    return false;
  }

  StreamHandle::from_stream_id(stream_id, handle).blocking_stream_action(StreamAction::SendRuntime(
    make_update(
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
    ),
  ));

  handle.blocking_next_runtime_ack();

  let configuration_update = make_configuration_update_with_workflow_flushing_buffer(
    "default",
    Type::CONTINUOUS,
    make_buffer_matcher_matching_everything_except_internal_logs(),
    make_workflow_matcher_matching_everything_except_internal_logs(),
  );

  StreamHandle::from_stream_id(stream_id, handle)
    .blocking_stream_action(StreamAction::SendConfiguration(configuration_update));

  handle.blocking_next_configuration_ack();

  let mut stream = StreamHandle::from_stream_id(stream_id, handle);

  for _ in 0 .. 5 {
    for _ in 0 .. 10 {
      logger_id.log(
        log_level::TRACE,
        LogType::NORMAL,
        "hello".into(),
        [].into(),
        [].into(),
        None,
        bd_logger::Block::No,
        &CaptureSession::default(),
      );
    }

    stream.blocking_stream_action(StreamAction::CloseStream);

    let Some(new_stream) = handle.blocking_next_stream() else {
      return false;
    };
    stream = new_stream;

    if !stream.await_event_with_timeout(
      ExpectedStreamEvent::Handshake {
        matcher: None,
        sleep_mode: false,
      },
      Duration::seconds(10),
    ) {
      return false;
    }
  }

  handle.blocking_next_log_upload().is_some()
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
  with_expected_server(|h| h.blocking_next_stream().map_or(-1, |s| s.id()))
}

#[no_mangle]
pub extern "C" fn wait_for_stream_with_test_api_key(stream_id: i32) {
  with_expected_server(|h| {
    // TODO(snowp): Support passing the expected API key for test vs testing for a specific test
    // one.
    assert!(
      StreamHandle::from_stream_id(stream_id, h).await_event_with_timeout(
        ExpectedStreamEvent::Created(Some("test!".to_string())),
        Duration::seconds(5)
      )
    );
  });
}

#[no_mangle]
pub extern "C" fn await_api_server_received_handshake(stream_id: i32) {
  with_expected_server(|h| {
    assert!(
      StreamHandle::from_stream_id(stream_id, h).await_event_with_timeout(
        ExpectedStreamEvent::Handshake {
          matcher: None,
          sleep_mode: false,
        },
        Duration::seconds(15)
      )
    );
  });
}

#[no_mangle]
pub extern "C" fn await_api_server_stream_closed(stream_id: i32, wait_time_ms: i64) -> bool {
  with_expected_server(|h| {
    StreamHandle::from_stream_id(stream_id, h).await_event_with_timeout(
      ExpectedStreamEvent::Closed,
      Duration::milliseconds(wait_time_ms),
    )
  })
}

#[no_mangle]
pub extern "C" fn send_configuration_update(stream_id: i32) {
  with_expected_server(|h| {
    StreamHandle::from_stream_id(stream_id, h).blocking_stream_action(
      StreamAction::SendConfiguration(default_configuration_update()),
    );
  });
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

#[no_mangle]
pub extern "C" fn await_configuration_ack(stream_id: i32) {
  with_expected_server(|h| {
    let (updated_stream_id, _) = h.blocking_next_configuration_ack();
    assert_eq!(stream_id, updated_stream_id);
  });
}

#[no_mangle]
pub extern "C" fn configure_aggressive_continuous_uploads(stream_id: i32) {
  with_expected_server(|h| {
    let stream = StreamHandle::from_stream_id(stream_id, h);
    // Ensure that we've received the handshake.
    assert!(stream.await_event_with_timeout(
      ExpectedStreamEvent::Handshake {
        matcher: None,
        sleep_mode: false,
      },
      Duration::milliseconds(2000),
    ));

    // Configure very aggressive runtime values: attempt to read the buffer every 1ms and attempt to
    // upload logs in batches of one. This should ensure a steady state of uploads being sent.
    StreamHandle::from_stream_id(stream_id, h).blocking_stream_action(StreamAction::SendRuntime(
      make_update(
        vec![
          // Enabled platform events listener.
          (
            bd_runtime::runtime::platform_events::ListenerEnabledFlag::path(),
            ValueKind::Bool(true),
          ),
          // Log upload.
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
          // For integration test purposes we don't want reconnects to take long, as it just slows
          // down the tests.
          (
            bd_runtime::runtime::api::MaxBackoffInterval::path(),
            ValueKind::Int(10),
          ),
          // By enabling internal logs here we get basic test coverage on both platforms.
          (
            bd_runtime::runtime::debugging::InternalLoggingFlag::path(),
            ValueKind::Bool(true),
          ),
          // Enable resource utilization logs.
          (
            bd_runtime::runtime::resource_utilization::ResourceUtilizationEnabledFlag::path(),
            ValueKind::Bool(true),
          ),
          // Enable session replay periodic screen collection.
          (
            bd_runtime::runtime::session_replay::PeriodicScreensEnabledFlag::path(),
            ValueKind::Bool(true),
          ),
          // Enable crash reporting.
          (
            bd_runtime::runtime::crash_reporting::Enabled::path(),
            ValueKind::Bool(true),
          ),
          // Enable persistent storage.
          (
            bd_runtime::runtime::state::UsePersistentStorage::path(),
            ValueKind::Bool(true),
          ),
        ],
        "base".to_string(),
      ),
    ));

    h.blocking_next_runtime_ack();

    let configuration_update = make_configuration_update_with_workflow_flushing_buffer(
      "default",
      Type::CONTINUOUS,
      make_buffer_matcher_matching_everything_except_internal_logs(),
      make_workflow_matcher_matching_everything_except_internal_logs(),
    );

    StreamHandle::from_stream_id(stream_id, h)
      .blocking_stream_action(StreamAction::SendConfiguration(configuration_update));

    h.blocking_next_configuration_ack();
  });
}

#[no_mangle]
pub extern "C" fn run_large_upload_test(logger_id: LoggerId<'_>) -> bool {
  let stream_id = await_next_api_stream();

  let is_succes = with_expected_server(|h| {
    if !StreamHandle::from_stream_id(stream_id, h).await_event_with_timeout(
      ExpectedStreamEvent::Handshake {
        matcher: None,
        sleep_mode: false,
      },
      Duration::milliseconds(800),
    ) {
      return false;
    }

    StreamHandle::from_stream_id(stream_id, h).blocking_stream_action(StreamAction::SendRuntime(
      make_update(
        vec![(
          bd_runtime::runtime::log_upload::BatchSizeBytesFlag::path(),
          ValueKind::Int(1024 * 1024 * 2), // 2 MiB, 2x the request buffer.
        )],
        "base".to_string(),
      ),
    ));

    h.blocking_next_runtime_ack();

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

    StreamHandle::from_stream_id(stream_id, h).blocking_stream_action(
      StreamAction::SendConfiguration(extra_large_buffer_uploading_everything),
    );

    h.blocking_next_configuration_ack();

    true
  });

  if !is_succes {
    return false;
  }

  // This gets us to the batch size limit of ~2 MiB.
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

  with_expected_server(|s| {
    assert_matches!(s.blocking_next_log_upload(), Some(log_upload) => {
        assert_eq!(log_upload.logs().len(), 21);
    });
  });

  true
}

#[no_mangle]
pub extern "C" fn run_aggressive_upload_test_with_stream_drops(logger_id: LoggerId<'_>) -> bool {
  let stream_id = await_next_api_stream();
  if stream_id == -1 {
    return false;
  }

  configure_aggressive_continuous_uploads(stream_id);

  with_expected_server(|h| {
    let mut stream = StreamHandle::from_stream_id(stream_id, h);

    // Write logs and turn around the API stream several times. This should tickle the cases
    // where the stream is being torn down while uploads are happening.
    for _ in 0 .. 5 {
      for _ in 0 .. 10 {
        logger_id.log(
          log_level::TRACE,
          LogType::NORMAL,
          "hello".into(),
          [].into(),
          [].into(),
          None,
          bd_logger::Block::No,
          &CaptureSession::default(),
        );
      }

      stream.blocking_stream_action(StreamAction::CloseStream);

      stream = h.blocking_next_stream()?;

      assert!(stream.await_event_with_timeout(
        ExpectedStreamEvent::Handshake {
          matcher: None,
          sleep_mode: false,
        },
        Duration::seconds(10),
      ));
    }

    // Verify at least one upload to make sure that things are working as expected.
    h.blocking_next_log_upload().unwrap();

    Some(())
  })
  .is_some()
}

// Runs a test scenario where we configure very aggressive upload parameters via runtime, then issue
// a number of logs which we then see uploaded. Some logs are left in the upload buffer to give test
// coverage to the case where the network is shut down while the logger is still processing uploads.
#[no_mangle]
pub extern "C" fn run_aggressive_upload_test(logger_id: LoggerId<'_>) {
  let stream_id = await_next_api_stream();
  assert_ne!(stream_id, -1);

  configure_aggressive_continuous_uploads(stream_id);

  // Write a bunch of logs, all of which should get uploaded to the server.
  for _ in 0 .. 100 {
    logger_id.log(
      log_level::TRACE,
      LogType::NORMAL,
      "hello".into(),
      [].into(),
      [].into(),
      None,
      bd_logger::Block::No,
      &CaptureSession::default(),
    );
  }

  // Verify that the first ten logs are uploaded successfully. We leave a few in the queue in
  // order to test what happens when the test ends while there is still logs being uploaded.
  with_expected_server(|h| {
    for _ in 0 .. 10 {
      let upload = h
        .blocking_next_log_upload()
        .expect("logs should be uploaded");

      assert_eq!(upload.logs().len(), 1);
    }
  });
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
