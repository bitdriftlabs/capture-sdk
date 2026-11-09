// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

#![allow(clippy::unwrap_used)]

use super::walk;
use crate::aarch64::{Frame, FrameKind, Registers, StopReason};
use crate::compact::tests::{table, word};
use crate::image::ImageInfo;
use crate::memory::{AddressRange, StackMemory};
use crate::test_support::count_allocations;

fn image() -> ImageInfo {
  ImageInfo {
    header_address: 0x8000,
    uuid: None,
    text: AddressRange::new(0x8000, 0x1000).unwrap(),
    unwind_info: Some(AddressRange::new(0x9000, 160).unwrap()),
    eh_frame: None,
  }
}

fn record(bytes: &mut [u8], offset: usize, fp: u64, lr: u64) {
  bytes[offset .. offset + 8].copy_from_slice(&fp.to_le_bytes());
  bytes[offset + 8 .. offset + 16].copy_from_slice(&lr.to_le_bytes());
}

#[test]
fn frameless_leaf_preserves_the_frame_skipped_by_fp_only_walking() {
  let mut bytes = [0; 0x100];
  record(&mut bytes, 0x40, 0x1080, 0x8110);
  record(&mut bytes, 0x80, 0, 0x8120);
  let registers = Registers {
    pc: 0x8190,
    sp: 0x1000,
    fp: 0x1040,
    lr: 0x8180,
    ..Registers::default()
  };
  let mut output = [Frame::default(); 8];
  let metadata = table(true);
  let (trace, allocations) = count_allocations(|| {
    walk(
      &StackMemory {
        address: 0x1000,
        bytes: &bytes,
      },
      &StackMemory {
        address: 0x9000,
        bytes: &metadata,
      },
      &[image()],
      &registers,
      AddressRange::new(0x1000, 0x100).unwrap(),
      &mut output,
    )
  });
  assert_eq!(allocations, 0);
  assert_eq!(trace.frame_count, 4);
  assert_eq!(trace.stop_reason, StopReason::EndOfStack);
  assert!(
    output[.. 4]
      .iter()
      .map(|frame| frame.address)
      .eq([0x8190, 0x8180, 0x8110, 0x8120])
  );
  assert_eq!(output[0].kind, FrameKind::ProgramCounter);
  assert!(
    output[1 .. 4]
      .iter()
      .all(|frame| frame.kind == FrameKind::ReturnAddress)
  );
  // 0x8180 is the first instruction of the frameless range, but as a return address must select
  // the preceding frame-based range. Looking up the unadjusted address would consume LR twice.
}

#[test]
fn sampled_pc_is_not_adjusted_and_lr_is_never_reused() {
  let metadata = table(true);
  let registers = Registers {
    pc: 0x8180,
    sp: 0x1000,
    lr: 0x8190,
    ..Registers::default()
  };
  let mut output = [Frame::default(); 8];
  let trace = walk(
    &StackMemory {
      address: 0x1000,
      bytes: &[0; 0x100],
    },
    &StackMemory {
      address: 0x9000,
      bytes: &metadata,
    },
    &[image()],
    &registers,
    AddressRange::new(0x1000, 0x100).unwrap(),
    &mut output,
  );
  assert_eq!(trace.frame_count, 2);
  assert_eq!(trace.stop_reason, StopReason::ReturnAddressUnavailable);
}

#[test]
fn distinguishes_dwarf_unknown_and_corrupt_rules_without_speculative_fp_fallback() {
  for (encoding, expected) in [
    (0x0300_0010, StopReason::DwarfRequired),
    (0x0600_0000, StopReason::UnsupportedUnwindEncoding),
  ] {
    let mut metadata = table(false);
    word(&mut metadata, 80, encoding);
    let mut output = [Frame::default(); 8];
    let trace = walk(
      &StackMemory {
        address: 0x1000,
        bytes: &[0; 0x100],
      },
      &StackMemory {
        address: 0x9000,
        bytes: &metadata,
      },
      &[image()],
      &Registers {
        pc: 0x8110,
        ..Registers::default()
      },
      AddressRange::new(0x1000, 0x100).unwrap(),
      &mut output,
    );
    assert_eq!(trace.frame_count, 1);
    assert_eq!(trace.stop_reason, expected);
  }
}

#[test]
fn fallback_uses_only_the_copied_stack_and_detects_invalid_chains() {
  for (fp, next, expected) in [
    (0x1020, 0, StopReason::EndOfStack),
    (0x1020, 0x1020, StopReason::NonIncreasingFramePointer),
    (0x1020, 1 << 60, StopReason::AsyncBoundary),
    (0x1021, 0, StopReason::InvalidFramePointer),
    (0x1100, 0, StopReason::OutOfStackBounds),
  ] {
    let mut bytes = [0; 0x100];
    record(&mut bytes, 0x20, next, 0x8110);
    let mut output = [Frame::default(); 8];
    let trace = walk(
      &StackMemory {
        address: 0x1000,
        bytes: &bytes,
      },
      &StackMemory {
        address: 0x1000,
        bytes: &[0xff; 0x100],
      },
      &[],
      &Registers {
        pc: 0x8120,
        sp: 0x1000,
        fp,
        ..Registers::default()
      },
      AddressRange::new(0x1000, 0x100).unwrap(),
      &mut output,
    );
    assert_eq!(trace.stop_reason, expected);
  }
  let mut output = [Frame::default(); 8];
  let trace = walk(
    &StackMemory {
      address: 0x1000,
      bytes: &[],
    },
    &StackMemory {
      address: 0x1000,
      bytes: &[0; 0x100],
    },
    &[],
    &Registers {
      pc: 0x8120,
      sp: 0x1000,
      fp: 0x1020,
      ..Registers::default()
    },
    AddressRange::new(0x1000, 0x100).unwrap(),
    &mut output,
  );
  assert_eq!(trace.stop_reason, StopReason::UnreadableMemory);
}

#[test]
fn bounded_output_and_frameless_stack_ranges() {
  let metadata = table(true);
  for (size, capacity, reason) in [
    (0x100, 0, StopReason::OutputFull),
    (0x100, 1, StopReason::OutputFull),
    (0x10, 8, StopReason::OutOfStackBounds),
  ] {
    let mut output = [Frame::default(); 8];
    let trace = walk(
      &StackMemory {
        address: 0x1000,
        bytes: &[0; 0x100],
      },
      &StackMemory {
        address: 0x9000,
        bytes: &metadata,
      },
      &[image()],
      &Registers {
        pc: 0x8190,
        sp: 0x1000,
        lr: 0x8110,
        ..Registers::default()
      },
      AddressRange::new(0x1000, size).unwrap(),
      &mut output[.. capacity],
    );
    assert_eq!(trace.stop_reason, reason);
  }
}
