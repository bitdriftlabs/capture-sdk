// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

use bd_logger::{log_level, AnnotatedLogField, InitParams, LogField, LogFieldValue, LogType};
use bd_session::fixed::UUIDCallbacks;
use bd_session::{fixed, Strategy};
use bd_shutdown::ComponentShutdownTrigger;
use bd_test_helpers::metadata::EmptyMetadata;
use bd_test_helpers::metadata_provider::LogMetadata;
use bd_test_helpers::session::InMemoryStorage;
use criterion::{criterion_group, criterion_main, Criterion};
use std::sync::Arc;
use std::time::Duration;

fn test_live_match_performance(c: &mut Criterion) {
  let _ignored = std::fs::remove_file("./config.pb");
  let _ignored = std::fs::remove_dir_all("./buffers");

  let shutdown = ComponentShutdownTrigger::default();

  // TODO(mattklein123): Allow auth key to be supplied somehow.
  let network =
    bd_hyper_network::HyperNetwork::run_on_thread(&bitdrift_api_url(), shutdown.make_shutdown());

  let metadata_provider = Arc::new(LogMetadata {
    timestamp: time::OffsetDateTime::now_utc(),
    fields: Vec::new(),
  });

  let store = Arc::new(bd_key_value::Store::new(Box::<InMemoryStorage>::default()));
  let device = Arc::new(bd_logger::Device::new(store.clone()));

  let logger = bd_logger::LoggerBuilder::new(InitParams {
    api_key: "replace me with a real auth token".to_string(),
    network: Box::new(network),
    static_metadata: Arc::new(EmptyMetadata),
    sdk_directory: ".".into(),
    session_strategy: Arc::new(Strategy::Fixed(fixed::Strategy::new(
      store.clone(),
      Arc::new(UUIDCallbacks),
    ))),
    store,
    metadata_provider,
    resource_utilization_target: Box::new(bd_test_helpers::resource_utilization::EmptyTarget),
    session_replay_target: Box::new(bd_test_helpers::session_replay::NoOpTarget),
    events_listener_target: Box::new(bd_test_helpers::events::NoOpListenerTarget),
    device,
  })
  .build()
  .unwrap()
  .0;
  let handle = logger.new_logger_handle();

  // Wait for the configuration to become active.
  std::thread::sleep(Duration::from_secs(2));

  c.bench_function("tiny logs", |b| {
    b.iter(|| {
      handle.log(
        log_level::TRACE,
        LogType::Normal,
        "hello".into(),
        vec![],
        vec![],
        None,
        false,
      );
      handle.log(
        log_level::DEBUG,
        LogType::Normal,
        "hello".into(),
        vec![],
        vec![],
        None,
        false,
      );
      handle.log(
        log_level::INFO,
        LogType::Normal,
        "hello".into(),
        vec![],
        vec![],
        None,
        false,
      );
      handle.log(
        log_level::WARNING,
        LogType::Normal,
        "hello".into(),
        vec![],
        vec![],
        None,
        false,
      );
      handle.log(
        log_level::ERROR,
        LogType::Normal,
        "hello".into(),
        vec![],
        vec![],
        None,
        false,
      );
    });
  });
  c.bench_function("analytic event-like log", |b| {
    b.iter(|| {
      handle.log(
        log_level::INFO,
        LogType::Normal,
        "analytics event: action.action_name".into(),
        vec![AnnotatedLogField {
          field: LogField {
            key: "log_arg".into(),
            value: LogFieldValue::String(
              "{\"auth\":{\"driver_dispatchable\":1,\"session_id\": \
               \"5B2CB91F-9C1F-4F16-9054-25D2DDE04B4F\"}}"
                .into(),
            ),
          },
          kind: bd_logger::LogFieldKind::Ootb,
        }],
        vec![],
        None,
        false,
      );
    });
  });
}

fn bitdrift_api_url() -> String {
  std::env::var("BITDRIFT_URL").unwrap_or_else(|_| "https://api.bitdrift.io".to_string())
}

criterion_group!(benches, test_live_match_performance);

criterion_main!(benches);
