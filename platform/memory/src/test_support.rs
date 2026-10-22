// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt
use std::sync::{Mutex, MutexGuard};

/// Bazel runs a crate's tests as threads of one process. A region mapped and unmapped while another
/// test walks the map shows in the walk but not in the footprint read after it.
/// Two censuses locking zones from different threads can deadlock.
static MEMORY_LOCK: Mutex<()> = Mutex::new(());

pub fn memory_lock() -> MutexGuard<'static, ()> {
  MEMORY_LOCK
    .lock()
    .unwrap_or_else(std::sync::PoisonError::into_inner)
}
