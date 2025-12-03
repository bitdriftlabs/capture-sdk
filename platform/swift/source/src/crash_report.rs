// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

use crate::conversion::{objc_value_to_rust, rust_value_to_objc};
use crate::ffi::nsstring_into_string;
use ahash::AHashMap;
use anyhow;
use bd_bonjson::decoder::DecodeError;
use bd_bonjson::{decoder, Value};
use bd_error_reporter::reporter::with_handle_unexpected_or;
use objc::runtime::Object;
use parking_lot::Mutex;
use std::collections::HashMap;
use std::fs;
use std::path::Path;

struct NamedThread {
  name: String,
  call_stack: Vec<u64>,
  count: usize,
}

#[repr(C)]
#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum CacheResult {
  /// The report was not cached due to an error
  Failure            = 0,
  /// The report file does not exist
  ReportDoesNotExist = 1,
  /// Successfully cached a partial document
  PartialSuccess     = 2,
  /// Successfully cached the complete document
  Success            = 3,
}

// Global cache for the most recently loaded KSCrash report.
// This allows us to safely delete the report file so that it's not picked up next launch by
// mistake.
static CACHED_KSCRASH_REPORT: Mutex<Option<AHashMap<String, Value>>> = Mutex::new(None);

// API

#[no_mangle]
extern "C" fn capture_cache_kscrash_report(kscrash_report_path_ptr: *const Object) -> CacheResult {
  with_handle_unexpected_or(
    || -> anyhow::Result<CacheResult> {
      capture_cache_kscrash_report_impl(kscrash_report_path_ptr)
    },
    CacheResult::Failure,
    "cache kscrash report",
  )
}

#[no_mangle]
extern "C" fn capture_enhance_metrickit_diagnostic_report(
  metrickit_report_ptr: *const Object,
) -> *const Object {
  enhance_metrickit_diagnostic_report_impl(metrickit_report_ptr)
    .inspect_err(|e| log::error!("Failed to enhance MetricKit report: {e}"))
    .unwrap_or(Some(metrickit_report_ptr))
    .unwrap_or(metrickit_report_ptr)
}

// Implementation

fn capture_cache_kscrash_report_impl(
  kscrash_report_path_ptr: *const Object,
) -> anyhow::Result<CacheResult> {
  let kscrash_report_path = unsafe { nsstring_into_string(kscrash_report_path_ptr) }
    .map_err(|e| anyhow::anyhow!("Failed to convert KSCrash report path to string: {e}"))?;

  if !Path::new(&kscrash_report_path).exists() {
    return Ok(CacheResult::ReportDoesNotExist);
  }

  let file_contents = fs::read(&kscrash_report_path)?;

  if file_contents.len() == 0 {
    // File is created when the writer is initialized. An empty file should
    // indicate that no error occurred to be written.
    return Ok(CacheResult::ReportDoesNotExist);
  }

  let (hashmap, was_partial) = match decoder::from_slice(&file_contents) {
    Ok((_, Value::Object(hashmap))) => (hashmap, false),
    Ok((..)) => {
      return Err(anyhow::anyhow!(
        "KSCrash report is not a valid object/hashmap"
      ))
    },
    Err(DecodeError::Partial {
      partial_value,
      error: _,
    }) => match partial_value {
      Value::Object(hashmap) => (hashmap, true),
      _ => {
        return Err(anyhow::anyhow!(
          "KSCrash report is not a valid object/hashmap"
        ))
      },
    },
    Err(DecodeError::Fatal(_)) => return Err(anyhow::anyhow!("Failed to decode KSCrash report")),
  };

  CACHED_KSCRASH_REPORT.lock().replace(hashmap);

  Ok(
    if was_partial {
      CacheResult::PartialSuccess
    } else {
      CacheResult::Success
    },
  )
}

fn enhance_metrickit_diagnostic_report_impl(
  metrickit_report_ptr: *const Object,
) -> anyhow::Result<Option<*const Object>> {
  let value = unsafe { objc_value_to_rust(metrickit_report_ptr) }
    .map_err(|e| anyhow::anyhow!("Failed to convert metrickit_report_ptr to Rust Value: {e}"))?;

  let Value::Object(metrickit_report) = value else {
    return Err(anyhow::anyhow!(
      "metrickit_report is not a valid object/hashmap"
    ));
  };

  let kscrash_report = CACHED_KSCRASH_REPORT
    .lock()
    .as_ref()
    .ok_or_else(|| {
      anyhow::anyhow!("No KSCrash report has been cached. Call capture_cache_kscrash_report first.")
    })?
    .clone();

  let Some(enhanced_hashmap) = enhance_report(&metrickit_report, &kscrash_report)? else {
    return Ok(None);
  };
  let enhanced_report = Value::Object(enhanced_hashmap);

  let strong_ptr = unsafe { rust_value_to_objc(&enhanced_report) }
    .map_err(|e| anyhow::anyhow!("Failed to convert enhanced_report to Objective-C: {e}"))?;
  Ok(Some(*strong_ptr))
}

fn enhance_report(
  metrickit_report: &AHashMap<String, Value>,
  kscrash_report: &AHashMap<String, Value>,
) -> anyhow::Result<Option<AHashMap<String, Value>>> {
  if !diagnostic_metadata_matches_in_reports(metrickit_report, kscrash_report) {
    return Ok(None);
  }

  let named_threads = named_threads_from_kscrash_report(kscrash_report)?;

  let Some(named_threads) = named_threads else {
    return Ok(None);
  };

  let enhanced_metrickit =
    inject_thread_names_into_metrickit(metrickit_report.clone(), &named_threads)?;
  Ok(Some(enhanced_metrickit))
}

fn diagnostic_metadata_matches_in_reports(
  report_a: &AHashMap<String, Value>,
  report_b: &AHashMap<String, Value>,
) -> bool {
  let (Some(a_meta), Some(b_meta)) = (
    report_a.get("diagnosticMetaData"),
    report_b.get("diagnosticMetaData"),
  ) else {
    return false;
  };

  let check_keys = ["exceptionType", "exceptionCode", "signal", "pid"];
  for key in &check_keys {
    if a_meta.get(key) != b_meta.get(key) {
      return false;
    }
  }
  true
}

fn named_threads_from_kscrash_report(
  kscrash_report: &AHashMap<String, Value>,
) -> anyhow::Result<Option<Vec<NamedThread>>> {
  let mut named_threads: Vec<NamedThread> = Vec::new();

  let Some(Value::Array(threads)) = kscrash_report.get("threads") else {
    return Err(anyhow::anyhow!("kscrash_report missing 'threads' array"));
  };

  for thread in threads {
    let Value::Object(thread) = thread else {
      return Err(anyhow::anyhow!("Thread is not a valid object/hashmap"));
    };

    let Some(Value::String(thread_name)) = thread.get("name") else {
      continue;
    };

    let Some(call_stack) = extract_call_stack_from_kcrash_thread(thread)? else {
      continue;
    };

    // Check if we already have a thread with this name
    if let Some(existing_thread) = named_threads.iter_mut().find(|t| t.name == *thread_name) {
      // Increment count for existing thread
      existing_thread.count += 1;
    } else {
      // Create new NamedThread entry
      named_threads.push(NamedThread {
        name: thread_name.clone(),
        call_stack,
        count: 1,
      });
    }
  }

  if named_threads.is_empty() {
    Ok(None)
  } else {
    Ok(Some(named_threads))
  }
}

fn extract_call_stack_from_kcrash_thread(
  kscrash_thread: &AHashMap<String, Value>,
) -> anyhow::Result<Option<Vec<u64>>> {
  let Some(Value::Object(backtrace)) = kscrash_thread.get("backtrace") else {
    return Err(anyhow::anyhow!("Thread missing 'backtrace' object"));
  };

  let Some(Value::Array(contents)) = backtrace.get("contents") else {
    return Err(anyhow::anyhow!("Backtrace missing 'contents' array"));
  };

  let mut call_stack = Vec::new();

  for frame in contents {
    let Value::Object(frame) = frame else {
      return Err(anyhow::anyhow!("Frame is not a valid object/hashmap"));
    };

    let Some(address) = frame.get("address") else {
      return Err(anyhow::anyhow!("Frame missing 'address' field"));
    };

    let address = parse_address_value(address)?;
    call_stack.push(address);
  }

  if call_stack.is_empty() {
    Ok(None)
  } else {
    Ok(Some(call_stack))
  }
}

fn parse_address_value(address: &Value) -> anyhow::Result<u64> {
  match address {
    Value::Unsigned(address) => Ok(*address),
    Value::Signed(address) => (*address >= 0)
      .then_some({
        #[allow(clippy::cast_sign_loss)]
        {
          *address as u64
        }
      })
      .ok_or_else(|| anyhow::anyhow!("Address value is negative: {address}")),
    _ => Err(anyhow::anyhow!(
      "Address value is not a valid number (got {:?})",
      address
    )),
  }
}

/// Injects thread names from a `KSCrash` report into a `MetricKit` report where their thread call
/// stacks match.
fn inject_thread_names_into_metrickit(
  mut metrickit_report: AHashMap<String, Value>,
  named_threads: &[NamedThread],
) -> anyhow::Result<AHashMap<String, Value>> {
  // Track how many times each named thread has been matched
  let mut usage_counts: HashMap<String, usize> = HashMap::new();

  let Some(Value::Object(ref mut call_stack_tree)) = metrickit_report.get_mut("callStackTree")
  else {
    return Err(anyhow::anyhow!(
      "MetricKit report missing 'callStackTree' object"
    ));
  };

  let Some(Value::Array(ref mut threads)) = call_stack_tree.get_mut("callStacks") else {
    return Err(anyhow::anyhow!("CallStackTree missing 'callStacks' array"));
  };

  for thread in threads.iter_mut() {
    let Value::Object(ref mut thread) = thread else {
      return Err(anyhow::anyhow!("Thread is not a valid object/hashmap"));
    };

    let call_stack = extract_call_stack_from_metrickit_thread(thread)?;
    let Some(call_stack) = call_stack else {
      continue;
    };

    // Find a matching named thread that hasn't exceeded its count limit
    let Some(matching_thread) =
      find_matching_named_thread_with_limit(&call_stack, named_threads, &usage_counts)
    else {
      continue;
    };

    let thread_name = matching_thread.name.clone();
    let current_count = usage_counts.get(&thread_name).unwrap_or(&0);
    let new_count = current_count + 1;

    usage_counts.insert(thread_name.clone(), new_count);

    thread.insert("name".to_string(), Value::String(thread_name));
  }

  Ok(metrickit_report)
}

fn extract_call_stack_from_metrickit_thread(
  thread: &AHashMap<String, Value>,
) -> anyhow::Result<Option<Vec<u64>>> {
  let Some(Value::Array(root_frames)) = thread.get("callStackRootFrames") else {
    return Err(anyhow::anyhow!(
      "MetricKit thread missing 'callStackRootFrames' array"
    ));
  };

  // Assume callStackRootFrames contains only one call stack
  let Some(root_frame) = root_frames.first() else {
    return Err(anyhow::anyhow!(
      "MetricKit thread 'callStackRootFrames' array is empty"
    ));
  };

  let Value::Object(root_frame) = root_frame else {
    return Err(anyhow::anyhow!("Root frame is not a valid object/hashmap"));
  };

  let mut addresses = extract_call_stack_from_metrickit_frame(root_frame)?;

  // MetricKit always has an extra frame at the root of the call stack
  // which is not captured by in-process crash reporters. Remove it so
  // that we can compare the stack traces.
  if !addresses.is_empty() {
    addresses.pop();
  }

  if addresses.is_empty() {
    Ok(None)
  } else {
    Ok(Some(addresses))
  }
}


/// Recursively extracts addresses from a `MetricKit` frame and its subFrames
fn extract_call_stack_from_metrickit_frame(
  metrickit_frame: &AHashMap<String, Value>,
) -> anyhow::Result<Vec<u64>> {
  let mut addresses = Vec::new();

  let Some(address_value) = metrickit_frame.get("address") else {
    return Err(anyhow::anyhow!("MetricKit frame missing 'address' field"));
  };

  let address = parse_address_value(address_value)?;
  addresses.push(address);

  if let Some(Value::Array(sub_frames)) = metrickit_frame.get("subFrames") {
    for sub_frame in sub_frames {
      if let Value::Object(sub_frame) = sub_frame {
        let mut sub_addresses = extract_call_stack_from_metrickit_frame(sub_frame)?;
        addresses.append(&mut sub_addresses);
      }
    }
  }

  Ok(addresses)
}

/// Finds a named thread whose addresses match the given call stack addresses,
/// but only if the thread hasn't exceeded its usage count limit
fn find_matching_named_thread_with_limit<'a>(
  call_stack: &[u64],
  named_threads: &'a [NamedThread],
  usage_counts: &HashMap<String, usize>,
) -> Option<&'a NamedThread> {
  for named_thread in named_threads {
    // Check if this thread has already been used up to its count limit
    let current_usage = usage_counts.get(&named_thread.name).unwrap_or(&0);
    if *current_usage >= named_thread.count {
      continue;
    }

    if call_stacks_match(call_stack, &named_thread.call_stack) {
      return Some(named_thread);
    }
  }
  None
}

fn call_stacks_match(call_stack_a: &[u64], call_stack_b: &[u64]) -> bool {
  !call_stack_a.is_empty() && call_stack_a == call_stack_b
}
