// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

#![allow(clippy::unwrap_used)]

use super::{ImageError, ImageInfo};
use crate::memory::{AddressRange, MemoryReader, StackMemory};
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

  bytes[104 .. 110].copy_from_slice(b"__text");
  put_u64(&mut bytes, 136, PREFERRED + 0x400);
  put_u64(&mut bytes, 144, 32);
  bytes[184 .. 193].copy_from_slice(b"__cstring");
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
fn reads_uuid_and_runtime_text_range() {
  let info = parse(&fixture()).unwrap();
  assert_eq!(info.uuid, Some([7; 16]));
  assert_eq!(info.text, AddressRange::new(HEADER, 0x1000).unwrap());
}

#[test]
fn image_survey_does_not_allocate() {
  let bytes = fixture();
  let (result, allocations) = count_allocations(|| parse(&bytes));
  assert!(result.is_ok());
  assert_eq!(allocations, 0);
}

#[test]
fn accepts_a_text_segment_without_sections_or_uuid() {
  let mut bytes = fixture();
  put_u32(&mut bytes, 16, 1);
  put_u32(&mut bytes, 20, 72);
  put_u32(&mut bytes, 36, 72);
  put_u32(&mut bytes, 96, 0);
  let info = parse(&bytes).unwrap();
  assert_eq!(info.uuid, None);
  assert_eq!(info.text, AddressRange::new(HEADER, 0x1000).unwrap());
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
fn rejects_command_counts_that_cannot_fit_in_the_declared_table() {
  let mut bytes = fixture();
  put_u32(&mut bytes, 16, u32::MAX);
  assert_eq!(parse(&bytes), Err(ImageError::InvalidLoadCommand));
}

#[test]
fn accepts_more_than_4096_structurally_valid_commands() {
  let mut bytes = fixture().to_vec();
  for _ in 0 .. 4095 {
    bytes.extend_from_slice(&0xffff_u32.to_le_bytes());
    bytes.extend_from_slice(&8_u32.to_le_bytes());
  }
  let table_size = u32::try_from(bytes.len() - 32).unwrap();
  put_u32(&mut bytes, 16, 4097);
  put_u32(&mut bytes, 20, table_size);
  put_u64(&mut bytes, 64, u64::from(table_size) + 32);
  put_u64(&mut bytes, 80, u64::from(table_size) + 32);
  assert!(parse(&bytes).is_ok());
}

#[test]
fn accepts_structurally_valid_command_tables_larger_than_one_mib() {
  let mut bytes = fixture().to_vec();
  let command_size: u32 = 1024 * 1024;
  bytes.extend_from_slice(&0xffff_u32.to_le_bytes());
  bytes.extend_from_slice(&command_size.to_le_bytes());
  bytes.resize(288 + command_size as usize, 0);
  put_u32(&mut bytes, 16, 3);
  put_u32(&mut bytes, 20, 256 + command_size);
  put_u64(&mut bytes, 64, u64::from(command_size) + 288);
  put_u64(&mut bytes, 80, u64::from(command_size) + 288);
  assert!(parse(&bytes).is_ok());
}

#[test]
fn validates_remaining_space_on_each_iteration_and_consumes_the_entire_table() {
  for table_size in [232, 240, 264, u32::MAX] {
    let mut bytes = fixture();
    put_u32(&mut bytes, 20, table_size);
    assert_eq!(parse(&bytes), Err(ImageError::InvalidLoadCommand));
  }
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
fn image_identity_does_not_require_readable_section_records() {
  struct UnreadableSections<'a>(StackMemory<'a>);

  impl MemoryReader for UnreadableSections<'_> {
    fn read(&self, address: u64, destination: &mut [u8]) -> bool {
      // Leave the Mach header, segment command and UUID readable, but not the section records.
      if (HEADER + 104 .. HEADER + 264).contains(&address) {
        return false;
      }
      self.0.read(address, destination)
    }
  }

  let bytes = fixture();
  let memory = UnreadableSections(StackMemory {
    address: HEADER,
    bytes: &bytes,
  });
  let info = ImageInfo::read(&memory, HEADER).unwrap();
  assert_eq!(info.uuid, Some([7; 16]));
  assert_eq!(info.text, AddressRange::new(HEADER, 0x1000).unwrap());
}

#[test]
fn requires_a_file_backed_text_segment_for_slide_calculation() {
  let mut bytes = fixture();
  bytes[40 .. 46].copy_from_slice(b"__DATA");
  assert_eq!(parse(&bytes), Err(ImageError::MissingTextSegment));
  bytes[40 .. 46].copy_from_slice(b"__TEXT");
  put_u64(&mut bytes, 72, 4096);
  assert_eq!(
    parse(&bytes),
    Err(ImageError::NonzeroTextFileOffset {
      in_shared_cache: false
    })
  );
}

#[test]
fn shared_cache_file_offsets_do_not_change_runtime_text_addresses() {
  let mut bytes = fixture();
  put_u32(&mut bytes, 12, 6); // MH_DYLIB
  put_u32(&mut bytes, 24, 0x8000_0000); // MH_DYLIB_IN_CACHE
  for file_offset in [0, 4096, 0x1234_5678, 0x7fff_ffff_0000] {
    put_u64(&mut bytes, 72, file_offset);
    let info = parse(&bytes).unwrap();
    assert_eq!(info.uuid, Some([7; 16]));
    assert_eq!(info.text, AddressRange::new(HEADER, 0x1000).unwrap());
  }
  // Being in the cache does not bypass structural checks.
  put_u32(&mut bytes, 96, 3);
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
fn detects_runtime_text_address_overflow() {
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
