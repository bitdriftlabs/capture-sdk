// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

#[cfg(test)]
#[path = "./image_test.rs"]
mod tests;

use crate::memory::{AddressRange, MemoryReader};

const MH_MAGIC_64: u32 = 0xfeed_facf;
const CPU_TYPE_ARM64: u32 = 0x0100_000c;
const LC_SEGMENT_64: u32 = 0x19;
const LC_UUID: u32 = 0x1b;
const HEADER_SIZE: u64 = 32;
const SEGMENT_SIZE: u64 = 72;
const SECTION_SIZE: u64 = 80;
const MAX_COMMANDS: u32 = 4096;
const MAX_COMMAND_BYTES: u32 = 1024 * 1024;

//
// ImageInfo
//

/// Metadata copied out of a loaded ARM64 Mach-O image during normal execution. All ranges use
/// runtime addresses. These values own no mapped pointers and can be retained after an image
/// unload; reading unwind bytes still requires a reader and a separately managed image lifetime or
/// copy.
#[derive(Clone, Copy, Debug, Eq, PartialEq)]
pub struct ImageInfo {
  pub header_address: u64,
  pub uuid: Option<[u8; 16]>,
  /// The __TEXT mapping, not an assertion that every byte in it is executable.
  pub text: AddressRange,
  pub unwind_info: Option<AddressRange>,
  pub eh_frame: Option<AddressRange>,
}

#[derive(Clone, Copy, Debug, Eq, PartialEq)]
pub enum ImageError {
  UnreadableMemory,
  InvalidHeader,
  UnsupportedArchitecture,
  InvalidLoadCommand,
  InvalidSection,
  MissingTextSegment,
  AddressOverflow,
  LimitExceeded,
}

impl ImageInfo {
  /// Surveys one image supplied by the caller's loader integration. Run this before suspending
  /// threads or entering a crash handler; it deliberately does not enumerate dyld's mutable list.
  pub fn read(memory: &impl MemoryReader, header_address: u64) -> Result<Self, ImageError> {
    let header = read::<32>(memory, header_address)?;
    if u32_at(&header, 0) != Some(MH_MAGIC_64) {
      return Err(ImageError::InvalidHeader);
    }
    if u32_at(&header, 4) != Some(CPU_TYPE_ARM64) {
      return Err(ImageError::UnsupportedArchitecture);
    }
    let count = u32_at(&header, 16).ok_or(ImageError::InvalidHeader)?;
    let size = u32_at(&header, 20).ok_or(ImageError::InvalidHeader)?;
    if count > MAX_COMMANDS || size > MAX_COMMAND_BYTES {
      return Err(ImageError::LimitExceeded);
    }
    let mut at = header_address
      .checked_add(HEADER_SIZE)
      .ok_or(ImageError::AddressOverflow)?;
    let end = at
      .checked_add(u64::from(size))
      .ok_or(ImageError::AddressOverflow)?;
    let mut uuid = None;
    let mut text = None;
    let mut unwind_info = None;
    let mut eh_frame = None;

    for _ in 0 .. count {
      if end.saturating_sub(at) < 8 {
        return Err(ImageError::InvalidLoadCommand);
      }
      let command = read::<8>(memory, at)?;
      let kind = u32_at(&command, 0).ok_or(ImageError::InvalidLoadCommand)?;
      let size = u64::from(u32_at(&command, 4).ok_or(ImageError::InvalidLoadCommand)?);
      if size < 8 || !size.is_multiple_of(8) || size > end - at {
        return Err(ImageError::InvalidLoadCommand);
      }
      match kind {
        LC_UUID => {
          if size < 24 || uuid.is_some() {
            return Err(ImageError::InvalidLoadCommand);
          }
          uuid = Some(read::<16>(memory, at + 8)?);
        },
        LC_SEGMENT_64 => {
          if size < SEGMENT_SIZE {
            return Err(ImageError::InvalidLoadCommand);
          }
          let segment = read::<72>(memory, at)?;
          let name = segment.get(8 .. 24).ok_or(ImageError::InvalidLoadCommand)?;
          let address = u64_at(&segment, 24).ok_or(ImageError::InvalidLoadCommand)?;
          let length = u64_at(&segment, 32).ok_or(ImageError::InvalidLoadCommand)?;
          if name.starts_with(b"__TEXT\0") {
            if text.is_some() || u64_at(&segment, 40) != Some(0) || u64_at(&segment, 48) == Some(0)
            {
              return Err(ImageError::InvalidLoadCommand);
            }
            text = Some(AddressRange::new(address, length).ok_or(ImageError::InvalidLoadCommand)?);
          }
          let sections = u32_at(&segment, 64).ok_or(ImageError::InvalidLoadCommand)?;
          if u64::from(sections) > (size - SEGMENT_SIZE) / SECTION_SIZE {
            return Err(ImageError::InvalidLoadCommand);
          }
          for index in 0 .. sections {
            let section = read::<80>(memory, at + SEGMENT_SIZE + u64::from(index) * SECTION_SIZE)?;
            let name = section.get(.. 16).ok_or(ImageError::InvalidSection)?;
            let destination = if name.starts_with(b"__unwind_info\0") {
              &mut unwind_info
            } else if name.starts_with(b"__eh_frame\0") {
              &mut eh_frame
            } else {
              continue;
            };
            let section_address = u64_at(&section, 32).ok_or(ImageError::InvalidSection)?;
            let section_size = u64_at(&section, 40).ok_or(ImageError::InvalidSection)?;
            if section_size == 0 {
              continue;
            }
            let segment_range =
              AddressRange::new(address, length).ok_or(ImageError::InvalidSection)?;
            if destination.is_some() || !segment_range.contains(section_address, section_size) {
              return Err(ImageError::InvalidSection);
            }
            *destination = AddressRange::new(section_address, section_size);
          }
        },
        _ => {},
      }
      at += size;
    }
    if at != end {
      return Err(ImageError::InvalidLoadCommand);
    }
    let preferred_text = text.ok_or(ImageError::MissingTextSegment)?;
    let relocate = |range: AddressRange| {
      // The header is at the beginning of the file-backed __TEXT mapping. Subtraction before
      // addition avoids representing the ASLR slide as a signed integer.
      let start = range
        .start()
        .checked_sub(preferred_text.start())
        .and_then(|offset| header_address.checked_add(offset))
        .ok_or(ImageError::AddressOverflow)?;
      AddressRange::new(start, range.size()).ok_or(ImageError::AddressOverflow)
    };
    Ok(Self {
      header_address,
      uuid,
      text: relocate(preferred_text)?,
      unwind_info: unwind_info.map(relocate).transpose()?,
      eh_frame: eh_frame.map(relocate).transpose()?,
    })
  }
}

fn read<const SIZE: usize>(
  memory: &impl MemoryReader,
  address: u64,
) -> Result<[u8; SIZE], ImageError> {
  let mut bytes = [0; SIZE];
  if !memory.read(address, &mut bytes) {
    return Err(ImageError::UnreadableMemory);
  }
  Ok(bytes)
}

fn u32_at(bytes: &[u8], offset: usize) -> Option<u32> {
  Some(u32::from_le_bytes(
    bytes
      .get(offset .. offset.checked_add(4)?)?
      .try_into()
      .ok()?,
  ))
}

fn u64_at(bytes: &[u8], offset: usize) -> Option<u64> {
  Some(u64::from_le_bytes(
    bytes
      .get(offset .. offset.checked_add(8)?)?
      .try_into()
      .ok()?,
  ))
}
