// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

#![allow(clippy::unwrap_used)]

use super::{ImageError, ImageInfo};
use crate::memory::{AddressRange, StackMemory};
use crate::test_support::count_allocations;

const HEADER: u64 = 0x2_0000_0000;
const PREFERRED: u64 = 0x1_0000_0000;

fn put_u32(bytes: &mut [u8], offset: usize, value: u32) {
  bytes[offset .. offset + 4].copy_from_slice(&value.to_le_bytes());
}

fn put_u64(bytes: &mut [u8], offset: usize, value: u64) {
  bytes[offset .. offset + 8].copy_from_slice(&value.to_le_bytes());
}

fn fixture() -> [u8; 288] {
  let mut bytes = [0; 288];
  put_u32(&mut bytes, 0, 0xfeed_facf);
  put_u32(&mut bytes, 4, 0x0100_000c);
  put_u32(&mut bytes, 16, 2);
  put_u32(&mut bytes, 20, 256);

  put_u32(&mut bytes, 32, 0x19);
  put_u32(&mut bytes, 36, 232);
  bytes[40 .. 46].copy_from_slice(b"__TEXT");
  put_u64(&mut bytes, 56, PREFERRED);
  put_u64(&mut bytes, 64, 0x1000);
  put_u64(&mut bytes, 80, 0x1000);
  put_u32(&mut bytes, 96, 2);

  bytes[104 .. 117].copy_from_slice(b"__unwind_info");
  put_u64(&mut bytes, 136, PREFERRED + 0x400);
  put_u64(&mut bytes, 144, 32);
  bytes[184 .. 194].copy_from_slice(b"__eh_frame");
  put_u64(&mut bytes, 216, PREFERRED + 0x500);
  put_u64(&mut bytes, 224, 64);

  put_u32(&mut bytes, 264, 0x1b);
  put_u32(&mut bytes, 268, 24);
  bytes[272 .. 288].copy_from_slice(&[7; 16]);
  bytes
}

fn parse(bytes: &[u8]) -> Result<ImageInfo, ImageError> {
  ImageInfo::read(
    &StackMemory {
      address: HEADER,
      bytes,
    },
    HEADER,
  )
}

#[test]
fn prepares_uuid_and_relocated_unwind_section_ranges() {
  let info = parse(&fixture()).unwrap();
  assert_eq!(info.header_address, HEADER);
  assert_eq!(info.uuid, Some([7; 16]));
  assert_eq!(info.text, AddressRange::new(HEADER, 0x1000).unwrap());
  assert_eq!(info.unwind_info, AddressRange::new(HEADER + 0x400, 32));
  assert_eq!(info.eh_frame, AddressRange::new(HEADER + 0x500, 64));
}

#[test]
fn image_survey_does_not_allocate() {
  let bytes = fixture();
  let (result, allocations) = count_allocations(|| parse(&bytes));
  assert!(result.is_ok());
  assert_eq!(allocations, 0);
}

#[test]
fn missing_uuid_and_unwind_sections_are_not_fabricated() {
  let mut bytes = fixture();
  put_u32(&mut bytes, 16, 1);
  put_u32(&mut bytes, 20, 72);
  put_u32(&mut bytes, 36, 72);
  put_u32(&mut bytes, 96, 0);
  let info = parse(&bytes).unwrap();
  assert_eq!(info.uuid, None);
  assert_eq!(info.unwind_info, None);
  assert_eq!(info.eh_frame, None);
}

#[test]
fn rejects_bad_magic_and_wrong_architecture() {
  let mut bytes = fixture();
  put_u32(&mut bytes, 0, 0);
  assert_eq!(parse(&bytes), Err(ImageError::InvalidHeader));
  put_u32(&mut bytes, 0, 0xfeed_facf);
  put_u32(&mut bytes, 4, 0x0100_0007);
  assert_eq!(parse(&bytes), Err(ImageError::UnsupportedArchitecture));
}

#[test]
fn bounds_work_for_corrupt_load_command_counts_and_sizes() {
  let mut bytes = fixture();
  put_u32(&mut bytes, 16, u32::MAX);
  assert_eq!(parse(&bytes), Err(ImageError::LimitExceeded));
  put_u32(&mut bytes, 16, 2);
  put_u32(&mut bytes, 20, u32::MAX);
  assert_eq!(parse(&bytes), Err(ImageError::LimitExceeded));
}

#[test]
fn rejects_truncated_or_nonprogressing_load_commands() {
  for size in [0, 7, 8, 71, 240, u32::MAX] {
    let mut bytes = fixture();
    put_u32(&mut bytes, 36, size);
    assert_eq!(parse(&bytes), Err(ImageError::InvalidLoadCommand));
  }
  let bytes = fixture();
  assert_eq!(parse(&bytes[.. 287]), Err(ImageError::UnreadableMemory));
}

#[test]
fn validates_section_count_against_its_own_segment_command() {
  let mut bytes = fixture();
  put_u32(&mut bytes, 96, 3);
  assert_eq!(parse(&bytes), Err(ImageError::InvalidLoadCommand));
}

#[test]
fn rejects_sections_outside_their_declared_segment() {
  for (address, size) in [
    (PREFERRED - 1, 32),
    (PREFERRED + 0xff0, 32),
    (u64::MAX - 15, 32),
  ] {
    let mut bytes = fixture();
    put_u64(&mut bytes, 136, address);
    put_u64(&mut bytes, 144, size);
    assert_eq!(parse(&bytes), Err(ImageError::InvalidSection));
  }
}

#[test]
fn rejects_duplicate_unwind_sections() {
  let mut bytes = fixture();
  bytes[184 .. 200].fill(0);
  bytes[184 .. 197].copy_from_slice(b"__unwind_info");
  assert_eq!(parse(&bytes), Err(ImageError::InvalidSection));
}

#[test]
fn requires_a_file_backed_text_segment_for_slide_calculation() {
  let mut bytes = fixture();
  bytes[40 .. 46].copy_from_slice(b"__DATA");
  assert_eq!(parse(&bytes), Err(ImageError::MissingTextSegment));
  bytes[40 .. 46].copy_from_slice(b"__TEXT");
  put_u64(&mut bytes, 72, 4096);
  assert_eq!(parse(&bytes), Err(ImageError::InvalidLoadCommand));
}

#[test]
fn every_truncated_fixture_is_rejected_without_panicking() {
  let bytes = fixture();
  for length in 0 .. bytes.len() {
    assert!(parse(&bytes[.. length]).is_err());
  }
}

#[test]
fn detects_runtime_section_address_overflow() {
  let bytes = fixture();
  let header_address = u64::MAX - 0x800;
  let memory = StackMemory {
    address: header_address,
    bytes: &bytes,
  };
  assert_eq!(
    ImageInfo::read(&memory, header_address),
    Err(ImageError::AddressOverflow)
  );
}
