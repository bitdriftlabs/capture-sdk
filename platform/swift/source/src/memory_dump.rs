// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

use crate::ffi::{make_nsdata, make_nsstring, set_dictionary_value};
use bd_error_reporter::reporter::with_handle_unexpected_or;
use bd_memory_dump::{Dump, HeapOptions, Request, Stages};
use objc::rc::StrongPtr;
use objc::runtime::Object;

const STAGE_COUNTERS: u32 = 1 << 0;
const STAGE_REGIONS: u32 = 1 << 1;
const STAGE_HEAP: u32 = 1 << 2;

/// Runs the requested stages of a memory dump on the calling thread.
///
/// Returns an autoreleased dictionary with `context` (string to string) and `attachment` (the JSON
/// report), or with `error` when the dump failed. Returns nil on an unexpected failure.
#[unsafe(no_mangle)]
extern "C" fn capture_memory_dump(
  stages: u32,
  heap_budget_ms: u64,
  heap_owners: bool,
) -> *const Object {
  with_handle_unexpected_or(
    move || {
      let request = Request {
        stages: Stages {
          counters: stages & STAGE_COUNTERS != 0,
          regions: stages & STAGE_REGIONS != 0,
          heap: stages & STAGE_HEAP != 0,
        },
        heap: HeapOptions {
          budget_ms: heap_budget_ms,
          owners: heap_owners,
        },
      };
      let result = unsafe { StrongPtr::new(msg_send![class!(NSMutableDictionary), new]) };
      match bd_memory_dump::capture(&request) {
        Ok(dump) => write_dump(&result, &dump)?,
        Err(error) => set_dictionary_value(&result, "error", &make_nsstring(&error.to_string())?)?,
      }
      Ok(result.autorelease().cast_const())
    },
    std::ptr::null(),
    "swift memory dump",
  )
}

fn write_dump(result: &StrongPtr, dump: &Dump) -> anyhow::Result<()> {
  let context = unsafe { StrongPtr::new(msg_send![class!(NSMutableDictionary), new]) };
  for (key, value) in &dump.context {
    set_dictionary_value(&context, key, &make_nsstring(value)?)?;
  }
  set_dictionary_value(result, "context", &context)?;
  set_dictionary_value(result, "attachment", &make_nsdata(&dump.attachment))
}
