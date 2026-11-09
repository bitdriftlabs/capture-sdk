// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

use crate::writer::CRASH_RECORD;
use std::ptr::null_mut;
use std::sync::atomic::Ordering;
use std::sync::{Mutex, MutexGuard};

static TEST_CRASH_RECORD_LOCK: Mutex<()> = Mutex::new(());

pub(crate) struct TestCrashRecordGuard {
  _guard: MutexGuard<'static, ()>,
}

impl Drop for TestCrashRecordGuard {
  fn drop(&mut self) {
    CRASH_RECORD.store(null_mut(), Ordering::Release);
  }
}

pub(crate) fn test_crash_record_guard() -> TestCrashRecordGuard {
  let guard = match TEST_CRASH_RECORD_LOCK.lock() {
    Ok(guard) => guard,
    Err(poisoned) => poisoned.into_inner(),
  };
  CRASH_RECORD.store(null_mut(), Ordering::Release);
  TestCrashRecordGuard { _guard: guard }
}
