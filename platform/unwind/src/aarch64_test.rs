// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

#![allow(clippy::unwrap_used)]

use super::{
  Frame,
  FrameKind,
  Registers,
  StopReason,
  strip_instruction_pointer,
  walk_frame_pointers,
};
use crate::memory::{AddressRange, StackMemory};
use crate::test_support::count_allocations;

fn registers() -> Registers {
  Registers {
    pc: 0x8000,
    sp: 0x1000,
    fp: 0x1020,
    lr: 0xdead_0000,
    ..Registers::default()
  }
}

fn record(bytes: &mut [u8], offset: usize, next: u64, address: u64) {
  bytes[offset .. offset + 8].copy_from_slice(&next.to_le_bytes());
  bytes[offset + 8 .. offset + 16].copy_from_slice(&address.to_le_bytes());
}

fn walk(registers: &Registers, bytes: &[u8], output: &mut [Frame]) -> super::Trace {
  walk_frame_pointers(
    &StackMemory {
      address: 0x1000,
      bytes,
    },
    registers,
    AddressRange::new(0x1000, 0x100).unwrap(),
    output,
  )
}

#[test]
fn preserves_pc_return_addresses_and_untouched_output_tail() {
  let mut bytes = [0; 0x100];
  record(&mut bytes, 0x20, 0x1040, 0x9004);
  record(&mut bytes, 0x40, 0, 0xa008);
  let sentinel = Frame {
    address: 42,
    kind: FrameKind::ProgramCounter,
  };
  let mut output = [sentinel; 5];
  let trace = walk(&registers(), &bytes, &mut output);
  assert_eq!(trace.frame_count, 3);
  assert_eq!(trace.stop_reason, StopReason::EndOfStack);
  assert_eq!(output[0].address, 0x8000);
  assert_eq!(output[0].kind, FrameKind::ProgramCounter);
  assert_eq!(output[1].address, 0x9004);
  assert_eq!(output[1].kind, FrameKind::ReturnAddress);
  assert_eq!(output[2].address, 0xa008);
  assert_eq!(&output[3 ..], &[sentinel; 2]);
}

#[test]
fn bounds_work_by_the_caller_output_capacity() {
  let mut bytes = [0; 0x100];
  record(&mut bytes, 0x20, 0x1040, 0x9004);
  record(&mut bytes, 0x40, 0, 0xa008);
  for capacity in 0 .. 3 {
    let mut output = [Frame::default(); 3];
    let trace = walk(&registers(), &bytes, &mut output[.. capacity]);
    assert_eq!(trace.frame_count, capacity);
    assert_eq!(trace.stop_reason, StopReason::OutputFull);
  }
}

#[test]
fn retains_invalid_sampled_pc_and_does_not_guess_lr() {
  let mut registers = registers();
  registers.pc = 0;
  registers.fp = 0;
  let mut output = [Frame::default(); 4];
  let trace = walk(&registers, &[], &mut output);
  assert_eq!(trace.frame_count, 1);
  assert_eq!(trace.stop_reason, StopReason::FramePointerUnavailable);
  assert_eq!(output[0].address, 0);
}

#[test]
fn rejects_misaligned_and_out_of_bounds_frames() {
  let mut registers = registers();
  let mut output = [Frame::default(); 4];
  registers.fp = 0x1024;
  assert_eq!(
    walk(&registers, &[], &mut output).stop_reason,
    StopReason::InvalidFramePointer
  );
  for fp in [0x0ff0, 0x1100, u64::MAX - 15] {
    registers.fp = fp;
    assert_eq!(
      walk(&registers, &[], &mut output).stop_reason,
      StopReason::OutOfStackBounds
    );
  }
  registers.fp = 0x1020;
  registers.sp = 0x1030;
  assert_eq!(
    walk(&registers, &[], &mut output).stop_reason,
    StopReason::OutOfStackBounds
  );
}

#[test]
fn does_not_require_frame_records_to_have_the_stack_pointers_alignment() {
  let mut registers = registers();
  registers.fp = 0x1028;
  let mut bytes = [0; 0x100];
  record(&mut bytes, 0x28, 0, 0x9004);
  let mut output = [Frame::default(); 4];
  let trace = walk(&registers, &bytes, &mut output);
  assert_eq!(trace.frame_count, 2);
  assert_eq!(trace.stop_reason, StopReason::EndOfStack);
}

#[test]
fn stops_on_unreadable_memory_without_losing_the_prefix() {
  let mut bytes = [0; 0x30];
  record(&mut bytes, 0x20, 0x1040, 0x9004);
  let mut output = [Frame::default(); 4];
  let trace = walk(&registers(), &bytes, &mut output);
  assert_eq!(trace.frame_count, 2);
  assert_eq!(trace.stop_reason, StopReason::UnreadableMemory);
  assert_eq!(output[1].address, 0x9004);
}

#[test]
fn detects_cycles_and_backward_links() {
  for next in [0x1020, 0x1010] {
    let mut bytes = [0; 0x100];
    record(&mut bytes, 0x20, next, 0x9004);
    let mut output = [Frame::default(); 8];
    let trace = walk(&registers(), &bytes, &mut output);
    assert_eq!(trace.frame_count, 2);
    assert_eq!(trace.stop_reason, StopReason::NonIncreasingFramePointer);
  }
}

#[test]
fn distinguishes_a_null_sentinel_from_missing_return_address() {
  let mut bytes = [0; 0x100];
  let mut output = [Frame::default(); 4];
  assert_eq!(
    walk(&registers(), &bytes, &mut output).stop_reason,
    StopReason::EndOfStack
  );
  record(&mut bytes, 0x20, 0x1040, 0);
  assert_eq!(
    walk(&registers(), &bytes, &mut output).stop_reason,
    StopReason::InvalidFramePointer
  );
}

#[test]
fn reports_async_tag_instead_of_treating_it_as_a_pointer() {
  let mut bytes = [0; 0x100];
  record(&mut bytes, 0x20, 0x1040 | (1 << 60), 0x9004);
  let mut output = [Frame::default(); 4];
  let trace = walk(&registers(), &bytes, &mut output);
  assert_eq!(trace.stop_reason, StopReason::AsyncBoundary);
  assert_eq!(trace.frame_count, 1);

  let mut registers = registers();
  registers.fp |= 1 << 60;
  assert_eq!(
    walk(&registers, &[], &mut output).stop_reason,
    StopReason::AsyncBoundary
  );
}

#[test]
fn walking_does_not_allocate() {
  let mut bytes = [0; 0x100];
  record(&mut bytes, 0x20, 0x1040, 0x9004);
  record(&mut bytes, 0x40, 0, 0xa008);
  let mut output = [Frame::default(); 8];
  let (trace, allocations) = count_allocations(|| walk(&registers(), &bytes, &mut output));
  assert_eq!(trace.frame_count, 3);
  assert_eq!(allocations, 0);
}

#[test]
fn stripping_keeps_unsigned_instruction_addresses() {
  for address in [0, 0x8000, 0x0000_0001_2345_6780] {
    assert_eq!(strip_instruction_pointer(address), address);
  }
}

#[cfg(target_arch = "aarch64")]
#[test]
fn strips_a_return_address_signed_by_the_processor() {
  let address = 0x0000_0001_2345_6780;
  let mut signed = address;
  // SAFETY: PACIASP only signs the supplied LR with the current SP. It neither dereferences the
  // address nor changes SP. Like XPACLRI, it is in hint space and is a no-op without PAC support.
  unsafe {
    core::arch::asm!("hint #25", inout("x30") signed, options(nomem, nostack, preserves_flags));
  }
  assert_eq!(strip_instruction_pointer(signed), address);
}
