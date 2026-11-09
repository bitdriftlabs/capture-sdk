// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

//! ARM64 compact-unwind walking over separate stack and image readers.

#[cfg(test)]
#[path = "./cursor_test.rs"]
mod tests;

use crate::aarch64::{
  Frame,
  FrameKind,
  Registers,
  StopReason,
  Trace,
  read_frame_record,
  strip_instruction_pointer,
  validate_frame_pointer,
};
use crate::compact::{CompactUnwind, LookupError};
use crate::image::ImageInfo;
use crate::memory::{AddressRange, MemoryReader};

const MODE_MASK: u32 = 0x0f00_0000;
const MODE_FRAMELESS: u32 = 0x0200_0000;
const MODE_DWARF: u32 = 0x0300_0000;
const MODE_FRAME: u32 = 0x0400_0000;
const MODE_FRAME_PAUTH_LR: u32 = 0x0500_0000;
const STACK_SIZE_MASK: u32 = 0x00ff_f000;
const ASYNC_FRAME_BIT: u64 = 1 << 60;

/// Walks compact-unwind frame-based and frameless rules, falling back to frame pointers when an
/// image or entry is absent (including zero encodings). DWARF and unknown encodings stop
/// explicitly. Only PC, SP, FP and the initial LR are needed; this does not restore a resumable
/// register context.
///
/// Images must be surveyed before capture. The image reader must keep their section contents
/// consistent and readable for this walk. The stack reader may expose only a bounded copy; it is
/// never replaced with the image reader. All work happens in caller-owned storage.
///
/// Return addresses are looked up at address minus one, but stored unchanged. Compact unwind is
/// not instruction-level metadata: an asynchronous sample inside a prologue or epilogue can still
/// be incomplete. Pointer authentication is stripped, not authenticated.
#[must_use]
pub fn walk(
  stack_memory: &impl MemoryReader,
  image_memory: &impl MemoryReader,
  images: &[ImageInfo],
  registers: &Registers,
  stack: AddressRange,
  output: &mut [Frame],
) -> Trace {
  // This cursor reconstructs callers without changing the captured thread's real registers.
  // Only the initial context supplies a known LR; subsequent steps must not reuse that value.
  let mut cursor = Cursor {
    pc: strip_instruction_pointer(registers.pc),
    sp: registers.sp,
    fp: registers.fp,
    lr: Some(strip_instruction_pointer(registers.lr)),
  };
  let mut count = 0;
  let mut terminal = false;
  let reason = loop {
    // Each iteration emits one frame into caller-owned storage, bounding the walk by its capacity.
    // A full buffer stops the walk without indexing past its end or allocating more space.
    let Some(slot) = output.get_mut(count) else {
      break StopReason::OutputFull;
    };
    // The first address is the sampled instruction. All later addresses were recovered as returns
    // into callers, even though the cursor stores both kinds in its `pc` field.
    let kind = if count == 0 {
      FrameKind::ProgramCounter
    } else {
      FrameKind::ReturnAddress
    };
    *slot = Frame {
      address: cursor.pc,
      kind,
    };
    count += 1;
    // A frame record with a null previous FP can still contain a valid return. Emit that caller
    // above before honoring the terminal flag produced by the preceding step.
    if terminal {
      break StopReason::EndOfStack;
    }
    if count == output.len() {
      break StopReason::OutputFull;
    }
    // A return points after the call and may land exactly on the next metadata range's boundary.
    // Subtract one only for lookup; the address already written to output remains unchanged.
    let instruction = if count == 1 {
      cursor.pc
    } else {
      cursor.pc.saturating_sub(1)
    };
    // Resolve metadata for this frame's image. Missing metadata permits FP fallback, whereas a
    // malformed/unreadable table stops the walk with the valid prefix already in output.
    let encoding = match encoding(image_memory, images, instruction) {
      Ok(encoding) => encoding,
      Err(reason) => break reason,
    };
    // The selected rule determines how to recover the next caller. A plausible FP alone cannot
    // make this decision: a frameless leaf may still hold its caller's FP and would skip a frame.
    let result = match encoding {
      None | Some(0) => cursor.frame(stack_memory, stack),
      Some(rule) => match rule & MODE_MASK {
        MODE_FRAME | MODE_FRAME_PAUTH_LR => cursor.frame(stack_memory, stack),
        MODE_FRAMELESS => cursor.frameless(rule, stack),
        // TODO(ademarco): Not yet implemented
        MODE_DWARF => Err(StopReason::DwarfRequired),
        _ => Err(StopReason::UnsupportedUnwindEncoding),
      },
    };
    terminal = match result {
      Ok(terminal) => terminal,
      Err(reason) => break reason,
    };
    // A null recovered return ends the chain without appending a fabricated frame at address zero.
    // This does not discard an initial sampled PC of zero, which was emitted before stepping.
    if cursor.pc == 0 {
      break StopReason::EndOfStack;
    }
  };
  Trace {
    frame_count: count,
    stop_reason: reason,
  }
}

fn encoding(
  memory: &impl MemoryReader,
  images: &[ImageInfo],
  instruction: u64,
) -> Result<Option<u32>, StopReason> {
  let Some(image) = images
    .iter()
    .find(|image| image.text.contains(instruction, 1))
  else {
    return Ok(None);
  };
  let Some(section) = image.unwind_info else {
    return Ok(None);
  };
  CompactUnwind {
    section,
    image_base: image.header_address,
  }
  .lookup(memory, instruction)
  .map_err(|error| match error {
    LookupError::UnreadableMemory => StopReason::UnreadableMemory,
    LookupError::InvalidSection => StopReason::InvalidUnwindInfo,
    LookupError::UnsupportedVersion | LookupError::UnsupportedPage => {
      StopReason::UnsupportedUnwindEncoding
    },
  })
}

//
// Cursor
//

struct Cursor {
  pc: u64,
  sp: u64,
  fp: u64,
  lr: Option<u64>,
}

impl Cursor {
  fn frame(&mut self, memory: &impl MemoryReader, stack: AddressRange) -> Result<bool, StopReason> {
    validate_frame_pointer(self.fp, self.sp, stack)?;
    // The current frame record stores the caller's FP at [FP] and its return address at [FP + 8].
    // Both words must come from the stack reader, which may expose only the captured copy.
    let (fp, pc) = read_frame_record(memory, self.fp)?;
    if fp & ASYNC_FRAME_BIT != 0 {
      return Err(StopReason::AsyncBoundary);
    }
    if fp != 0 && fp <= self.fp {
      return Err(StopReason::NonIncreasingFramePointer);
    }
    if pc == 0 && fp != 0 {
      return Err(StopReason::InvalidFramePointer);
    }
    // For this frame layout, the caller's SP is immediately above the saved FP/LR pair.
    self.sp = self.fp + 16; // The checked stack range above proves this cannot overflow.
    self.fp = fp;
    self.pc = pc;
    self.lr = None; // The caller's LR was not saved as part of this frame record.
    Ok(fp == 0)
  }

  fn frameless(&mut self, encoding: u32, stack: AddressRange) -> Result<bool, StopReason> {
    let lr = self.lr.ok_or(StopReason::ReturnAddressUnavailable)?;
    // ARM64 frameless rules encode the stack allocation in units of 16 bytes. The leaf did not
    // save a new FP/LR pair, so keep FP, undo that allocation and use the known live LR as return.
    let size = u64::from((encoding & STACK_SIZE_MASK) >> 12) * 16;
    if !self.sp.is_multiple_of(16) || !stack.contains(self.sp, size) {
      return Err(StopReason::OutOfStackBounds);
    }
    self.sp += size;
    self.pc = lr;
    self.lr = None;
    Ok(false)
  }
}
