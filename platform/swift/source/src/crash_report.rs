// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

use objc::runtime::Object;
use crate::{conversion::{objc_value_to_rust, rust_value_to_objc}, ffi::nsstring_into_string};
use std::fs;
use std::path::Path;
use bd_bonjson::decoder::{decode_value, Value};
use anyhow;

#[no_mangle]
extern "C" fn enhance_metrickit_diagnostic_report(metrickit_report_ptr: *const Object, kscrash_report_path: *const Object) -> *const Object {
    match enhance_metrickit_diagnostic_report_impl(metrickit_report_ptr, kscrash_report_path) {
        Ok(result_ptr) => result_ptr,
        Err(e) => {
            log::error!("Failed to enhance MetricKit report: {e}");
            metrickit_report_ptr // Return original on error
        }
    }
}

fn enhance_metrickit_diagnostic_report_impl(metrickit_report_ptr: *const Object, kscrash_report_path: *const Object) -> anyhow::Result<*const Object> {
    unsafe {
        let path_str = match nsstring_into_string(kscrash_report_path) {
            Ok(s) => s,
            Err(e) => {
                return Err(anyhow::anyhow!("Failed to convert kscrash_report_path to string: {e}"));
            }
        };

        let metrickit_report = match objc_value_to_rust(metrickit_report_ptr) {
            Ok(v) => {
                if matches!(v, Value::Object(_)) {
                    v
                } else {
                    return Err(anyhow::anyhow!("MetricKit report is not a valid object/hashmap"));
                }
            },
            Err(e) => {
                return Err(anyhow::anyhow!("Failed to convert metrickit_report_ptr to Rust Value: {e}"));
            },
        };

        let kscrash_report = match load_bonjson_document(path_str) {
            Ok(v) => {
                if matches!(v, Value::Object(_)) {
                    v
                } else {
                    return Err(anyhow::anyhow!("KSCrash report is not a valid object/hashmap"));
                }
            },
            Err(e) => {
                return Err(anyhow::anyhow!("Failed to load kscrash_report: {e}"));
            },
        };

        let enhanced_report = match enhance_report(metrickit_report, kscrash_report) {
            Ok(report) => report,
            Err(e) => {
                return Err(anyhow::anyhow!("Failed to enhance report: {e}"));
            }
        };

        match rust_value_to_objc(&enhanced_report) {
            Ok(strong_ptr) => Ok(*strong_ptr),
            Err(e) => {
                Err(anyhow::anyhow!("Failed to convert enhanced_report to Objective-C: {e}"))
            },
        }
    }
}

fn load_bonjson_document<P: AsRef<Path>>(path: P) -> anyhow::Result<Value> {
    let file_contents = fs::read(path)?;
    let decode_result = decode_value(&file_contents);

    match decode_result {
        Ok(value) => Ok(value),
        Err(e) => {
            match e.partial_value {
                Value::None => {
                    return Err(anyhow::anyhow!("Failed to decode BonJSON: {:?}", e));
                }
                value => { Ok(value) }
            }
        }
    }
}

fn enhance_report(metrickit_report: Value, kscrash_report: Value) -> anyhow::Result<Value> {
    if !diagnostic_metadata_matches_in_reports(&metrickit_report, &kscrash_report) {
        return Ok(metrickit_report);
    }
    
    let Some(named_threads) = named_threads_from_kscrash_report(&kscrash_report) else {
        return Ok(metrickit_report);
    };
    
    let enhanced_metrickit = inject_thread_names_into_metrickit(metrickit_report, &named_threads);
    Ok(enhanced_metrickit)
}

fn diagnostic_metadata_matches_in_reports(report_a: &Value, report_b: &Value) -> bool {
    match (report_a, report_b) {
        (Value::Object(a_obj), Value::Object(b_obj)) => {
            let a_meta = a_obj.get("diagnosticMetaData");
            let b_meta = b_obj.get("diagnosticMetaData");
            if a_meta.is_none() || b_meta.is_none() {
                return false;
            }
            let check_keys = [
                "exceptionType",
                "exceptionCode",
                "signal",
                "pid",
            ];
            for key in check_keys.iter() {
                if a_meta.as_ref().unwrap().get(key) != b_meta.as_ref().unwrap().get(key) {
                    return false;
                }
            }
            true
        }
        _ => false,
    }
}

fn named_threads_from_kscrash_report(kscrash_report: &Value) -> Option<Vec<NamedThread>> {
    let mut named_threads: Vec<NamedThread> = Vec::new();

    let Some(Value::Array(threads)) = kscrash_report.get("threads") else {
        return None;
    };
    
    for thread in threads {
        let Value::Object(thread_obj) = thread else {
            continue;
        };
        
        let Some(Value::String(thread_name)) = thread_obj.get("name") else {
            continue;
        };
        
        let stack_addresses = extract_stack_addresses_from_thread(thread_obj);
        
        // Check if we already have a thread with this name
        if let Some(existing_thread) = named_threads.iter_mut().find(|t| t.name == *thread_name) {
            // Increment count for existing thread
            existing_thread.count += 1;
        } else {
            // Create new NamedThread entry
            named_threads.push(NamedThread {
                name: thread_name.clone(),
                call_stack: stack_addresses,
                count: 1,
            });
        }
    }
    
    if named_threads.is_empty() {
        None
    } else {
        Some(named_threads)
    }
}

fn extract_stack_addresses_from_thread(thread_obj: &std::collections::HashMap<String, Value>) -> Vec<u64> {
    let mut stack_addresses = Vec::new();
    
    let Some(Value::Object(backtrace_obj)) = thread_obj.get("backtrace") else {
        return stack_addresses;
    };
    
    let Some(Value::Array(contents)) = backtrace_obj.get("contents") else {
        return stack_addresses;
    };
    
    for frame in contents {
        let Value::Object(frame_obj) = frame else {
            continue;
        };
        
        let Some(address_value) = frame_obj.get("address") else {
            continue;
        };
        
        match address_value {
            Value::Unsigned(address) => {
                stack_addresses.push(*address);
            },
            Value::Signed(address) => {
                if *address >= 0 {
                    stack_addresses.push(*address as u64);
                }
            },
            _ => {}
        }
    }
    
    stack_addresses
}

/// Injects thread names from KSCrash report into MetricKit report call stacks
/// where the addresses match between the two reports.
fn inject_thread_names_into_metrickit(mut metrickit_report: Value, named_threads: &[NamedThread]) -> Value {
    // Track how many times each named thread has been matched
    let mut usage_counts: std::collections::HashMap<String, usize> = std::collections::HashMap::new();
    
    let Value::Object(ref mut report_obj) = metrickit_report else {
        return metrickit_report;
    };
    
    let Some(Value::Object(ref mut call_stack_tree)) = report_obj.get_mut("callStackTree") else {
        return metrickit_report;
    };
    
    let Some(Value::Array(ref mut call_stacks)) = call_stack_tree.get_mut("callStacks") else {
        return metrickit_report;
    };
    
    for call_stack in call_stacks.iter_mut() {
        let Value::Object(ref mut call_stack_obj) = call_stack else {
            continue;
        };
        
        // Extract addresses from this call stack
        let addresses = extract_call_stack_from_metrickit_thread(call_stack_obj);
        
        // Find matching named thread that hasn't exceeded its count limit
        let Some(matching_thread) = find_matching_thread_with_limit(&addresses, named_threads, &usage_counts) else {
            continue;
        };
        
        let thread_name = matching_thread.name.clone();
        let current_count = usage_counts.get(&thread_name).unwrap_or(&0);
        let new_count = current_count + 1;
        
        // Update usage count
        usage_counts.insert(thread_name.clone(), new_count);
        
        // Inject the thread name next to callStackRootFrames
        call_stack_obj.insert("name".to_string(), Value::String(thread_name));
    }
    
    metrickit_report
}

fn extract_call_stack_from_metrickit_thread(thread: &std::collections::HashMap<String, Value>) -> Vec<u64> {
    let mut addresses = Vec::new();
    
    if let Some(Value::Array(root_frames)) = thread.get("callStackRootFrames") {
        for (_i, root_frame) in root_frames.iter().enumerate() {
            if let Value::Object(frame_obj) = root_frame {
                extract_call_stack_from_metrickit_frame(frame_obj, &mut addresses);
            }
        }
    }
    
    // MetricKit always has an extra frame at the root of the call stack,
    // which is not part of the actual call stack, so remove it.
    if !addresses.is_empty() {
        addresses.pop();
    }

    addresses
}


/// Recursively extracts addresses from a MetricKit frame and its subFrames
fn extract_call_stack_from_metrickit_frame(frame: &std::collections::HashMap<String, Value>, addresses: &mut Vec<u64>) {
    // Extract address from current frame - try both Unsigned and Signed
    if let Some(address_value) = frame.get("address") {
        match address_value {
            Value::Unsigned(address) => {
                addresses.push(*address);
            },
            Value::Signed(address) => {
                if *address >= 0 {
                    addresses.push(*address as u64);
                }
            },
            _ => {}
        }
    }
    
    // Recursively process subFrames
    if let Some(Value::Array(sub_frames)) = frame.get("subFrames") {
        for sub_frame in sub_frames {
            if let Value::Object(sub_frame_obj) = sub_frame {
                extract_call_stack_from_metrickit_frame(sub_frame_obj, addresses);
            }
        }
    }
}

/// Finds a named thread whose addresses match the given call stack addresses,
/// but only if the thread hasn't exceeded its usage count limit
fn find_matching_thread_with_limit<'a>(
    call_stack_addresses: &[u64], 
    named_threads: &'a [NamedThread],
    usage_counts: &std::collections::HashMap<String, usize>
) -> Option<&'a NamedThread> {
    for named_thread in named_threads {
        // Check if this thread has already been used up to its count limit
        let current_usage = usage_counts.get(&named_thread.name).unwrap_or(&0);
        if *current_usage >= named_thread.count {
            continue;
        }
        
        if addresses_match(call_stack_addresses, &named_thread.call_stack) {
            return Some(named_thread);
        }
    }
    None
}

/// Checks if two address sequences are an exact match
fn addresses_match(metrickit_addresses: &[u64], kscrash_addresses: &[u64]) -> bool {
    if metrickit_addresses.len() != kscrash_addresses.len() {
        return false;
    }
    
    if metrickit_addresses.is_empty() {
        return false;
    }
    
    // Check for exact match - all addresses must be identical in the same order
    metrickit_addresses == kscrash_addresses
}

struct NamedThread {
    name: String,
    call_stack: Vec<u64>,
    count: usize,
}
