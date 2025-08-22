// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

use crate::conversion::{objc_value_to_rust, rust_value_to_objc};
use crate::ffi::nsstring_into_string;
use anyhow;
use bd_bonjson::decoder::{decode_value, Value};
use objc::runtime::Object;
use std::collections::HashMap;
use std::fs;
use std::path::Path;
use std::sync::Mutex;

struct NamedThread {
  name: String,
  call_stack: Vec<u64>,
  count: usize,
}

// Global cache for the most recently loaded KSCrash report.
// This allows us to safely delete the report file so that it's not picked up next launch by
// mistake.
static CACHED_KSCRASH_REPORT: Mutex<Option<HashMap<String, Value>>> = Mutex::new(None);

// API

#[no_mangle]
extern "C" fn capture_cache_kscrash_report(kscrash_report_path_ptr: *const Object) -> bool {
  match capture_cache_kscrash_report_impl(kscrash_report_path_ptr) {
    Ok(Some(result)) => result,
    Ok(None) => false, // Return false when caching returns None
    Err(e) => {
      log::error!("Failed to cache KSCrash report: {e}");
      false // Return false on error
    },
  }
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
) -> anyhow::Result<Option<bool>> {
  let kscrash_report_path = unsafe {
    nsstring_into_string(kscrash_report_path_ptr)
      .map_err(|e| anyhow::anyhow!("Failed to convert KSCrash report path to string: {e}"))?
  };

  if !Path::new(&kscrash_report_path).exists() {
    log::debug!(
      "capture_cache_kscrash_report: App has not produced a crash report yet, so returning success"
    );
    return Ok(Some(true));
  }

  let hashmap = match load_bonjson_document(&kscrash_report_path)? {
    Value::Object(hashmap) => hashmap,
    _ => {
      return Err(anyhow::anyhow!(
        "KSCrash report is not a valid object/hashmap"
      ))
    },
  };

  CACHED_KSCRASH_REPORT
    .lock()
    .map_err(|e| anyhow::anyhow!("Failed to acquire cache lock: {e}"))?
    .replace(hashmap);

  Ok(Some(true))
}

fn load_bonjson_document<P: AsRef<Path>>(path: &P) -> anyhow::Result<Value> {
  let file_contents = fs::read(path)?;

  decode_value(&file_contents).or_else(|e| match e.partial_value {
    Value::None => Err(anyhow::anyhow!("Failed to decode BONJSON: {:?}", e)),
    value => Ok(value),
  })
}

fn enhance_metrickit_diagnostic_report_impl(
  metrickit_report_ptr: *const Object,
) -> anyhow::Result<Option<*const Object>> {
  let metrickit_report = unsafe {
    let value = objc_value_to_rust(metrickit_report_ptr)
      .map_err(|e| anyhow::anyhow!("Failed to convert metrickit_report_ptr to Rust Value: {e}"))?;

    match value {
      Value::Object(hashmap) => hashmap,
      _ => {
        return Err(anyhow::anyhow!(
          "metrickit_report is not a valid object/hashmap"
        ))
      },
    }
  };

  let kscrash_report = CACHED_KSCRASH_REPORT
    .lock()
    .map_err(|e| anyhow::anyhow!("Failed to acquire cache lock: {e}"))?
    .as_ref()
    .ok_or_else(|| {
      anyhow::anyhow!("No KSCrash report has been cached. Call capture_cache_kscrash_report first.")
    })?
    .clone();

  let enhanced_report = match enhance_report(&metrickit_report, &kscrash_report)? {
    Some(hashmap) => Value::Object(hashmap),
    None => return Ok(None),
  };

  unsafe {
    let strong_ptr = rust_value_to_objc(&enhanced_report)
      .map_err(|e| anyhow::anyhow!("Failed to convert enhanced_report to Objective-C: {e}"))?;
    Ok(Some(*strong_ptr))
  }
}

fn enhance_report(
  metrickit_report: &HashMap<String, Value>,
  kscrash_report: &HashMap<String, Value>,
) -> anyhow::Result<Option<HashMap<String, Value>>> {
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
  report_a: &HashMap<String, Value>,
  report_b: &HashMap<String, Value>,
) -> bool {
  let a_meta = report_a.get("diagnosticMetaData");
  let b_meta = report_b.get("diagnosticMetaData");
  if a_meta.is_none() || b_meta.is_none() {
    return false;
  }
  let check_keys = ["exceptionType", "exceptionCode", "signal", "pid"];
  for key in &check_keys {
    if a_meta.as_ref().unwrap().get(key) != b_meta.as_ref().unwrap().get(key) {
      return false;
    }
  }
  true
}

fn named_threads_from_kscrash_report(
  kscrash_report: &HashMap<String, Value>,
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
  kscrash_thread: &HashMap<String, Value>,
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

    match address {
      Value::Unsigned(address) => {
        call_stack.push(*address);
      },
      Value::Signed(address) => {
        if *address >= 0 {
          #[allow(clippy::cast_sign_loss)]
          call_stack.push(*address as u64);
        } else {
          return Err(anyhow::anyhow!("Address value is negative: {}", address));
        }
      },
      _ => {
        return Err(anyhow::anyhow!(
          "Address value is not a valid number (got {:?})",
          address
        ));
      },
    }
  }

  if call_stack.is_empty() {
    Ok(None)
  } else {
    Ok(Some(call_stack))
  }
}

/// Injects thread names from a `KSCrash` report into a `MetricKit` report where their thread call
/// stacks match.
fn inject_thread_names_into_metrickit(
  mut metrickit_report: HashMap<String, Value>,
  named_threads: &[NamedThread],
) -> anyhow::Result<HashMap<String, Value>> {
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
  thread: &HashMap<String, Value>,
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
  metrickit_frame: &HashMap<String, Value>,
) -> anyhow::Result<Vec<u64>> {
  let mut addresses = Vec::new();

  let Some(address_value) = metrickit_frame.get("address") else {
    return Err(anyhow::anyhow!("MetricKit frame missing 'address' field"));
  };

  match address_value {
    Value::Unsigned(address) => {
      addresses.push(*address);
    },
    Value::Signed(address) => {
      if *address >= 0 {
        #[allow(clippy::cast_sign_loss)]
        addresses.push(*address as u64);
      } else {
        return Err(anyhow::anyhow!("Address value is negative: {}", address));
      }
    },
    _ => {
      return Err(anyhow::anyhow!(
        "Address value is not a valid number (got {:?})",
        address_value
      ));
    },
  }

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
  if call_stack_a.len() != call_stack_b.len() || call_stack_a.is_empty() {
    return false;
  }
  call_stack_a == call_stack_b
}
