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
use std::fs;
use std::path::Path;

struct NamedThread {
  name: String,
  call_stack: Vec<u64>,
  crashed: bool,
}

#[derive(Clone, Copy, Debug, PartialEq, Eq)]
enum EnrichmentOutcome {
  Enhanced,
  NoEffect,
  NotAttempted,
  Error,
}

impl EnrichmentOutcome {
  const fn as_str(self) -> &'static str {
    match self {
      Self::Enhanced => "enhanced",
      Self::NoEffect => "no_effect",
      Self::NotAttempted => "not_attempted",
      Self::Error => "error",
    }
  }
}

#[derive(Clone, Copy, Debug, PartialEq, Eq)]
enum EnrichmentReason {
  None,
  MetadataMismatch,
  NoNamedKSCrashThreads,
  NoMatchFound,
  MissingRequiredData,
  InternalError,
}

impl EnrichmentReason {
  const fn as_str(self) -> &'static str {
    match self {
      Self::None => "none",
      Self::MetadataMismatch => "metadata_mismatch",
      Self::NoNamedKSCrashThreads => "no_named_kscrash_threads",
      Self::NoMatchFound => "no_match_found",
      Self::MissingRequiredData => "missing_required_data",
      Self::InternalError => "internal_error",
    }
  }
}

#[derive(Clone, Copy, Debug, PartialEq, Eq)]
struct EnrichmentSummary {
  outcome: EnrichmentOutcome,
  reason: EnrichmentReason,
}

impl EnrichmentSummary {
  const fn new(outcome: EnrichmentOutcome, reason: EnrichmentReason) -> Self {
    Self { outcome, reason }
  }

  fn to_value(self) -> Value {
    Value::Object(AHashMap::from([
      (
        "outcome".to_string(),
        Value::String(self.outcome.as_str().to_string()),
      ),
      (
        "reason".to_string(),
        Value::String(self.reason.as_str().to_string()),
      ),
    ]))
  }
}

struct EnhancedReportResult {
  report: Option<AHashMap<String, Value>>,
  summary: EnrichmentSummary,
}

struct EnhancedReportBridgeResult {
  report_ptr: Option<*const Object>,
  summary: EnrichmentSummary,
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

const BASE_THREAD_MATCH_MIN_FRAMES: usize = 4;

#[derive(Clone, Copy, Debug, Default, PartialEq, Eq)]
struct StackOverlap {
  len: usize,
  a_start: usize,
  b_start: usize,
  bottom_distance: usize,
}

impl StackOverlap {
  const fn is_better_than(self, other: Self) -> bool {
    if self.len != other.len {
      return self.len > other.len;
    }

    self.bottom_distance < other.bottom_distance
  }
}

fn best_contiguous_overlap(call_stack_a: &[u64], call_stack_b: &[u64]) -> StackOverlap {
  let mut best_overlap = StackOverlap::default();
  let reversed_call_stack_a: Vec<_> = call_stack_a.iter().rev().copied().collect();
  let reversed_call_stack_b: Vec<_> = call_stack_b.iter().rev().copied().collect();

  for a_start in 0 .. reversed_call_stack_a.len() {
    for b_start in 0 .. reversed_call_stack_b.len() {
      let mut overlap_len = 0;

      while a_start + overlap_len < reversed_call_stack_a.len()
        && b_start + overlap_len < reversed_call_stack_b.len()
        && reversed_call_stack_a[a_start + overlap_len]
          == reversed_call_stack_b[b_start + overlap_len]
      {
        overlap_len += 1;
      }

      if overlap_len == 0 {
        continue;
      }

      let original_a_start = call_stack_a.len() - (a_start + overlap_len);
      let original_b_start = call_stack_b.len() - (b_start + overlap_len);
      let overlap = StackOverlap {
        len: overlap_len,
        a_start: original_a_start,
        b_start: original_b_start,
        bottom_distance: a_start + b_start,
      };

      if overlap.is_better_than(best_overlap) {
        best_overlap = overlap;
      }
    }
  }

  best_overlap
}

#[derive(Clone, Copy, Debug, PartialEq, Eq)]
struct StackPrefixMatch {
  exact: bool,
  matching_frames: usize,
  shorter_len: usize,
  len_delta: usize,
}

impl StackPrefixMatch {
  const fn viable(self) -> bool {
    self.exact || (self.matching_frames >= BASE_THREAD_MATCH_MIN_FRAMES && self.shorter_len > 0)
  }

  const fn is_better_than(self, other: Self) -> bool {
    if self.exact != other.exact {
      return self.exact;
    }

    if self.matching_frames != other.matching_frames {
      return self.matching_frames > other.matching_frames;
    }

    self.len_delta < other.len_delta
  }

  const fn ties_with(self, other: Self) -> bool {
    self.exact == other.exact
      && self.matching_frames == other.matching_frames
      && self.len_delta == other.len_delta
  }
}

fn compare_call_stacks_from_base(call_stack_a: &[u64], call_stack_b: &[u64]) -> StackPrefixMatch {
  let overlap = best_contiguous_overlap(call_stack_a, call_stack_b);
  StackPrefixMatch {
    exact: !call_stack_a.is_empty() && call_stack_a == call_stack_b,
    matching_frames: overlap.len,
    shorter_len: call_stack_a.len().min(call_stack_b.len()),
    len_delta: call_stack_a.len().abs_diff(call_stack_b.len()),
  }
}

fn is_metrickit_crash_thread(thread: &AHashMap<String, Value>) -> bool {
  match thread.get("threadAttributed") {
    Some(Value::Unsigned(value)) => *value != 0,
    Some(Value::Signed(value)) => *value != 0,
    _ => false,
  }
}

fn crashed_candidate_indices(
  named_threads: &[NamedThread],
  used_named_threads: &[bool],
) -> Vec<usize> {
  named_threads
    .iter()
    .enumerate()
    .filter_map(|(index, thread)| (!used_named_threads[index] && thread.crashed).then_some(index))
    .collect()
}

fn metrickit_crash_thread_count(threads: &[Value]) -> usize {
  threads
    .iter()
    .filter_map(|thread| {
      let Value::Object(thread) = thread else {
        return None;
      };

      is_metrickit_crash_thread(thread).then_some(())
    })
    .count()
}

fn equivalent_non_crash_candidates(
  metrickit_thread: &AHashMap<String, Value>,
  best_thread: &NamedThread,
  candidate_thread: &NamedThread,
) -> bool {
  !is_metrickit_crash_thread(metrickit_thread)
    && best_thread.call_stack == candidate_thread.call_stack
}

fn should_mark_ambiguous_tie(
  metrickit_thread: &AHashMap<String, Value>,
  best_thread: &NamedThread,
  candidate_thread: &NamedThread,
) -> bool {
  best_thread.name != candidate_thread.name
    && !equivalent_non_crash_candidates(metrickit_thread, best_thread, candidate_thread)
}

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
  use_stack_overlap_matching: bool,
  summary_out: *mut *const Object,
) -> *const Object {
  let result =
    enhance_metrickit_diagnostic_report_impl(metrickit_report_ptr, use_stack_overlap_matching)
      .inspect_err(|e| log::error!("Failed to enhance MetricKit report: {e}"))
      .unwrap_or_else(|_| EnhancedReportBridgeResult {
        report_ptr: None,
        summary: EnrichmentSummary::new(EnrichmentOutcome::Error, EnrichmentReason::InternalError),
      });

  write_summary_bridge_out(summary_out, result.summary);
  result.report_ptr.unwrap_or(metrickit_report_ptr)
}

#[no_mangle]
extern "C" fn capture_cached_kscrash_timestamp() -> u64 {
  with_handle_unexpected_or(
    || -> anyhow::Result<u64> { cached_kscrash_timestamp_impl() },
    0,
    "cached kscrash timestamp",
  )
}

// Implementation

fn capture_cache_kscrash_report_impl(
  kscrash_report_path_ptr: *const Object,
) -> anyhow::Result<CacheResult> {
  let report_path = unsafe { nsstring_into_string(kscrash_report_path_ptr) }
    .map_err(|e| anyhow::anyhow!("Failed to convert KSCrash report path to string: {e}"))?;
  parse_cached_report(report_path)
}

fn parse_cached_report(report_path: String) -> anyhow::Result<CacheResult> {
  if !Path::new(&report_path).exists() {
    return Ok(CacheResult::ReportDoesNotExist);
  }

  let file_contents = fs::read(&report_path)?;

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
  use_stack_overlap_matching: bool,
) -> anyhow::Result<EnhancedReportBridgeResult> {
  let value = unsafe { objc_value_to_rust(metrickit_report_ptr) }
    .map_err(|e| anyhow::anyhow!("Failed to convert metrickit_report_ptr to Rust Value: {e}"))?;

  let Value::Object(metrickit_report) = value else {
    return Err(anyhow::anyhow!(
      "metrickit_report is not a valid object/hashmap"
    ));
  };

  let Some(kscrash_report) = CACHED_KSCRASH_REPORT.lock().as_ref().cloned() else {
    return Ok(EnhancedReportBridgeResult {
      report_ptr: None,
      summary: EnrichmentSummary::new(
        EnrichmentOutcome::NotAttempted,
        EnrichmentReason::MissingRequiredData,
      ),
    });
  };

  let result = enhance_report_with_summary(
    &metrickit_report,
    &kscrash_report,
    use_stack_overlap_matching,
  )?;
  let report_ptr = match result.report {
    Some(enhanced_hashmap) => {
      let enhanced_report = Value::Object(enhanced_hashmap);
      let strong_ptr = unsafe { rust_value_to_objc(&enhanced_report) }
        .map_err(|e| anyhow::anyhow!("Failed to convert enhanced_report to Objective-C: {e}"))?;
      Some(*strong_ptr as *const Object)
    },
    None => None,
  };

  Ok(EnhancedReportBridgeResult {
    report_ptr,
    summary: result.summary,
  })
}

fn cached_kscrash_timestamp_impl() -> anyhow::Result<u64> {
  let Some(kscrash_report) = CACHED_KSCRASH_REPORT.lock().as_ref().cloned() else {
    return Ok(0);
  };

  let diagnostic = kscrash_report
    .get("diagnosticMetaData")
    .and_then(|value| value.as_object().ok())
    .ok_or_else(|| anyhow::anyhow!("KSCrash report missing diagnostic metadata"))?;

  let seconds = diagnostic
    .get("crashedAt")
    .and_then(value_as_u64)
    .ok_or_else(|| anyhow::anyhow!("KSCrash report missing crash timestamp"))?;

  Ok(seconds)
}

fn enhance_report_with_summary(
  metrickit_report: &AHashMap<String, Value>,
  kscrash_report: &AHashMap<String, Value>,
  use_stack_overlap_matching: bool,
) -> anyhow::Result<EnhancedReportResult> {
  if !diagnostic_metadata_matches_in_reports(metrickit_report, kscrash_report) {
    return Ok(EnhancedReportResult {
      report: None,
      summary: EnrichmentSummary::new(
        EnrichmentOutcome::NoEffect,
        EnrichmentReason::MetadataMismatch,
      ),
    });
  }

  if use_stack_overlap_matching {
    enhance_report_with_base_matching(metrickit_report, kscrash_report)
  } else {
    enhance_report_with_exact_matching(metrickit_report, kscrash_report)
  }
}

fn enhance_report_with_exact_matching(
  metrickit_report: &AHashMap<String, Value>,
  kscrash_report: &AHashMap<String, Value>,
) -> anyhow::Result<EnhancedReportResult> {
  let named_threads = exact_named_threads_from_kscrash_report(kscrash_report)?;

  let Some(named_threads) = named_threads else {
    return Ok(EnhancedReportResult {
      report: None,
      summary: EnrichmentSummary::new(
        EnrichmentOutcome::NoEffect,
        EnrichmentReason::NoNamedKSCrashThreads,
      ),
    });
  };

  let named_before = count_named_metrickit_threads(metrickit_report)?;
  let enhanced_metrickit =
    inject_thread_names_into_metrickit_exact(metrickit_report.clone(), &named_threads)?;
  let named_after = count_named_metrickit_threads(&enhanced_metrickit)?;
  let (outcome, reason) = if named_after > named_before {
    (EnrichmentOutcome::Enhanced, EnrichmentReason::None)
  } else {
    (EnrichmentOutcome::NoEffect, EnrichmentReason::NoMatchFound)
  };

  Ok(EnhancedReportResult {
    report: Some(enhanced_metrickit),
    summary: EnrichmentSummary::new(outcome, reason),
  })
}

fn enhance_report_with_base_matching(
  metrickit_report: &AHashMap<String, Value>,
  kscrash_report: &AHashMap<String, Value>,
) -> anyhow::Result<EnhancedReportResult> {
  let named_threads = named_threads_from_kscrash_report(kscrash_report)?;

  let Some(named_threads) = named_threads else {
    return Ok(EnhancedReportResult {
      report: None,
      summary: EnrichmentSummary::new(
        EnrichmentOutcome::NoEffect,
        EnrichmentReason::NoNamedKSCrashThreads,
      ),
    });
  };

  let named_before = count_named_metrickit_threads(metrickit_report)?;
  let enhanced_metrickit =
    inject_thread_names_into_metrickit_from_base(metrickit_report.clone(), &named_threads)?;
  let named_after = count_named_metrickit_threads(&enhanced_metrickit)?;
  let (outcome, reason) = if named_after > named_before {
    (EnrichmentOutcome::Enhanced, EnrichmentReason::None)
  } else {
    (EnrichmentOutcome::NoEffect, EnrichmentReason::NoMatchFound)
  };

  Ok(EnhancedReportResult {
    report: Some(enhanced_metrickit),
    summary: EnrichmentSummary::new(outcome, reason),
  })
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

  // If the low level signal and the process id match, we can say with
  // fairly high confidence that both reports refer to the same crash.
  let check_keys = ["signal", "pid"];
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

    let crashed = match thread.get("crashed") {
      Some(Value::Unsigned(value)) => *value != 0,
      Some(Value::Signed(value)) => *value != 0,
      _ => false,
    };
    let call_stack = extract_call_stack_from_kcrash_thread(thread)?;

    let Some(call_stack) = call_stack else {
      continue;
    };

    let Some(Value::String(thread_name)) = thread.get("name") else {
      continue;
    };
    named_threads.push(NamedThread {
      name: thread_name.clone(),
      call_stack,
      crashed,
    });
  }

  if named_threads.is_empty() {
    Ok(None)
  } else {
    Ok(Some(named_threads))
  }
}

fn exact_named_threads_from_kscrash_report(
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

    let call_stack = extract_call_stack_from_kcrash_thread(thread)?;

    let Some(call_stack) = call_stack else {
      continue;
    };

    let Some(Value::String(thread_name)) = thread.get("name") else {
      continue;
    };
    named_threads.push(NamedThread {
      name: thread_name.clone(),
      call_stack,
      crashed: false,
    });
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

fn inject_thread_names_into_metrickit_exact(
  mut metrickit_report: AHashMap<String, Value>,
  named_threads: &[NamedThread],
) -> anyhow::Result<AHashMap<String, Value>> {
  let mut used_named_threads = vec![false; named_threads.len()];

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

    let Some(matching_thread_index) =
      find_matching_named_thread_index(&call_stack, named_threads, &used_named_threads)
    else {
      continue;
    };

    let matching_thread = &named_threads[matching_thread_index];
    used_named_threads[matching_thread_index] = true;
    thread.insert(
      "name".to_string(),
      Value::String(matching_thread.name.clone()),
    );
  }

  Ok(metrickit_report)
}

fn inject_thread_names_into_metrickit_from_base(
  mut metrickit_report: AHashMap<String, Value>,
  named_threads: &[NamedThread],
) -> anyhow::Result<AHashMap<String, Value>> {
  let threads = metrickit_threads_mut(&mut metrickit_report)?;

  let mut used_named_threads = vec![false; named_threads.len()];
  let metrickit_crash_threads = metrickit_crash_thread_count(threads);

  for thread in threads.iter_mut() {
    let Some((thread, call_stack)) = get_metrickit_thread(thread)? else {
      continue;
    };

    if try_assign_crash_thread_name(
      thread,
      named_threads,
      &mut used_named_threads,
      metrickit_crash_threads,
    ) {
      continue;
    }

    let Some(best_match_index) =
      find_best_base_thread_match(thread, &call_stack, named_threads, &used_named_threads)
    else {
      continue;
    };

    assign_thread_name(
      thread,
      named_threads,
      &mut used_named_threads,
      best_match_index,
    );
  }

  Ok(metrickit_report)
}

fn metrickit_threads_mut(
  metrickit_report: &mut AHashMap<String, Value>,
) -> anyhow::Result<&mut Vec<Value>> {
  let Some(Value::Object(ref mut call_stack_tree)) = metrickit_report.get_mut("callStackTree")
  else {
    return Err(anyhow::anyhow!(
      "MetricKit report missing 'callStackTree' object"
    ));
  };

  let Some(Value::Array(ref mut threads)) = call_stack_tree.get_mut("callStacks") else {
    return Err(anyhow::anyhow!("CallStackTree missing 'callStacks' array"));
  };

  Ok(threads)
}

fn metrickit_threads(metrickit_report: &AHashMap<String, Value>) -> anyhow::Result<&Vec<Value>> {
  let Some(Value::Object(call_stack_tree)) = metrickit_report.get("callStackTree") else {
    return Err(anyhow::anyhow!(
      "MetricKit report missing 'callStackTree' object"
    ));
  };

  let Some(Value::Array(threads)) = call_stack_tree.get("callStacks") else {
    return Err(anyhow::anyhow!("CallStackTree missing 'callStacks' array"));
  };

  Ok(threads)
}

fn count_named_metrickit_threads(
  metrickit_report: &AHashMap<String, Value>,
) -> anyhow::Result<usize> {
  Ok(
    metrickit_threads(metrickit_report)?
      .iter()
      .filter(|thread| match thread {
        Value::Object(thread) => match thread.get("name") {
          Some(Value::String(name)) => !name.is_empty(),
          _ => false,
        },
        _ => false,
      })
      .count(),
  )
}

fn get_metrickit_thread(
  thread: &mut Value,
) -> anyhow::Result<Option<(&mut AHashMap<String, Value>, Vec<u64>)>> {
  let Value::Object(ref mut thread) = thread else {
    return Err(anyhow::anyhow!("Thread is not a valid object/hashmap"));
  };

  let Ok(Some(call_stack)) = extract_call_stack_from_metrickit_thread(thread) else {
    return Ok(None);
  };

  Ok(Some((thread, call_stack)))
}

fn try_assign_crash_thread_name(
  thread: &mut AHashMap<String, Value>,
  named_threads: &[NamedThread],
  used_named_threads: &mut [bool],
  metrickit_crash_threads: usize,
) -> bool {
  if metrickit_crash_threads != 1 || !is_metrickit_crash_thread(thread) {
    return false;
  }

  let crashed_candidates = crashed_candidate_indices(named_threads, used_named_threads);
  if crashed_candidates.len() != 1 {
    return false;
  }

  assign_thread_name(
    thread,
    named_threads,
    used_named_threads,
    crashed_candidates[0],
  );

  true
}

fn find_best_base_thread_match(
  metrickit_thread: &AHashMap<String, Value>,
  call_stack: &[u64],
  named_threads: &[NamedThread],
  used_named_threads: &[bool],
) -> Option<usize> {
  let mut best_match: Option<(usize, StackPrefixMatch)> = None;
  let mut ambiguous = false;

  for (named_thread_index, named_thread) in named_threads.iter().enumerate() {
    if used_named_threads[named_thread_index] {
      continue;
    }

    let match_result = compare_call_stacks_from_base(call_stack, &named_thread.call_stack);
    if !match_result.viable() {
      continue;
    }

    match best_match {
      None => {
        best_match = Some((named_thread_index, match_result));
        ambiguous = false;
      },
      Some((best_index, best_result)) => {
        if match_result.is_better_than(best_result) {
          best_match = Some((named_thread_index, match_result));
          ambiguous = false;
          continue;
        }

        if match_result.ties_with(best_result)
          && named_thread_index != best_index
          && should_mark_ambiguous_tie(metrickit_thread, &named_threads[best_index], named_thread)
        {
          ambiguous = true;
        }
      },
    }
  }

  if ambiguous {
    None
  } else {
    best_match.map(|(best_match_index, _)| best_match_index)
  }
}

fn assign_thread_name(
  thread: &mut AHashMap<String, Value>,
  named_threads: &[NamedThread],
  used_named_threads: &mut [bool],
  matching_thread_index: usize,
) {
  used_named_threads[matching_thread_index] = true;
  thread.insert(
    "name".to_string(),
    Value::String(named_threads[matching_thread_index].name.clone()),
  );
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

fn find_matching_named_thread_index(
  call_stack: &[u64],
  named_threads: &[NamedThread],
  used_named_threads: &[bool],
) -> Option<usize> {
  named_threads
    .iter()
    .enumerate()
    .find_map(|(index, named_thread)| {
      if used_named_threads[index] {
        return None;
      }

      if call_stacks_match(call_stack, &named_thread.call_stack) {
        Some(index)
      } else {
        None
      }
    })
}

fn call_stacks_match(call_stack_a: &[u64], call_stack_b: &[u64]) -> bool {
  !call_stack_a.is_empty() && call_stack_a == call_stack_b
}

fn value_as_u64(value: &Value) -> Option<u64> {
  match value {
    Value::Unsigned(value) => Some(*value),
    Value::Signed(value) => (*value >= 0).then_some(*value as u64),
    _ => None,
  }
}

fn write_summary_bridge_out(summary_out: *mut *const Object, summary: EnrichmentSummary) {
  if summary_out.is_null() {
    return;
  }

  match unsafe { rust_value_to_objc(&summary.to_value()) } {
    Ok(summary_ptr) => unsafe {
      *summary_out = *summary_ptr;
    },
    Err(e) => {
      log::error!("Failed to convert crash enrichment summary to Objective-C: {e}");
      unsafe {
        *summary_out = std::ptr::null();
      };
    },
  }
}

#[cfg(test)]
mod tests {
  use super::*;
  use std::io::Write;

  static CACHED_KSCRASH_REPORT_TEST_LOCK: Mutex<()> = Mutex::new(());

  fn make_kscrash_thread(
    index: i64,
    name: Option<&str>,
    addresses: &[u64],
    crashed: bool,
  ) -> AHashMap<String, Value> {
    let mut thread = AHashMap::new();
    thread.insert("index".to_string(), Value::Signed(index));
    thread.insert(
      "crashed".to_string(),
      Value::Unsigned(if crashed { 1 } else { 0 }),
    );

    if let Some(name) = name {
      thread.insert("name".to_string(), Value::String(name.to_string()));
    }

    let mut backtrace = AHashMap::new();
    let contents = addresses
      .iter()
      .map(|address| {
        let mut frame = AHashMap::new();
        frame.insert("address".to_string(), Value::Unsigned(*address));
        Value::Object(frame)
      })
      .collect();
    backtrace.insert("contents".to_string(), Value::Array(contents));
    thread.insert("backtrace".to_string(), Value::Object(backtrace));

    thread
  }

  fn make_frame_chain(addresses: &[u64]) -> AHashMap<String, Value> {
    let (first, rest) = addresses
      .split_first()
      .expect("MetricKit thread needs at least one frame");

    let mut frame = AHashMap::new();
    frame.insert("address".to_string(), Value::Unsigned(*first));
    let sub_frames = if rest.is_empty() {
      Vec::new()
    } else {
      vec![Value::Object(make_frame_chain(rest))]
    };
    frame.insert("subFrames".to_string(), Value::Array(sub_frames));
    frame
  }

  fn make_metrickit_thread(
    addresses_after_root_trim: &[u64],
    thread_attributed: bool,
  ) -> AHashMap<String, Value> {
    let mut thread = AHashMap::new();
    let mut full_addresses = addresses_after_root_trim.to_vec();
    full_addresses.push(0xFFFF);
    thread.insert(
      "callStackRootFrames".to_string(),
      Value::Array(vec![Value::Object(make_frame_chain(&full_addresses))]),
    );
    thread.insert(
      "threadAttributed".to_string(),
      Value::Unsigned(if thread_attributed { 1 } else { 0 }),
    );
    thread
  }

  fn make_kscrash_report(
    signal: u64,
    pid: u64,
    threads: Vec<AHashMap<String, Value>>,
  ) -> AHashMap<String, Value> {
    let mut diagnostic_metadata = AHashMap::new();
    diagnostic_metadata.insert("signal".to_string(), Value::Unsigned(signal));
    diagnostic_metadata.insert("pid".to_string(), Value::Unsigned(pid));

    let mut report = AHashMap::new();
    report.insert(
      "diagnosticMetaData".to_string(),
      Value::Object(diagnostic_metadata),
    );
    report.insert(
      "threads".to_string(),
      Value::Array(threads.into_iter().map(Value::Object).collect()),
    );
    report
  }

  fn make_metrickit_report(
    signal: u64,
    pid: u64,
    threads: Vec<AHashMap<String, Value>>,
  ) -> AHashMap<String, Value> {
    let mut diagnostic_metadata = AHashMap::new();
    diagnostic_metadata.insert("signal".to_string(), Value::Unsigned(signal));
    diagnostic_metadata.insert("pid".to_string(), Value::Unsigned(pid));

    let mut call_stack_tree = AHashMap::new();
    call_stack_tree.insert(
      "callStacks".to_string(),
      Value::Array(threads.into_iter().map(Value::Object).collect()),
    );

    let mut report = AHashMap::new();
    report.insert(
      "diagnosticMetaData".to_string(),
      Value::Object(diagnostic_metadata),
    );
    report.insert("callStackTree".to_string(), Value::Object(call_stack_tree));
    report
  }

  fn metrickit_thread_name(report: &AHashMap<String, Value>, index: usize) -> Option<&str> {
    report
      .get("callStackTree")
      .and_then(|value| value.as_object().ok())
      .and_then(|call_stack_tree| call_stack_tree.get("callStacks"))
      .and_then(|value| value.as_array().ok())
      .and_then(|threads| threads.get(index))
      .and_then(|thread| thread.as_object().ok())
      .and_then(|thread| thread.get("name"))
      .and_then(|value| match value {
        Value::String(value) => Some(value.as_str()),
        _ => None,
      })
  }

  fn with_cached_report<T>(
    report: Option<AHashMap<String, Value>>,
    callback: impl FnOnce() -> T,
  ) -> T {
    let _test_lock = CACHED_KSCRASH_REPORT_TEST_LOCK.lock();
    *CACHED_KSCRASH_REPORT.lock() = report;

    let result = callback();

    *CACHED_KSCRASH_REPORT.lock() = None;
    result
  }

  #[test]
  fn nonexistent_report_path_test() {
    assert_eq!(
      CacheResult::ReportDoesNotExist,
      parse_cached_report("/sys/nonpath".to_string()).unwrap()
    );
  }

  #[test]
  fn empty_report_contents_test() {
    let report = tempfile::Builder::new()
      .prefix("report")
      .tempfile()
      .unwrap();
    let report_path = report.path().to_str().unwrap().to_string();
    assert_eq!(
      CacheResult::ReportDoesNotExist,
      parse_cached_report(report_path).unwrap()
    );
  }

  #[test]
  fn invalid_report_test() {
    let mut report = tempfile::Builder::new()
      .prefix("report")
      .tempfile()
      .unwrap();
    report.write("\0\0\0".as_bytes()).unwrap();
    let report_path = report.path().to_str().unwrap().to_string();
    assert!(parse_cached_report(report_path).is_err());
  }

  #[test]
  fn cached_kscrash_timestamp_includes_timestamp_when_present() {
    let mut diagnostic_metadata = AHashMap::new();
    diagnostic_metadata.insert("crashedAt".to_string(), Value::Unsigned(1_000_000_000));

    let mut crash_report = AHashMap::new();
    crash_report.insert(
      "diagnosticMetaData".to_string(),
      Value::Object(diagnostic_metadata),
    );

    let result = with_cached_report(Some(crash_report), || {
      cached_kscrash_timestamp_impl().unwrap()
    });

    assert_eq!(result, 1_000_000_000);
  }

  #[test]
  fn cached_kscrash_timestamp_returns_zero_when_not_having_report() {
    let result = with_cached_report(None, || cached_kscrash_timestamp_impl().unwrap());

    assert_eq!(result, 0);
  }

  #[test]
  fn enrichment_exact_matching_does_not_use_base_overlap_matches() {
    let kscrash_report = make_kscrash_report(
      11,
      123,
      vec![make_kscrash_thread(
        0,
        Some("worker"),
        &[0x10, 0x20, 0x30, 0x40],
        false,
      )],
    );
    let metrickit_report = make_metrickit_report(
      11,
      123,
      vec![make_metrickit_thread(
        &[0x99, 0x10, 0x20, 0x30, 0x40],
        false,
      )],
    );

    let result = enhance_report_with_summary(&metrickit_report, &kscrash_report, false).unwrap();
    let enhanced_report = result.report.expect("expected report");

    assert_eq!(metrickit_thread_name(&enhanced_report, 0), None);
    assert_eq!(
      result.summary,
      EnrichmentSummary::new(EnrichmentOutcome::NoEffect, EnrichmentReason::NoMatchFound)
    );
  }

  #[test]
  fn enrichment_base_matching_uses_stack_overlap_matches() {
    let kscrash_report = make_kscrash_report(
      11,
      123,
      vec![make_kscrash_thread(
        0,
        Some("worker"),
        &[0x10, 0x20, 0x30, 0x40],
        false,
      )],
    );
    let metrickit_report = make_metrickit_report(
      11,
      123,
      vec![make_metrickit_thread(
        &[0x99, 0x10, 0x20, 0x30, 0x40],
        false,
      )],
    );

    let result = enhance_report_with_summary(&metrickit_report, &kscrash_report, true).unwrap();
    let enhanced_report = result.report.expect("expected report");

    assert_eq!(metrickit_thread_name(&enhanced_report, 0), Some("worker"));
    assert_eq!(
      result.summary,
      EnrichmentSummary::new(EnrichmentOutcome::Enhanced, EnrichmentReason::None)
    );
  }

  #[test]
  fn enrichment_returns_none_when_metadata_mismatches() {
    let kscrash_report = make_kscrash_report(
      11,
      123,
      vec![make_kscrash_thread(
        0,
        Some("worker"),
        &[0x10, 0x20, 0x30, 0x40],
        false,
      )],
    );
    let metrickit_report = make_metrickit_report(
      10,
      123,
      vec![make_metrickit_thread(&[0x10, 0x20, 0x30, 0x40], false)],
    );

    let result = enhance_report_with_summary(&metrickit_report, &kscrash_report, true).unwrap();

    assert_eq!(result.report, None);
    assert_eq!(
      result.summary,
      EnrichmentSummary::new(
        EnrichmentOutcome::NoEffect,
        EnrichmentReason::MetadataMismatch
      )
    );
  }

  #[test]
  fn enrichment_base_matching_does_not_invent_name_for_unnamed_crash_thread() {
    let kscrash_report = make_kscrash_report(
      11,
      123,
      vec![
        make_kscrash_thread(0, None, &[0x10, 0x20, 0x30, 0x40], true),
        make_kscrash_thread(1, Some("worker"), &[0xAA, 0xBB, 0xCC, 0xDD], false),
      ],
    );
    let metrickit_report = make_metrickit_report(
      11,
      123,
      vec![make_metrickit_thread(&[0x10, 0x20, 0x30, 0x40], true)],
    );

    let result = enhance_report_with_summary(&metrickit_report, &kscrash_report, true).unwrap();
    let enhanced_report = result.report.expect("expected report");

    assert_eq!(metrickit_thread_name(&enhanced_report, 0), None);
  }

  #[test]
  fn enrichment_base_matching_leaves_ambiguous_ties_unnamed() {
    let kscrash_report = make_kscrash_report(
      11,
      123,
      vec![
        make_kscrash_thread(0, Some("worker-a"), &[0x99, 0x20, 0x30, 0x40], false),
        make_kscrash_thread(1, Some("worker-b"), &[0x88, 0x20, 0x30, 0x40], false),
      ],
    );
    let metrickit_report = make_metrickit_report(
      11,
      123,
      vec![make_metrickit_thread(&[0x10, 0x20, 0x30, 0x40], false)],
    );

    let result = enhance_report_with_summary(&metrickit_report, &kscrash_report, true).unwrap();
    let enhanced_report = result.report.expect("expected report");

    assert_eq!(metrickit_thread_name(&enhanced_report, 0), None);
  }

  #[test]
  fn enrichment_base_matching_allows_same_name_ties() {
    let kscrash_report = make_kscrash_report(
      11,
      123,
      vec![
        make_kscrash_thread(0, Some("worker"), &[0x99, 0x20, 0x30, 0x40, 0x50], false),
        make_kscrash_thread(1, Some("worker"), &[0x88, 0x20, 0x30, 0x40, 0x50], false),
      ],
    );
    let metrickit_report = make_metrickit_report(
      11,
      123,
      vec![make_metrickit_thread(
        &[0x10, 0x20, 0x30, 0x40, 0x50],
        false,
      )],
    );

    let result = enhance_report_with_summary(&metrickit_report, &kscrash_report, true).unwrap();
    let enhanced_report = result.report.expect("expected report");

    assert_eq!(metrickit_thread_name(&enhanced_report, 0), Some("worker"));
  }

  #[test]
  fn enrichment_base_matching_requires_minimum_overlap_threshold() {
    let kscrash_report = make_kscrash_report(
      11,
      123,
      vec![make_kscrash_thread(
        0,
        Some("worker"),
        &[0x99, 0x20, 0x30, 0x40],
        false,
      )],
    );
    let metrickit_report = make_metrickit_report(
      11,
      123,
      vec![make_metrickit_thread(&[0x10, 0x20, 0x30, 0x40], false)],
    );

    let result = enhance_report_with_summary(&metrickit_report, &kscrash_report, true).unwrap();
    let enhanced_report = result.report.expect("expected report");

    assert_eq!(metrickit_thread_name(&enhanced_report, 0), None);
  }
}
