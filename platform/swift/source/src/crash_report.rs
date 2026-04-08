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
use std::ffi::c_void;
use std::ffi::CString;
use std::fs;
use std::path::Path;
use std::ptr::null;
use std::slice;

struct NamedThread {
  name: String,
  call_stack: Vec<u64>,
  count: usize,
}

struct OwnedStackTrace {
  _image_ids: Vec<CString>,
  frames: Vec<BDStackFrame>,
}

#[repr(C)]
#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum CacheResult {
  /// The report was not cached due to an error
  Failure = 0,
  /// The report file does not exist
  ReportDoesNotExist = 1,
  /// Successfully cached a partial document
  PartialSuccess = 2,
  /// Successfully cached the complete document
  Success = 3,
}

#[repr(C)]
#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum ReportCreationResult {
  Failure = 0,
  ReportDoesNotExist = 1,
  Success = 2,
}

#[repr(i8)]
#[derive(Debug, Clone, Copy, PartialEq, Eq)]
enum ReportType {
  NativeCrash = 5,
}

const SDK_ID: &str = "io.bitdrift.capture-apple";

type BDProcessorHandle = *mut *const c_void;

#[repr(C)]
struct BDBinaryImage {
  id: *const i8,
  path: *const i8,
  load_address: u64,
}

#[repr(C)]
struct BDCPURegister {
  name: *const i8,
  value: u64,
}

#[repr(C)]
struct BDStackFrame {
  type_: i8,
  frame_address: u64,
  symbol_address: u64,
  symbol_name: *const i8,
  class_name: *const i8,
  file_name: *const i8,
  line: i64,
  column: i64,
  image_id: *const i8,
  state_count: usize,
  state: *const *const i8,
  reg_count: usize,
  regs: *const BDCPURegister,
}

#[repr(C)]
struct BDThread {
  name: *const i8,
  state: *const i8,
  active: bool,
  index: u32,
  priority: f32,
  quality_of_service: i8,
}

#[repr(C)]
struct BDDeviceMetrics {
  time_seconds: u64,
  time_nanos: u32,
  timezone: *const i8,
  manufacturer: *const i8,
  model: *const i8,
  os_version: *const i8,
  os_brand: *const i8,
  os_fingerprint: *const i8,
  os_kernversion: *const i8,
  power_state: i8,
  power_charge_percent: u8,
  network_state: i8,
  architecture: i8,
  display_height: u32,
  display_width: u32,
  display_density_dpi: u32,
  platform: i8,
  rotation: i8,
  cpu_abi_count: u8,
  cpu_abis: *const *const i8,
}

#[repr(C)]
struct BDAppMetrics {
  app_id: *const i8,
  version: *const i8,
  version_code: i64,
  cf_bundle_version: *const i8,
  running_state: *const i8,
  memory_used: u64,
  memory_free: u64,
  memory_total: u64,
}

unsafe extern "C-unwind" {
  fn bdrw_create_buffer_handle(
    handle: BDProcessorHandle,
    report_type: i8,
    sdk_id: *const i8,
    sdk_version: *const i8,
  );
  fn bdrw_get_completed_buffer(handle: BDProcessorHandle, buffer_length: *mut u64) -> *const u8;
  fn bdrw_dispose_buffer_handle(handle: BDProcessorHandle);
  fn bdrw_add_binary_image(handle: BDProcessorHandle, image: *const BDBinaryImage) -> bool;
  fn bdrw_add_thread(
    handle: BDProcessorHandle,
    system_thread_count: u16,
    thread_ptr: *const BDThread,
    stack_count: u32,
    stack: *const BDStackFrame,
  ) -> bool;
  fn bdrw_add_error(
    handle: BDProcessorHandle,
    name: *const i8,
    reason: *const i8,
    relation_to_next: i8,
    stack_count: u32,
    stack: *const BDStackFrame,
  ) -> bool;
  fn bdrw_add_device(handle: BDProcessorHandle, device_ptr: *const BDDeviceMetrics) -> bool;
  fn bdrw_add_app(handle: BDProcessorHandle, app_ptr: *const BDAppMetrics) -> bool;
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

#[no_mangle]
extern "C" fn capture_create_report_from_cached_kscrash_report(
  report_path_ptr: *const Object,
  sdk_version_ptr: *const Object,
) -> ReportCreationResult {
  with_handle_unexpected_or(
    || -> anyhow::Result<ReportCreationResult> {
      create_report_from_cached_kscrash_report_impl(report_path_ptr, sdk_version_ptr)
    },
    ReportCreationResult::Failure,
    "create crash report from cached kscrash report",
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

fn create_report_from_cached_kscrash_report_impl(
  report_path_ptr: *const Object,
  sdk_version_ptr: *const Object,
) -> anyhow::Result<ReportCreationResult> {
  let report_path = unsafe { nsstring_into_string(report_path_ptr) }
    .map_err(|e| anyhow::anyhow!("Failed to convert report path to string: {e}"))?;
  let sdk_version = unsafe { nsstring_into_string(sdk_version_ptr) }
    .map_err(|e| anyhow::anyhow!("Failed to convert sdk version to string: {e}"))?;

  let Some(kscrash_report) = CACHED_KSCRASH_REPORT.lock().as_ref().cloned() else {
    return Ok(ReportCreationResult::ReportDoesNotExist);
  };

  let bytes = build_report_bytes_from_kscrash_report(&kscrash_report, &sdk_version)?;
  fs::write(report_path, bytes)?;
  Ok(ReportCreationResult::Success)
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

  Ok(if was_partial {
    CacheResult::PartialSuccess
  } else {
    CacheResult::Success
  })
}

fn build_report_bytes_from_kscrash_report(
  kscrash_report: &AHashMap<String, Value>,
  sdk_version: &str,
) -> anyhow::Result<Vec<u8>> {
  let mut handle_ptr: *const std::ffi::c_void = null();
  let handle: BDProcessorHandle = &mut handle_ptr;
  let sdk_id = CString::new(SDK_ID)?;
  let sdk_version = CString::new(sdk_version)?;

  unsafe {
    bdrw_create_buffer_handle(
      handle,
      ReportType::NativeCrash as i8,
      sdk_id.as_ptr(),
      sdk_version.as_ptr(),
    );
  }

  let result = build_report_contents(handle, kscrash_report);

  if result.is_err() {
    unsafe {
      bdrw_dispose_buffer_handle(handle);
    }
  }

  result
}

fn build_report_contents(
  handle: BDProcessorHandle,
  kscrash_report: &AHashMap<String, Value>,
) -> anyhow::Result<Vec<u8>> {
  add_binary_images(handle, kscrash_report)?;
  let crashed_thread_stack = add_threads(handle, kscrash_report)?;
  add_error(handle, kscrash_report, crashed_thread_stack.as_deref())?;
  add_app_metrics(handle, kscrash_report)?;
  add_device_metrics(handle, kscrash_report)?;

  let mut length = 0_u64;
  let buffer = unsafe { bdrw_get_completed_buffer(handle, &mut length) };
  if buffer.is_null() {
    unsafe {
      bdrw_dispose_buffer_handle(handle);
    }
    return Err(anyhow::anyhow!("Failed to serialize crash report buffer"));
  }

  let bytes = unsafe { slice::from_raw_parts(buffer, length as usize) }.to_vec();
  unsafe {
    bdrw_dispose_buffer_handle(handle);
  }
  Ok(bytes)
}

fn add_binary_images(
  handle: BDProcessorHandle,
  kscrash_report: &AHashMap<String, Value>,
) -> anyhow::Result<()> {
  let Some(Value::Array(images)) = kscrash_report.get("binaryImages") else {
    return Ok(());
  };

  for image in images {
    let Value::Object(image) = image else {
      continue;
    };
    let Some(binary_uuid) = image.get("uuid").and_then(value_as_string) else {
      continue;
    };
    let Some(binary_name) = image.get("name").and_then(value_as_string) else {
      continue;
    };
    let Some(load_address) = image.get("loadAddress").and_then(value_as_u64) else {
      continue;
    };

    let id = CString::new(binary_uuid)?;
    let path = CString::new(binary_name)?;
    let image = BDBinaryImage {
      id: id.as_ptr(),
      path: path.as_ptr(),
      load_address,
    };

    unsafe {
      let _ = bdrw_add_binary_image(handle, &image);
    }
  }

  Ok(())
}

fn add_threads(
  handle: BDProcessorHandle,
  kscrash_report: &AHashMap<String, Value>,
) -> anyhow::Result<Option<Vec<BDStackFrame>>> {
  let mut crashed_thread_stack = None;
  let Some(Value::Array(threads)) = kscrash_report.get("threads") else {
    return Ok(None);
  };

  let system_thread_count = u16::try_from(threads.len()).unwrap_or(u16::MAX);

  for thread in threads {
    let Value::Object(thread) = thread else {
      continue;
    };

    let index = thread
      .get("index")
      .and_then(value_as_u64)
      .and_then(|index| u32::try_from(index).ok())
      .unwrap_or(0);
    let active = thread
      .get("crashed")
      .and_then(value_as_bool)
      .unwrap_or(false);

    let thread_name = thread
      .get("name")
      .and_then(value_as_string)
      .or_else(|| thread.get("dispatchQueue").and_then(value_as_string));
    let thread_name_c = thread_name.as_deref().map(CString::new).transpose()?;

    let stack = build_stack_frames(thread)?;
    let thread_info = BDThread {
      name: thread_name_c
        .as_ref()
        .map_or(null(), |value| value.as_ptr()),
      state: null(),
      active,
      index,
      priority: 0.0,
      quality_of_service: -1,
    };

    unsafe {
      let _ = bdrw_add_thread(
        handle,
        system_thread_count,
        &thread_info,
        u32::try_from(stack.frames.len()).unwrap_or(u32::MAX),
        if stack.frames.is_empty() {
          null()
        } else {
          stack.frames.as_ptr()
        },
      );
    }

    if active && crashed_thread_stack.is_none() {
      crashed_thread_stack = Some(stack.frames);
    }
  }

  Ok(crashed_thread_stack)
}

fn add_error(
  handle: BDProcessorHandle,
  kscrash_report: &AHashMap<String, Value>,
  crashed_thread_stack: Option<&[BDStackFrame]>,
) -> anyhow::Result<()> {
  let error = kscrash_report
    .get("error")
    .and_then(|value| value.as_object().ok());
  let diagnostic = kscrash_report
    .get("diagnosticMetaData")
    .and_then(|value| value.as_object().ok());

  let name = error
    .and_then(|error| error.get("exceptionName").and_then(value_as_string))
    .or_else(|| diagnostic.and_then(|meta| diagnostic_name(meta)))
    .unwrap_or_else(|| "Native Crash".to_string());

  let reason = error
    .and_then(|error| error.get("crashReason").and_then(value_as_string))
    .or_else(|| diagnostic.and_then(|meta| diagnostic_reason(meta)));

  let name = CString::new(name)?;
  let reason_c = reason.as_deref().map(CString::new).transpose()?;
  let stack = crashed_thread_stack.unwrap_or(&[]);

  unsafe {
    let _ = bdrw_add_error(
      handle,
      name.as_ptr(),
      reason_c.as_ref().map_or(null(), |value| value.as_ptr()),
      0,
      u32::try_from(stack.len()).unwrap_or(u32::MAX),
      if stack.is_empty() {
        null()
      } else {
        stack.as_ptr()
      },
    );
  }

  Ok(())
}

fn add_app_metrics(
  handle: BDProcessorHandle,
  kscrash_report: &AHashMap<String, Value>,
) -> anyhow::Result<()> {
  let system = kscrash_report
    .get("system")
    .and_then(|value| value.as_object().ok())
    .ok_or_else(|| anyhow::anyhow!("KSCrash report missing system info"))?;

  let app_id = CString::new(
    system
      .get("bundleID")
      .and_then(value_as_string)
      .unwrap_or_default(),
  )?;
  let version = CString::new(
    system
      .get("bundleShortVersion")
      .and_then(value_as_string)
      .unwrap_or_default(),
  )?;
  let bundle_version = CString::new(
    system
      .get("bundleVersion")
      .and_then(value_as_string)
      .unwrap_or_default(),
  )?;

  let app = BDAppMetrics {
    app_id: app_id.as_ptr(),
    version: version.as_ptr(),
    version_code: 0,
    cf_bundle_version: bundle_version.as_ptr(),
    running_state: null(),
    memory_used: 0,
    memory_free: 0,
    memory_total: 0,
  };

  unsafe {
    let _ = bdrw_add_app(handle, &app);
  }
  Ok(())
}

fn add_device_metrics(
  handle: BDProcessorHandle,
  kscrash_report: &AHashMap<String, Value>,
) -> anyhow::Result<()> {
  let system = kscrash_report
    .get("system")
    .and_then(|value| value.as_object().ok())
    .ok_or_else(|| anyhow::anyhow!("KSCrash report missing system info"))?;
  let diagnostic = kscrash_report
    .get("diagnosticMetaData")
    .and_then(|value| value.as_object().ok())
    .ok_or_else(|| anyhow::anyhow!("KSCrash report missing diagnostic metadata"))?;

  let crashed_at = diagnostic
    .get("crashedAt")
    .and_then(value_as_u64)
    .ok_or_else(|| anyhow::anyhow!("KSCrash report missing crash timestamp"))?;
  let crashed_at_nanos = diagnostic
    .get("crashedAtNanos")
    .and_then(value_as_u64)
    .and_then(|value| u32::try_from(value).ok())
    .unwrap_or(0);
  let model = CString::new(
    system
      .get("model")
      .and_then(value_as_string)
      .unwrap_or_default(),
  )?;
  let os_version = CString::new(
    system
      .get("osVersion")
      .and_then(value_as_string)
      .unwrap_or_default(),
  )?;
  let os_brand = CString::new(
    system
      .get("systemName")
      .and_then(value_as_string)
      .unwrap_or_default(),
  )?;
  let kernel_version = CString::new(
    system
      .get("kernelVersion")
      .and_then(value_as_string)
      .unwrap_or_default(),
  )?;
  let timezone = system
    .get("timezone")
    .and_then(value_as_string)
    .map(CString::new)
    .transpose()?;

  let device = BDDeviceMetrics {
    time_seconds: crashed_at,
    time_nanos: crashed_at_nanos,
    timezone: timezone.as_ref().map_or(null(), |value| value.as_ptr()),
    manufacturer: null(),
    model: model.as_ptr(),
    os_version: os_version.as_ptr(),
    os_brand: os_brand.as_ptr(),
    os_fingerprint: null(),
    os_kernversion: kernel_version.as_ptr(),
    power_state: 0,
    power_charge_percent: 0,
    network_state: 0,
    architecture: architecture_constant(
      system
        .get("binaryArchitecture")
        .and_then(value_as_string)
        .as_deref(),
    ),
    display_height: 0,
    display_width: 0,
    display_density_dpi: 0,
    platform: 2,
    rotation: 0,
    cpu_abi_count: 0,
    cpu_abis: null(),
  };

  unsafe {
    let _ = bdrw_add_device(handle, &device);
  }
  Ok(())
}

fn build_stack_frames(thread: &AHashMap<String, Value>) -> anyhow::Result<OwnedStackTrace> {
  let Some(Value::Object(backtrace)) = thread.get("backtrace") else {
    return Ok(OwnedStackTrace {
      _image_ids: Vec::new(),
      frames: Vec::new(),
    });
  };
  let Some(Value::Array(contents)) = backtrace.get("contents") else {
    return Ok(OwnedStackTrace {
      _image_ids: Vec::new(),
      frames: Vec::new(),
    });
  };

  let mut image_ids = Vec::with_capacity(contents.len());
  let mut frames = Vec::with_capacity(contents.len());
  for frame in contents {
    let Value::Object(frame) = frame else {
      continue;
    };
    let Some(frame_address) = frame.get("address").and_then(value_as_u64) else {
      continue;
    };
    let symbol_address = frame
      .get("offsetIntoBinaryTextSegment")
      .and_then(value_as_u64)
      .unwrap_or(0);
    let image_id = frame
      .get("binaryUUID")
      .and_then(value_as_string)
      .map(CString::new)
      .transpose()?;
    if let Some(image_id) = image_id {
      image_ids.push(image_id);
    }
    let image_id_ptr = image_ids.last().map_or(null(), |value| value.as_ptr());

    frames.push(BDStackFrame {
      type_: 2,
      frame_address,
      symbol_address,
      symbol_name: null(),
      class_name: null(),
      file_name: null(),
      line: -1,
      column: -1,
      image_id: image_id_ptr,
      state_count: 0,
      state: null(),
      reg_count: 0,
      regs: null(),
    });
  }

  Ok(OwnedStackTrace {
    _image_ids: image_ids,
    frames,
  })
}

fn value_as_u64(value: &Value) -> Option<u64> {
  match value {
    Value::Unsigned(value) => Some(*value),
    Value::Signed(value) => (*value >= 0).then_some(*value as u64),
    _ => None,
  }
}

fn value_as_bool(value: &Value) -> Option<bool> {
  match value {
    Value::Bool(value) => Some(*value),
    _ => None,
  }
}

fn value_as_string(value: &Value) -> Option<String> {
  match value {
    Value::String(value) => Some(value.clone()),
    _ => None,
  }
}

fn architecture_constant(arch: Option<&str>) -> i8 {
  match arch {
    Some(arch) if arch.contains("arm64") => 2,
    Some(arch) if arch.contains("x86_64") => 4,
    _ => 0,
  }
}

fn diagnostic_name(meta: &AHashMap<String, Value>) -> Option<String> {
  let exception_type = meta.get("exceptionType").and_then(value_as_u64)?;
  match exception_type {
    1 => Some("EXC_BAD_ACCESS".to_string()),
    2 => Some("EXC_BAD_INSTRUCTION".to_string()),
    3 => Some("EXC_ARITHMETIC".to_string()),
    4 => Some("EXC_EMULATION".to_string()),
    5 => Some("EXC_SOFTWARE".to_string()),
    6 => Some("EXC_BREAKPOINT".to_string()),
    10 => Some("EXC_CRASH".to_string()),
    _ => None,
  }
}

fn diagnostic_reason(meta: &AHashMap<String, Value>) -> Option<String> {
  let exception_code = meta.get("exceptionCode").and_then(value_as_u64)?;
  let signal = meta.get("signal").and_then(value_as_u64)?;
  Some(format!(
    "code: {exception_code}, signal: {}",
    signal_name(signal)
  ))
}

fn signal_name(signal: u64) -> &'static str {
  match signal {
    1 => "SIGHUP",
    2 => "SIGINT",
    3 => "SIGQUIT",
    4 => "SIGILL",
    5 => "SIGTRAP",
    6 => "SIGABRT",
    8 => "SIGFPE",
    9 => "SIGKILL",
    10 => "SIGBUS",
    11 => "SIGSEGV",
    12 => "SIGSYS",
    13 => "SIGPIPE",
    14 => "SIGALRM",
    15 => "SIGTERM",
    _ => "UNKNOWN",
  }
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

#[cfg(test)]
mod tests {
  use super::*;
  use std::io::Write;

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
}
