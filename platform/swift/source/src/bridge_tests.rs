// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/licenses/strict/1.0.0

#![allow(clippy::unwrap_used)]

use crate::bridge::{State, StreamWriter, SwiftNetworkStream, capture_complete_command};
use crate::commands::commands_test::command_target;
use crate::commands::{self, Target as CommandTarget};
use crate::ffi::{convert_map, make_nsstring};
use bd_api::PlatformNetworkStream;
use bd_artifact_upload::UploadSource;
use bd_logger::{CommandInvocation, CommandResult, RegisteredCommandHandler};
use bd_runtime::runtime::FeatureFlag;
use bd_test_helpers::RecordingErrorReporter;
use bd_test_helpers::runtime::ValueKind;
use objc::rc::autoreleasepool;
use std::collections::HashMap;
use std::future::poll_fn;
use std::sync::Arc;
use std::sync::atomic::{AtomicBool, AtomicUsize, Ordering};
use std::task::Poll;
use std::{env, fs, ptr};
use tempfile::TempDir;

#[tokio::test]
async fn native_file_completion_transfers_or_cleans_the_owned_source() -> anyhow::Result<()> {
  let scratch = env::current_dir()?.join(".tmp");
  fs::create_dir_all(&scratch)?;
  for consume in [true, false] {
    let directory = TempDir::with_prefix_in("native-command-", &scratch)?;
    let path = directory.path().join("attachment");
    fs::write(&path, b"native file payload")?;
    let object = command_target();
    let target = CommandTarget::new(*object);
    let mut execution = Box::pin(target.execute(CommandInvocation {
      command_id: None,
      registered_command_id: "file".into(),
      arguments: HashMap::new(),
      session_id: "session".into(),
    }));
    poll_fn(|context| {
      assert!(execution.as_mut().poll(context).is_pending());
      Poll::Ready(())
    })
    .await;
    let request_id = unsafe { *(**object).get_ivar::<u64>("requestID") };

    autoreleasepool(|| -> anyhow::Result<()> {
      let context = convert_map(&HashMap::from([("source", "native-file")]))?;
      let attachment_path = make_nsstring(path.to_str().unwrap())?;
      let mime_type = make_nsstring("application/octet-stream")?;
      assert!(capture_complete_command(
        request_id,
        true,
        *context,
        ptr::null(),
        *attachment_path,
        *mime_type,
        ptr::null(),
        ptr::null(),
      ));
      Ok(())
    })?;
    assert!(commands::take_completion(request_id).is_none());
    assert_eq!(fs::read(&path)?, b"native file payload");

    if consume {
      let CommandResult::Completed {
        fields,
        attachment: Some(attachment),
      } = execution.await
      else {
        anyhow::bail!("expected file-backed command completion");
      };
      assert_eq!(fields.get("source"), Some(&"native-file".into()));
      assert_eq!(
        attachment.content_type.as_deref(),
        Some("application/octet-stream")
      );
      let UploadSource::Path(source) = attachment.source else {
        anyhow::bail!("expected file source rather than copied bytes");
      };
      assert_eq!(source, path);
      assert_eq!(fs::read(&source)?, b"native file payload");
      unsafe { assert_eq!(*(**object).get_ivar::<u64>("cancelCount"), 0) };
    } else {
      drop(execution);
      assert!(!path.exists());
      unsafe { assert_eq!(*(**object).get_ivar::<u64>("cancelCount"), 1) };
    }
  }
  Ok(())
}

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
  _temp_dir: tempfile::TempDir,
}

impl Setup {
  fn new() -> Self {
    let capacity = Arc::new(AtomicUsize::new(0));
    let calls = Arc::new(AtomicUsize::new(0));
    let timeout = Arc::new(AtomicBool::new(false));

    let (stream_state_tx, _) = tokio::sync::watch::channel(State::Open);
    let temp_dir = tempfile::TempDir::new().unwrap();
    let runtime = bd_runtime::runtime::ConfigLoader::new(temp_dir.path());

    Self {
      capacity,
      calls,
      timeout,
      stream_state_tx,
      runtime,
      _temp_dir: temp_dir,
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
    .await
    .unwrap();

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
    .await
    .unwrap();

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
