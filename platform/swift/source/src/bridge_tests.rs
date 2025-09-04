// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

use crate::bridge::{State, StreamWriter, SwiftNetworkStream};
use bd_api::PlatformNetworkStream;
use bd_runtime::runtime::FeatureFlag;
use bd_test_helpers::runtime::ValueKind;
use bd_test_helpers::RecordingErrorReporter;
use std::path::PathBuf;
use std::sync::atomic::{AtomicBool, AtomicUsize, Ordering};
use std::sync::Arc;

struct TestWriter {
  capacity: Arc<AtomicUsize>,
  calls: Arc<AtomicUsize>,
  timeout: Arc<AtomicBool>,
}

impl StreamWriter for TestWriter {
  #[allow(clippy::cast_possible_wrap)]
  fn write_to_stream(&mut self, data: &[u8]) -> isize {
    self.calls.fetch_add(1, Ordering::SeqCst);

    if self.capacity.load(Ordering::SeqCst) == 0 {
      return -1;
    }

    if self.capacity.load(Ordering::SeqCst) > data.len() {
      self.capacity.fetch_sub(data.len(), Ordering::SeqCst);

      data.len() as isize
    } else {
      self.capacity.swap(0, Ordering::SeqCst) as isize
    }
  }

  fn shutdown(&mut self) {}

  fn timeout(&mut self) {
    self.timeout.store(true, Ordering::SeqCst);
  }
}

// Setup for testing the behavior of sendData. The number of retries are verified by first
// specifying the number of expected
struct Setup {
  capacity: Arc<AtomicUsize>,
  calls: Arc<AtomicUsize>,
  timeout: Arc<AtomicBool>,
  stream_state_tx: tokio::sync::watch::Sender<State>,
  runtime: Arc<bd_runtime::runtime::ConfigLoader>,
}

impl Setup {
  fn new() -> Self {
    let capacity = Arc::new(AtomicUsize::new(0));
    let calls = Arc::new(AtomicUsize::new(0));
    let timeout = Arc::new(AtomicBool::new(false));

    let (stream_state_tx, _) = tokio::sync::watch::channel(State::Open);
    let runtime = bd_runtime::runtime::ConfigLoader::new(&PathBuf::from("."));

    Self {
      capacity,
      calls,
      timeout,
      stream_state_tx,
      runtime,
    }
  }

  fn stream(&self) -> SwiftNetworkStream<TestWriter> {
    SwiftNetworkStream {
      stream_writer: TestWriter {
        capacity: self.capacity.clone(),
        calls: self.calls.clone(),
        timeout: self.timeout.clone(),
      },
      stream_state_rx: self.stream_state_tx.subscribe(),
      emit_send_data_timeout_error: self.runtime.register_bool_watch(),
      send_data_timeout: self.runtime.register_int_watch(),
    }
  }

  fn received_calls(&self) -> usize {
    self.calls.load(Ordering::SeqCst)
  }

  fn timed_out(&self) -> bool {
    self.timeout.load(Ordering::SeqCst)
  }
}

#[tokio::test(start_paused = true)]
async fn no_capacity_no_error_without_runtime() {
  let setup = Setup::new();

  let mut stream = setup.stream();

  let ((), error) = RecordingErrorReporter::maybe_async_record_error(async move {
    assert!(stream.send_data(&[0; 100]).await.is_ok());
  })
  .await;

  assert!(error.is_none());
  assert!(setup.timed_out());
  assert_eq!(setup.received_calls(), 1800);
}

#[tokio::test(start_paused = true)]
async fn no_capacity_sends_error_with_runtime() {
  let setup = Setup::new();

  setup
    .runtime
    .update_snapshot(bd_test_helpers::runtime::make_simple_update(vec![(
      crate::bridge::flags::ReportSendDataTimeoutError::path(),
      ValueKind::Bool(true),
    )]))
    .await;

  let mut stream = setup.stream();

  let ((), error) = RecordingErrorReporter::maybe_async_record_error(async move {
    assert!(stream.send_data(&[0; 100]).await.is_ok());
  })
  .await;

  assert_eq!(error.unwrap().as_str(), "send_data: timed out!");
  assert!(setup.timed_out());
  assert_eq!(setup.received_calls(), 1800);
}

#[tokio::test(start_paused = true)]
async fn runtime_configured_deadline() {
  let setup = Setup::new();

  setup
    .runtime
    .update_snapshot(bd_test_helpers::runtime::make_simple_update(vec![(
      crate::bridge::flags::SendDataTimeout::path(),
      ValueKind::Int(1),
    )]))
    .await;

  let mut stream = setup.stream();

  let ((), error) = RecordingErrorReporter::maybe_async_record_error(async move {
    assert!(stream.send_data(&[0; 100]).await.is_ok());
  })
  .await;

  assert!(error.is_none());
  assert!(setup.timed_out());
  // Only 10 calls since we reduce the overall timeout, allowing for fewer retries.
  assert_eq!(setup.received_calls(), 10);
}

#[tokio::test(start_paused = true)]
async fn available_capacity() {
  let setup = Setup::new();

  setup.capacity.store(200, Ordering::SeqCst);

  let mut stream = setup.stream();

  let ((), error) = RecordingErrorReporter::maybe_async_record_error(async move {
    assert!(stream.send_data(&[0; 100]).await.is_ok());
  })
  .await;

  assert!(error.is_none());
  assert_eq!(setup.received_calls(), 1);
  assert!(!setup.timed_out());
}
