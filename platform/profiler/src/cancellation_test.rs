// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

#![allow(clippy::unwrap_used)]
use super::CancellationToken;
use crate::CaptureError;
use std::sync::{Arc, mpsc};
use std::thread;
use std::time::{Duration, Instant};

#[test]
fn cancellation_after_worker_registration_wakes_the_waiter() {
  let token = Arc::new(CancellationToken::default());
  let worker_token = token.clone();
  let (tx, rx) = mpsc::channel();
  let worker = thread::spawn(move || {
    tx.send(worker_token.wait(Instant::now() + Duration::from_secs(30)))
      .unwrap();
  });
  let deadline = Instant::now() + Duration::from_secs(5);
  while token.worker.get().is_none() && Instant::now() < deadline {
    thread::yield_now();
  }
  assert!(token.worker.get().is_some());
  token.cancel();
  assert_eq!(
    rx.recv_timeout(Duration::from_secs(2)).unwrap(),
    Err(CaptureError::Cancelled)
  );
  worker.join().unwrap();
}
