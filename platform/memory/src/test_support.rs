// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt
use std::sync::{Mutex, MutexGuard};

/// Two censuses locking zones from different threads can deadlock, and Bazel runs a crate's tests
/// as threads of one process.
static HEAP_LOCK: Mutex<()> = Mutex::new(());

pub fn heap_lock() -> MutexGuard<'static, ()> {
  HEAP_LOCK
    .lock()
    .unwrap_or_else(std::sync::PoisonError::into_inner)
}
