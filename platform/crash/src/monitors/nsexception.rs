// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

#[cfg(test)]
#[path = "./nsexception_test.rs"]
mod tests;

use crate::monitors::Monitor;
use crate::writer::{self, NSExceptionFrameRecord};
use objc2_foundation::{NSArray, NSException, NSNumber};
use std::ffi::{c_char, c_int, c_void, CStr};
use std::ptr::{null, null_mut, read_unaligned};
use std::sync::atomic::{AtomicBool, AtomicUsize, Ordering};

type ExceptionHandler = unsafe extern "C" fn(*mut NSException);

static IN_HANDLER: AtomicBool = AtomicBool::new(false);
static PREVIOUS_HANDLER: AtomicUsize = AtomicUsize::new(0);

#[derive(Clone, Debug, PartialEq, Eq)]
struct ExceptionSnapshot {
  name: String,
  reason: Option<String>,
  frames: Vec<ExceptionFrameSnapshot>,
}

#[derive(Clone, Debug, PartialEq, Eq)]
struct ExceptionFrameSnapshot {
  return_address: u64,
  image_load_address: u64,
  binary_name: Option<String>,
  image_id: Option<String>,
}

#[allow(clippy::struct_field_names)]
#[repr(C)]
struct DlInfo {
  dli_fname: *const c_char,
  dli_fbase: *mut c_void,
  dli_sname: *const c_char,
  dli_saddr: *mut c_void,
}

#[repr(C)]
struct MachHeader64 {
  magic: u32,
  cputype: i32,
  cpusubtype: i32,
  filetype: u32,
  ncmds: u32,
  sizeofcmds: u32,
  flags: u32,
  reserved: u32,
}

#[repr(C)]
struct LoadCommand {
  cmd: u32,
  cmdsize: u32,
}

#[repr(C)]
struct UuidCommand {
  cmd: u32,
  cmdsize: u32,
  uuid: [u8; 16],
}

unsafe extern "C" {
  // Apple bridge functions for the process-wide uncaught NSException handler.
  // `NSGetUncaughtExceptionHandler` returns the current/original handler so we can preserve it and
  // chain to it after recording our snapshot. `NSSetUncaughtExceptionHandler` installs or restores
  // the handler that Foundation should invoke for an uncaught NSException.
  // https://developer.apple.com/documentation/foundation/nsgetuncaughtexceptionhandler()
  // https://developer.apple.com/documentation/Foundation/NSSetUncaughtExceptionHandler(_:)
  fn NSGetUncaughtExceptionHandler() -> Option<ExceptionHandler>;
  fn NSSetUncaughtExceptionHandler(handler: Option<ExceptionHandler>);
  fn dladdr(addr: *const c_void, info: *mut DlInfo) -> c_int;
}

const LC_UUID: u32 = 0x1b;
const MH_MAGIC_64: u32 = 0xfeed_facf;
const MH_CIGAM_64: u32 = 0xcffa_edfe;

//
// NSExceptionMonitor
//

pub(crate) struct NSExceptionMonitor;

impl Monitor for NSExceptionMonitor {
  fn install(&self) -> bool {
    // Foundation only calls a process-wide uncaught exception handler if one has been installed.
    // When there is no existing handler, `NSGetUncaughtExceptionHandler` returns `None`, which
    // means we become the first uncaught exception handler for the process.
    //
    // Install flow:
    // 1. Read and save the current handler, if any.
    // 2. Publish our handler with `NSSetUncaughtExceptionHandler`.
    // 3. When an uncaught NSException later arrives, record a snapshot and then chain to the saved
    //    handler so existing application or system behavior is preserved.
    unsafe {
      let previous = NSGetUncaughtExceptionHandler();
      store_previous_handler(previous);
      NSSetUncaughtExceptionHandler(Some(handle_exception));
    }
    true
  }

  fn uninstall(&self) {
    // Uninstall flow:
    // 1. Clear the re-entrancy flag so a future install starts from a clean state.
    // 2. Restore the previously registered Foundation handler, if one existed.
    // 3. Drop our saved raw handler pointer once restoration is complete.
    //
    // If we were the only uncaught exception handler, restoring `None` returns the process to the
    // default Foundation behavior where no custom uncaught exception handler is installed.
    IN_HANDLER.store(false, Ordering::SeqCst);
    let previous = previous_handler();

    unsafe {
      NSSetUncaughtExceptionHandler(previous);
    }

    PREVIOUS_HANDLER.store(0, Ordering::Release);
  }
}

unsafe extern "C" fn handle_exception(exception: *mut NSException) {
  let snapshot = unsafe { exception.as_ref() }.map(extract_exception_snapshot);
  handle_exception_snapshot(exception, snapshot);
}

fn handle_exception_snapshot(exception: *mut NSException, snapshot: Option<ExceptionSnapshot>) {
  // The system invokes the uncaught exception handler at the point of termination, so keep the
  // critical section small and reject re-entrant handler entry.
  if !try_enter_handler() {
    return;
  }

  if let Some(snapshot) = snapshot {
    record_exception_snapshot(&snapshot);
  }

  chain_previous(exception);
}

fn extract_exception_snapshot(exception: &NSException) -> ExceptionSnapshot {
  let return_addresses = exception.callStackReturnAddresses();
  let return_addresses: &NSArray<NSNumber> = return_addresses.as_ref();
  let frames = (0 .. return_addresses.count())
    .map(|index| resolve_frame_snapshot(return_addresses.objectAtIndex(index).as_u64()))
    .collect::<Vec<_>>();

  ExceptionSnapshot {
    name: exception.name().to_string(),
    reason: exception.reason().map(|reason| reason.to_string()),
    frames,
  }
}

fn record_exception_snapshot(snapshot: &ExceptionSnapshot) {
  let frames = snapshot
    .frames
    .iter()
    .map(|frame| NSExceptionFrameRecord {
      return_address: frame.return_address,
      image_load_address: frame.image_load_address,
      binary_name: frame.binary_name.as_deref(),
      image_id: frame.image_id.as_deref(),
    })
    .collect::<Vec<_>>();
  writer::record_nsexception(snapshot.name.as_str(), snapshot.reason.as_deref(), &frames);
}

fn resolve_frame_snapshot(return_address: u64) -> ExceptionFrameSnapshot {
  let mut info = DlInfo {
    dli_fname: null(),
    dli_fbase: null_mut(),
    dli_sname: null(),
    dli_saddr: null_mut(),
  };
  #[allow(clippy::cast_possible_truncation)]
  let address = return_address as usize as *const c_void;
  let result = unsafe { dladdr(address, &raw mut info) };
  if result == 0 {
    return ExceptionFrameSnapshot {
      return_address,
      image_load_address: 0,
      binary_name: None,
      image_id: None,
    };
  }

  ExceptionFrameSnapshot {
    return_address,
    image_load_address: info.dli_fbase as usize as u64,
    binary_name: binary_name_from_path(info.dli_fname),
    image_id: image_id_from_header(info.dli_fbase.cast_const()),
  }
}

fn binary_name_from_path(path_ptr: *const c_char) -> Option<String> {
  if path_ptr.is_null() {
    return None;
  }

  let path = unsafe { CStr::from_ptr(path_ptr) }.to_str().ok()?;
  path.rsplit('/').next().map(str::to_owned)
}

fn image_id_from_header(header_ptr: *const c_void) -> Option<String> {
  if header_ptr.is_null() {
    return None;
  }

  let header = unsafe { &*header_ptr.cast::<MachHeader64>() };
  if !matches!(header.magic, MH_MAGIC_64 | MH_CIGAM_64) {
    return None;
  }

  let mut cursor = unsafe {
    header_ptr
      .cast::<u8>()
      .add(std::mem::size_of::<MachHeader64>())
  };
  let load_command_size = size_u32::<LoadCommand>();
  let uuid_command_size = size_u32::<UuidCommand>();
  for _ in 0 .. header.ncmds {
    let command = unsafe { read_unaligned(cursor.cast::<LoadCommand>()) };
    if command.cmdsize < load_command_size {
      break;
    }
    if command.cmd == LC_UUID {
      if command.cmdsize < uuid_command_size {
        break;
      }
      let uuid_command = unsafe { read_unaligned(cursor.cast::<UuidCommand>()) };
      return Some(format_uuid(uuid_command.uuid));
    }
    cursor = unsafe { cursor.add(command.cmdsize as usize) };
  }

  None
}

fn size_u32<T>() -> u32 {
  u32::try_from(std::mem::size_of::<T>())
    .ok()
    .unwrap_or(u32::MAX)
}

fn format_uuid(uuid: [u8; 16]) -> String {
  format!(
    "{:02X}{:02X}{:02X}{:02X}-{:02X}{:02X}-{:02X}{:02X}-{:02X}{:02X}-{:02X}{:02X}{:02X}{:02X}{:\
     02X}{:02X}",
    uuid[0],
    uuid[1],
    uuid[2],
    uuid[3],
    uuid[4],
    uuid[5],
    uuid[6],
    uuid[7],
    uuid[8],
    uuid[9],
    uuid[10],
    uuid[11],
    uuid[12],
    uuid[13],
    uuid[14],
    uuid[15],
  )
}

fn try_enter_handler() -> bool {
  !IN_HANDLER.swap(true, Ordering::SeqCst)
}

fn chain_previous(exception: *mut NSException) {
  if let Some(previous) = previous_handler() {
    unsafe {
      previous(exception);
    }
  }
}

fn store_previous_handler(handler: Option<ExceptionHandler>) {
  // NSGetUncaughtExceptionHandler returns a raw function pointer. Store the previous handler so it
  // can be restored on uninstall and chained after the current snapshot is recorded.
  PREVIOUS_HANDLER.store(
    handler.map_or(0, |handler| handler as usize),
    Ordering::Release,
  );
}

fn previous_handler() -> Option<ExceptionHandler> {
  let previous = PREVIOUS_HANDLER.load(Ordering::Acquire);
  if previous == 0 {
    None
  } else {
    Some(unsafe { std::mem::transmute::<usize, ExceptionHandler>(previous) })
  }
}
