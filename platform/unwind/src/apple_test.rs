// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

use super::TaskMemory;
#[cfg(target_arch = "aarch64")]
use crate::aarch64::{Frame, FrameKind, Registers, StopReason, Trace, walk_frame_pointers};
#[cfg(target_arch = "aarch64")]
use crate::image::ImageInfo;
#[cfg(target_arch = "aarch64")]
use crate::memory::AddressRange;
use crate::memory::MemoryReader;
use crate::test_support::count_allocations;

#[test]
fn copies_current_task_memory_without_allocating() {
  let source = [11, 22, 33, 44];
  let mut destination = [0; 4];
  let (success, allocations) =
    count_allocations(|| TaskMemory.read(source.as_ptr() as u64, &mut destination));
  assert!(success);
  assert_eq!(destination, source);
  assert_eq!(allocations, 0);
}

#[test]
fn unreadable_addresses_fail_instead_of_faulting() {
  for address in [0, 1, u64::MAX - 1] {
    assert!(!TaskMemory.read(address, &mut [0; 8]));
  }
}

#[test]
fn empty_read_does_not_touch_the_source() {
  assert!(TaskMemory.read(0, &mut []));
}

#[cfg(target_arch = "aarch64")]
#[test]
fn reads_metadata_of_the_running_executable() {
  unsafe extern "C" {
    fn _dyld_get_image_header(index: u32) -> *const core::ffi::c_void;
  }
  // SAFETY: image zero is the process executable and cannot be unloaded during this call.
  let header = unsafe { _dyld_get_image_header(0) } as u64;
  let info = ImageInfo::read(&TaskMemory, header);
  assert!(info.is_ok());
  if let Ok(info) = info {
    assert_eq!(info.text.start(), header);
    assert!(info.uuid.is_some());
  }
}

//
// NativeCapture
//

#[cfg(target_arch = "aarch64")]
struct NativeCapture {
  frames: [Frame; 4],
  trace: Option<Trace>,
}

#[cfg(target_arch = "aarch64")]
extern "C" fn capture_native_frames(context: *mut NativeCapture, fp: u64, sp: u64, pc: u64) {
  // SAFETY: with_native_frames forwards the unique live pointer supplied by the test, and the
  // callback completes before either fixture frame is popped.
  let capture = unsafe { &mut *context };
  if let Some(stack) = AddressRange::new(sp, 32) {
    capture.trace = Some(walk_frame_pointers(
      &TaskMemory,
      &Registers {
        fp,
        sp,
        pc,
        ..Registers::default()
      },
      stack,
      &mut capture.frames,
    ));
  }
}

/// Builds two real ARM64 frame records with a null root, independent of Rust's FP settings. The
/// callback walks them while both functions are still active. Caller-saved state is restored on
/// exit.
#[cfg(target_arch = "aarch64")]
#[unsafe(naked)]
unsafe extern "C" fn with_native_frames(
  callback: extern "C" fn(*mut NativeCapture, u64, u64, u64),
  context: *mut NativeCapture,
) {
  core::arch::naked_asm!(
    "stp x19, x20, [sp, #-32]!",
    "stp x29, x30, [sp, #16]",
    "mov x19, x0",
    "mov x20, x1",
    "stp xzr, x30, [sp, #-16]!",
    "mov x29, sp",
    "bl 2f",
    "add sp, sp, #16",
    "ldp x29, x30, [sp, #16]",
    "ldp x19, x20, [sp], #32",
    "ret",
    "2:",
    "stp x29, x30, [sp, #-16]!",
    "mov x29, sp",
    "mov x0, x20",
    "mov x1, x29",
    "mov x2, sp",
    "adr x3, 2b",
    "blr x19",
    "ldp x29, x30, [sp], #16",
    "ret",
  );
}

#[cfg(target_arch = "aarch64")]
#[test]
fn walks_real_native_frames_through_mach_without_allocating() {
  let mut capture = NativeCapture {
    frames: [Frame::default(); 4],
    trace: None,
  };
  // SAFETY: the fixture preserves the C ABI and invokes the callback synchronously with this
  // unique live context. Its two frame records remain valid for the duration of the callback.
  let ((), allocations) = count_allocations(|| unsafe {
    with_native_frames(capture_native_frames, &raw mut capture);
  });
  assert_eq!(
    capture.trace,
    Some(Trace {
      frame_count: 3,
      stop_reason: StopReason::EndOfStack,
    })
  );
  assert_eq!(allocations, 0);
  assert_eq!(capture.frames[0].kind, FrameKind::ProgramCounter);
  assert_eq!(capture.frames[1].kind, FrameKind::ReturnAddress);
  assert_eq!(capture.frames[2].kind, FrameKind::ReturnAddress);
  assert_ne!(capture.frames[0].address, capture.frames[1].address);
}
