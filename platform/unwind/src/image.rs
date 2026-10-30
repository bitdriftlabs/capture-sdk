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
}

impl ImageInfo {
  /// Surveys one image supplied by the caller's loader integration. Run this before suspending
  /// threads or entering a crash handler; it deliberately does not enumerate dyld's mutable list.
  /// The caller must keep the image mapped and consistent throughout this survey.
  ///
  /// Load commands describe the image's segments, sections and identity. Their count and total
  /// size come from `mach_header_64`; each command supplies its own size. Work scales with those
  /// declared tables, validated structurally without a separate command-count or byte-size cap.
  ///
  /// Mach-O definitions: https://github.com/apple-oss-distributions/xnu/blob/main/EXTERNAL_HEADERS/mach-o/loader.h
  pub fn read(memory: &impl MemoryReader, header_address: u64) -> Result<Self, ImageError> {
    let header = read::<32>(memory, header_address)?;
    if u32_at(&header, 0) != Some(MH_MAGIC_64) {
      return Err(ImageError::InvalidHeader);
    }
    if u32_at(&header, 4) != Some(CPU_TYPE_ARM64) {
      return Err(ImageError::UnsupportedArchitecture);
    }
    let command_count = u32_at(&header, 16).ok_or(ImageError::InvalidHeader)?;
    let load_commands_size = u32_at(&header, 20).ok_or(ImageError::InvalidHeader)?;
    // Each load command needs at least its 8-byte kind/size header.
    if command_count > load_commands_size / 8 {
      return Err(ImageError::InvalidLoadCommand);
    }
    let mut command_address = header_address
      .checked_add(HEADER_SIZE)
      .ok_or(ImageError::AddressOverflow)?;
    let commands_end = command_address
      .checked_add(u64::from(load_commands_size))
      .ok_or(ImageError::AddressOverflow)?;
    let mut uuid = None;
    let mut text = None;
    let mut unwind_info = None;
    let mut eh_frame = None;

    for _ in 0 .. command_count {
      if commands_end.saturating_sub(command_address) < 8 {
        return Err(ImageError::InvalidLoadCommand);
      }
      let command = read::<8>(memory, command_address)?;
      let kind = u32_at(&command, 0).ok_or(ImageError::InvalidLoadCommand)?;
      let command_size = u64::from(u32_at(&command, 4).ok_or(ImageError::InvalidLoadCommand)?);
      if command_size < 8
        || !command_size.is_multiple_of(8)
        || command_size > commands_end - command_address
      {
        return Err(ImageError::InvalidLoadCommand);
      }
      match kind {
        LC_UUID => {
          if command_size < 24 || uuid.is_some() {
            return Err(ImageError::InvalidLoadCommand);
          }
          uuid = Some(read::<16>(memory, command_address + 8)?);
        },
        LC_SEGMENT_64 => {
          if command_size < SEGMENT_SIZE {
            return Err(ImageError::InvalidLoadCommand);
          }
          let segment = read::<72>(memory, command_address)?;
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
          if u64::from(sections) > (command_size - SEGMENT_SIZE) / SECTION_SIZE {
            return Err(ImageError::InvalidLoadCommand);
          }
          for index in 0 .. sections {
            let section = read::<80>(
              memory,
              command_address + SEGMENT_SIZE + u64::from(index) * SECTION_SIZE,
            )?;
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
      command_address += command_size;
    }
    if command_address != commands_end {
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
