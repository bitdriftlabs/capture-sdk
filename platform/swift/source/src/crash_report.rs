// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

// use bd_bonjson::decoder::Value;
use objc::runtime::Object;
use crate::{conversion::{objc_value_to_rust, rust_value_to_objc}, ffi::nsstring_into_string};
use std::fs;
use std::path::Path;
use bd_bonjson::decoder::{decode_value, Value};
use anyhow;

/// Loads and parses a BonJSON document from the specified file path.
/// 
/// # Arguments
/// * `path` - The file path to the BonJSON document
/// 
/// # Returns
/// * `Ok(Value)` - The (possibly partially) decoded value
/// * `Err(anyhow::Error)` - If the file cannot be read or parsed
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

#[no_mangle]
extern "C" fn enhance_metrickit_diagnostic_report(metrickit_report_ptr: *const Object, kscrash_report_path: *const Object) -> *const Object {
    match enhance_metrickit_diagnostic_report_impl(metrickit_report_ptr, kscrash_report_path) {
        Ok(result_ptr) => result_ptr,
        Err(e) => {
            println!("Error in enhance_metrickit_diagnostic_report: {}", e);
            metrickit_report_ptr // Return original on error
        }
    }
}

fn enhance_metrickit_diagnostic_report_impl(metrickit_report_ptr: *const Object, kscrash_report_path: *const Object) -> anyhow::Result<*const Object> {
    unsafe {
        let path_str = match nsstring_into_string(kscrash_report_path) {
            Ok(s) => s,
            Err(e) => {
                return Err(anyhow::anyhow!("Failed to convert kscrash_report_path to string: {}", e));
            }
        };

        let metrickit_report = match objc_value_to_rust(metrickit_report_ptr) {
            Ok(v) => v,
            Err(e) => {
                return Err(anyhow::anyhow!("Failed to convert metrickit_report_ptr to Rust Value: {:?}", e));
            },
        };

        let kscrash_report = match load_bonjson_document(path_str) {
            Ok(value) => value,
            Err(e) => {
                return Err(anyhow::anyhow!("Failed to load kscrash_report: {}", e));
            },
        };

        let enhanced_report = match enhance_report(metrickit_report, kscrash_report) {
            Ok(report) => report,
            Err(e) => {
                return Err(anyhow::anyhow!("Failed to enhance report: {}", e));
            }
        };

        match rust_value_to_objc(&enhanced_report) {
            Ok(strong_ptr) => Ok(*strong_ptr),
            Err(e) => {
                Err(anyhow::anyhow!("Failed to convert enhanced_report to Objective-C: {:?}", e))
            },
        }
    }
}

fn diagnostic_metadata_matches(a: &Value, b: &Value) -> bool {
    match (a, b) {
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

fn enhance_report(metrickit_report: Value, kscrash_report: Value) -> anyhow::Result<Value> {
    if !diagnostic_metadata_matches(&metrickit_report, &kscrash_report) {
        println!("diagnostic_metadata_matches: false");
        return Ok(metrickit_report);
    }
    
    // Test the named_threads_from_kscrash_report function
    let named_threads = named_threads_from_kscrash_report(&kscrash_report);
    println!("### Found {} named threads:", named_threads.len());
    for thread in &named_threads {
        println!("###   - {}: {} addresses, count: {}", thread.name, thread.stack_addresses.len(), thread.count);
    }
    
    println!("### ALL OK (Placeholder)");
    
    // Inject thread names from KSCrash into MetricKit report
    let enhanced_metrickit = inject_thread_names_into_metrickit(metrickit_report, &named_threads);
    println!("### Thread name injection complete");
    
    Ok(enhanced_metrickit)
}

#[allow(dead_code)]
fn named_threads_from_kscrash_report(kscrash_report: &Value) -> Vec<NamedThread> {
    let mut named_threads: Vec<NamedThread> = Vec::new();
    
    println!("### Starting named_threads_from_kscrash_report");
    
    if let Value::Object(report_obj) = kscrash_report {
        println!("### KSCrash report keys: {:?}", report_obj.keys().collect::<Vec<_>>());
        
        if let Some(Value::Array(threads)) = report_obj.get("threads") {
            println!("### Found {} threads in KSCrash report", threads.len());
            
            for (thread_index, thread) in threads.iter().enumerate() {
                println!("### Processing thread {}", thread_index);
                
                if let Value::Object(thread_obj) = thread {
                    println!("### Thread {} keys: {:?}", thread_index, thread_obj.keys().collect::<Vec<_>>());
                    
                    // Check if this thread has a name
                    if let Some(Value::String(thread_name)) = thread_obj.get("name") {
                        // Extract stack addresses from backtrace.contents
                        let mut stack_addresses = Vec::new();
                        
                        println!("### Processing thread: {}", thread_name);
                        
                        if let Some(Value::Object(backtrace_obj)) = thread_obj.get("backtrace") {
                            println!("### Found backtrace object");
                            if let Some(Value::Array(contents)) = backtrace_obj.get("contents") {
                                println!("### Found {} backtrace contents", contents.len());
                                for (i, frame) in contents.iter().enumerate() {
                                    if let Value::Object(frame_obj) = frame {
                                        println!("### Processing frame {} keys: {:?}", i, frame_obj.keys().collect::<Vec<_>>());
                                        
                                        if let Some(address_value) = frame_obj.get("address") {
                                            println!("### Found address field: {:?}", address_value);
                                            match address_value {
                                                Value::Unsigned(address) => {
                                                    stack_addresses.push(*address);
                                                    println!("### Added unsigned address: 0x{:x}", address);
                                                },
                                                Value::Signed(address) => {
                                                    if *address >= 0 {
                                                        stack_addresses.push(*address as u64);
                                                        println!("### Added signed address: 0x{:x}", address);
                                                    }
                                                },
                                                _ => {
                                                    println!("### Address is neither unsigned nor signed: {:?}", address_value);
                                                }
                                            }
                                        } else {
                                            println!("### No address field found in frame {}", i);
                                        }
                                    } else {
                                        println!("### Frame {} is not an object: {:?}", i, frame);
                                    }
                                }
                            } else {
                                println!("### No contents array found in backtrace");
                                if let Some(contents_value) = backtrace_obj.get("contents") {
                                    println!("### contents value: {:?}", contents_value);
                                }
                            }
                        } else {
                            println!("### No backtrace object found for thread {}", thread_name);
                        }
                        
                        println!("### Thread {} has {} addresses", thread_name, stack_addresses.len());
                        
                        // Check if we already have a thread with this name
                        if let Some(existing_thread) = named_threads.iter_mut().find(|t| t.name == *thread_name) {
                            // Increment count for existing thread
                            existing_thread.count += 1;
                        } else {
                            // Create new NamedThread entry
                            named_threads.push(NamedThread {
                                name: thread_name.clone(),
                                stack_addresses,
                                count: 1,
                            });
                        }
                    } else {
                        println!("### Thread {} has no name field", thread_index);
                    }
                } else {
                    println!("### Thread {} is not an object: {:?}", thread_index, thread);
                }
            }
        } else {
            println!("### No threads array found in KSCrash report");
            if let Some(threads_value) = report_obj.get("threads") {
                println!("### threads value: {:?}", threads_value);
            }
        }
    } else {
        println!("### KSCrash report is not an object: {:?}", kscrash_report);
    }
    
    println!("### Found {} named threads total", named_threads.len());
    
    named_threads
}

/// Injects thread names from KSCrash report into MetricKit report call stacks
/// where the addresses match between the two reports.
#[allow(dead_code)]
fn inject_thread_names_into_metrickit(mut metrickit_report: Value, named_threads: &[NamedThread]) -> Value {
    if let Value::Object(ref mut report_obj) = metrickit_report {
        if let Some(Value::Object(ref mut call_stack_tree)) = report_obj.get_mut("callStackTree") {
            if let Some(Value::Array(ref mut call_stacks)) = call_stack_tree.get_mut("callStacks") {
                for call_stack in call_stacks.iter_mut() {
                    if let Value::Object(ref mut call_stack_obj) = call_stack {
                        // Extract addresses from this call stack
                        let addresses = extract_addresses_from_metrickit_call_stack(call_stack_obj);
                        
                        // Find matching named thread
                        if let Some(matching_thread) = find_matching_thread(&addresses, named_threads) {
                            // Inject the thread name next to callStackRootFrames
                            call_stack_obj.insert("name".to_string(), Value::String(matching_thread.name.clone()));
                            println!("### Injected thread name '{}' for call stack with {} addresses", matching_thread.name, addresses.len());
                        } else if !addresses.is_empty() {
                            println!("### No matching thread found for call stack with {} addresses", addresses.len());
                        }
                    }
                }
            }
        }
    }
    metrickit_report
}

/// Extracts all addresses from a MetricKit call stack by traversing the subFrames tree
fn extract_addresses_from_metrickit_call_stack(call_stack_obj: &std::collections::HashMap<String, Value>) -> Vec<u64> {
    let mut addresses = Vec::new();
    
    println!("### Call stack keys: {:?}", call_stack_obj.keys().collect::<Vec<_>>());
    
    if let Some(Value::Array(root_frames)) = call_stack_obj.get("callStackRootFrames") {
        println!("### Found {} root frames", root_frames.len());
        for (i, root_frame) in root_frames.iter().enumerate() {
            println!("### Processing root frame {}", i);
            if let Value::Object(frame_obj) = root_frame {
                extract_addresses_from_frame(frame_obj, &mut addresses);
            } else {
                println!("### Root frame {} is not an object: {:?}", i, root_frame);
            }
        }
    } else {
        println!("### No callStackRootFrames found or it's not an array");
        if let Some(root_frames_value) = call_stack_obj.get("callStackRootFrames") {
            println!("### callStackRootFrames value: {:?}", root_frames_value);
        }
    }
    
    // Remove the last entry from addresses
    if !addresses.is_empty() {
        addresses.pop();
    }

    println!("### Extracted {} addresses from MetricKit call stack", addresses.len());
    for addr in addresses.iter() {
        println!("### Extracted address: {:?}", addr);
    }

    addresses
}


/// Recursively extracts addresses from a MetricKit frame and its subFrames
fn extract_addresses_from_frame(frame_obj: &std::collections::HashMap<String, Value>, addresses: &mut Vec<u64>) {
    // Debug: Print all keys in the frame
    println!("### Frame keys: {:?}", frame_obj.keys().collect::<Vec<_>>());
    
    // Extract address from current frame - try both Unsigned and Signed
    if let Some(address_value) = frame_obj.get("address") {
        println!("### Found address field: {:?}", address_value);
        match address_value {
            Value::Unsigned(address) => {
                addresses.push(*address);
                println!("### Added unsigned address: 0x{:x}", address);
            },
            Value::Signed(address) => {
                if *address >= 0 {
                    addresses.push(*address as u64);
                    println!("### Added signed address: 0x{:x}", address);
                }
            },
            _ => {
                println!("### Address is neither unsigned nor signed: {:?}", address_value);
            }
        }
    } else {
        println!("### No address field found in frame");
    }
    
    // Recursively process subFrames
    if let Some(Value::Array(sub_frames)) = frame_obj.get("subFrames") {
        println!("### Found {} subFrames", sub_frames.len());
        for sub_frame in sub_frames {
            if let Value::Object(sub_frame_obj) = sub_frame {
                extract_addresses_from_frame(sub_frame_obj, addresses);
            }
        }
    } else {
        println!("### No subFrames found");
    }
}

/// Finds a named thread whose addresses match the given call stack addresses
fn find_matching_thread<'a>(call_stack_addresses: &[u64], named_threads: &'a [NamedThread]) -> Option<&'a NamedThread> {
    for named_thread in named_threads {
        println!("### Checking thread: {} with {} entries", named_thread.name, named_thread.stack_addresses.len());
        for addr in named_thread.stack_addresses.iter() {
            println!("###### address: {:?}", addr);
        }
        println!("#### VS");
        for addr in call_stack_addresses.iter() {
            println!("###### address: {:?}", addr);
        }
        if addresses_match(call_stack_addresses, &named_thread.stack_addresses) {
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

#[allow(dead_code)]
struct NamedThread {
    name: String,
    stack_addresses: Vec<u64>,
    count: usize,
}
