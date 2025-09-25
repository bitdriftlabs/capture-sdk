// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

use bd_buffer::{AggregateRingBuffer, PerRecordCrc32Check, RingBuffer, RingBufferStats};
use bd_logger::{log_level, Block, CaptureSession, InitParams, LogType, LoggerHandle};
use bd_noop_network::NoopNetwork;
use bd_session::fixed::UUIDCallbacks;
use bd_session::{fixed, Strategy};
use bd_test_helpers::config_helper;
use bd_test_helpers::metadata::EmptyMetadata;
use bd_test_helpers::metadata_provider::LogMetadata;
use bd_test_helpers::session::InMemoryStorage;
use config_helper::make_configuration_update_with_workflow_flushing_buffer_on_anything;
use criterion::{criterion_group, criterion_main, Criterion};
use protobuf::Message;
use std::sync::Arc;
use std::time::Duration;
use tempfile::tempdir;

// TODO(mattklein123): #[allow(unused)] has been put on a bunch of things so that benchmarks can
// be manually removed from criterion_group! below. The main issue is that criterion filtering only
// happens inside bench_function() so all of the setup code still runs. This leads to messy profiles
// if trying to generate flame graphs or individual benchmarks. I couldn't figure out any easy way
// to fix this so just leaving this hack around for now.

fn do_log(logger: &LoggerHandle) {
  logger.log(
    log_level::TRACE,
    LogType::Normal,
    "hello".into(),
    [].into(),
    [].into(),
    None,
    Block::No,
    CaptureSession::default(),
  );
}

// Test the performance when logging with no config, i.e. it will not match the log against
// anything or attempt to write it to any buffers.
#[allow(unused)]
fn simple_log(c: &mut Criterion) {
  let _ignored = std::fs::remove_file("./config.pb");

  let store = Arc::new(bd_key_value::Store::new(Box::<InMemoryStorage>::default()));
  let device = Arc::new(bd_logger::Device::new(store.clone()));

  let logger = bd_logger::LoggerBuilder::new(InitParams {
    sdk_directory: ".".into(),
    api_key: "foo".to_string(),
    session_strategy: Arc::new(Strategy::Fixed(fixed::Strategy::new(
      store.clone(),
      Arc::new(UUIDCallbacks),
    ))),
    metadata_provider: Arc::new(LogMetadata {
      timestamp: time::OffsetDateTime::now_utc().into(),
      ..Default::default()
    }),
    resource_utilization_target: Box::new(bd_test_helpers::resource_utilization::EmptyTarget),
    session_replay_target: Box::new(bd_test_helpers::session_replay::NoOpTarget),
    events_listener_target: Box::new(bd_test_helpers::events::NoOpListenerTarget),
    device,
    store,
    network: Box::new(NoopNetwork {}),
    static_metadata: Arc::new(EmptyMetadata),
    start_in_sleep_mode: false,
    feature_flags_file_size_bytes: 1024 * 1024,
    feature_flags_high_watermark: 0.8,
  })
  .build()
  .unwrap()
  .0;

  let handle = logger.new_logger_handle();

  c.bench_function("testing zero config log", |b| b.iter(|| do_log(&handle)));
}

// Test the performance when both a listener and a buffer is configured, testing both listener
// matching and buffer writes.
#[allow(unused)]
fn with_matcher_and_buffer(c: &mut Criterion) {
  // We write config to disk and rely on the cached config mechanism, as this avoids having to run a
  // test server in memory which complicates profiling etc.
  let config = make_configuration_update_with_workflow_flushing_buffer_on_anything(
    "default",
    config_helper::BufferType::CONTINUOUS,
  );
  let encoded_config = config.write_to_bytes().unwrap();
  let _ignored = std::fs::write("./config.pb", encoded_config);

  let store = Arc::new(bd_key_value::Store::new(Box::<InMemoryStorage>::default()));
  let device = Arc::new(bd_logger::Device::new(store.clone()));

  let logger = bd_logger::LoggerBuilder::new(InitParams {
    sdk_directory: ".".into(),
    api_key: "foo-api-key".to_string(),
    session_strategy: Arc::new(Strategy::Fixed(fixed::Strategy::new(
      store.clone(),
      Arc::new(UUIDCallbacks),
    ))),
    metadata_provider: Arc::new(LogMetadata {
      timestamp: time::OffsetDateTime::now_utc().into(),
      ..Default::default()
    }),
    resource_utilization_target: Box::new(bd_test_helpers::resource_utilization::EmptyTarget),
    session_replay_target: Box::new(bd_test_helpers::session_replay::NoOpTarget),
    events_listener_target: Box::new(bd_test_helpers::events::NoOpListenerTarget),
    device,
    store,
    network: Box::new(NoopNetwork {}),
    static_metadata: Arc::new(EmptyMetadata),
    start_in_sleep_mode: false,
    feature_flags_file_size_bytes: 1024 * 1024,
    feature_flags_high_watermark: 0.8,
  })
  .build()
  .unwrap()
  .0;

  // TODO(snowp): Add a way to guarantuee that the cached config has been read by the time
  // init_logger ends.
  std::thread::sleep(Duration::from_secs(1));

  let handle = logger.new_logger_handle();
  c.bench_function("testing simple listener with buffer write", |b| {
    b.iter(|| do_log(&handle));
  });
}

fn buffer_write_and_read(c: &mut Criterion) {
  let temp_dir = tempdir().unwrap();
  let stats = Arc::new(RingBufferStats::default());
  let buffer = AggregateRingBuffer::new(
    "test",
    1024 * 1024,
    temp_dir.path().join("buffer"),
    1024 * 1024 * 2,
    PerRecordCrc32Check::Yes,
    bd_buffer::AllowOverwrite::Yes,
    stats.clone(),
    stats,
  )
  .unwrap();
  let mut producer = buffer.clone().register_producer().unwrap();
  let mut consumer = buffer.clone().register_consumer().unwrap();

  c.bench_function("buffer_write_and_read", |b| {
    b.iter(|| {
      for _ in 0 .. 10 {
        let reserved = producer.reserve(100, true).unwrap();
        reserved.fill(0);
        producer.commit().unwrap();
      }

      for _ in 0 .. 10 {
        let reserved = consumer.start_read(true).unwrap();
        assert!(!reserved.iter().any(|b| *b != 0));
        consumer.finish_read().unwrap();
      }
    });
  });

  std::mem::drop(buffer);
}

criterion_group!(
  benches,
  simple_log,
  with_matcher_and_buffer,
  buffer_write_and_read
);

criterion_main!(benches);
