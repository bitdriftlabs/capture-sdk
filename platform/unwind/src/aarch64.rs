// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

#[cfg(test)]
#[path = "./aarch64_test.rs"]
mod tests;

use crate::memory::{AddressRange, MemoryReader};

const FRAME_RECORD_SIZE: u64 = 16;
const FRAME_POINTER_ALIGNMENT: u64 = 8;
const ASYNC_FRAME_BIT: u64 = 1 << 60;

//
// Registers
//

/// ARM64 registers captured by the caller from a Mach thread, signal context or exception state.
///
/// PC, SP and FP must already be decoded using the platform's thread-state accessors. Keeping the
/// complete integer register bank also allows later unwind rules to recover callee-saved registers.
#[derive(Clone, Copy, Debug, Default)]
pub struct Registers {
  /// x0 through x28. x29 and x30 are represented by FP and LR below.
  pub x: [u64; 29],
  pub fp: u64,
  pub lr: u64,
  pub sp: u64,
  pub pc: u64,
}

//
// Frame
//

#[derive(Clone, Copy, Debug, Default, Eq, PartialEq)]
pub enum FrameKind {
  #[default]
  ProgramCounter,
  ReturnAddress,
}

/// The address is not decremented. Consumers must distinguish the sampled PC from return addresses
/// when choosing the instruction to symbolicate.
#[derive(Clone, Copy, Debug, Default, Eq, PartialEq)]
pub struct Frame {
  pub address: u64,
  pub kind: FrameKind,
}

//
// Trace
//

#[derive(Clone, Copy, Debug, Eq, PartialEq)]
pub enum StopReason {
  EndOfStack,
  OutputFull,
  FramePointerUnavailable,
  InvalidFramePointer,
  OutOfStackBounds,
  UnreadableMemory,
  NonIncreasingFramePointer,
  /// Do not follow an async-tagged FP as an ordinary stack address. Async walking is a separate
  /// extension of the same capture primitives.
  AsyncBoundary,
}

#[derive(Clone, Copy, Debug, Eq, PartialEq)]
pub struct Trace {
  /// Only this prefix of the caller's output buffer was written.
  pub frame_count: usize,
  pub stop_reason: StopReason,
}

/// Walks ARM64 frame records, retaining valid frames if the chain is incomplete or corrupt.
///
/// The sampled PC is preserved even when zero, since an invalid PC can explain a crash. LR is not
/// appended speculatively: without unwind metadata we cannot tell whether it belongs to a leaf
/// function or is an unrelated value. Every iteration consumes one output slot, bounding the work.
#[must_use]
pub fn walk_frame_pointers(
  memory: &impl MemoryReader,
  registers: &Registers,
  stack: AddressRange,
  output: &mut [Frame],
) -> Trace {
  let mut frames = output.iter_mut();
  let mut count = 0;
  let Some(first) = frames.next() else {
    return Trace {
      frame_count: 0,
      stop_reason: StopReason::OutputFull,
    };
  };
  *first = Frame {
    address: strip_instruction_pointer(registers.pc),
    kind: FrameKind::ProgramCounter,
  };
  count += 1;

  let mut fp = registers.fp;
  let reason = loop {
    if fp == 0 {
      break StopReason::FramePointerUnavailable;
    }
    let frame_address = fp & !ASYNC_FRAME_BIT;
    if !frame_address.is_multiple_of(FRAME_POINTER_ALIGNMENT) {
      break StopReason::InvalidFramePointer;
    }
    if frame_address < registers.sp || !stack.contains(frame_address, FRAME_RECORD_SIZE) {
      break StopReason::OutOfStackBounds;
    }
    if fp & ASYNC_FRAME_BIT != 0 {
      break StopReason::AsyncBoundary;
    }
    if frames.len() == 0 {
      break StopReason::OutputFull;
    }

    let mut record = [0; 16];
    if !memory.read(fp, &mut record) {
      break StopReason::UnreadableMemory;
    }
    let (next_bytes, return_bytes) = record.split_at(8);
    let (Ok(next_bytes), Ok(return_bytes)) = (next_bytes.try_into(), return_bytes.try_into())
    else {
      break StopReason::UnreadableMemory;
    };
    let next = u64::from_le_bytes(next_bytes);
    let address = strip_instruction_pointer(u64::from_le_bytes(return_bytes));
    if next & ASYNC_FRAME_BIT != 0 {
      break StopReason::AsyncBoundary;
    }
    if address == 0 {
      break if next == 0 {
        StopReason::EndOfStack
      } else {
        StopReason::InvalidFramePointer
      };
    }
    let Some(frame) = frames.next() else {
      break StopReason::OutputFull;
    };
    *frame = Frame {
      address,
      kind: FrameKind::ReturnAddress,
    };
    count += 1;
    if next == 0 {
      break StopReason::EndOfStack;
    }
    if next <= fp {
      break StopReason::NonIncreasingFramePointer;
    }
    fp = next;
  };

  Trace {
    frame_count: count,
    stop_reason: reason,
  }
}

/// Strips PAC using the hint-space instruction, which is a no-op on ARM64 CPUs without PAC. Do not
/// substitute a fixed virtual-address mask: its width depends on the running platform.
#[must_use]
pub fn strip_instruction_pointer(address: u64) -> u64 {
  #[cfg(target_arch = "aarch64")]
  let address = {
    let mut stripped = address;
    // SAFETY: XPACLRI only changes LR, supplied as an explicit in/out operand. It does not read
    // memory or authenticate/dereference the address, and HINT #7 is supported before PAC too.
    unsafe {
      core::arch::asm!("hint #7", inout("x30") stripped, options(nomem, nostack, preserves_flags));
    }
    stripped
  };
  address
}
