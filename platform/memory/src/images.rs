// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt
// Segment map of the loaded images, surveyed once per dump and shared by the stages that attribute
// addresses to images. Headers and load commands are copied with safe reads, so an image unloaded
// mid-survey is skipped rather than faulting.

#[cfg(test)]
#[path = "./images_test.rs"]
mod tests;

use crate::safe_read;
use crate::sys::dyld;

/// From `<mach-o/loader.h>`.
const MH_MAGIC_64: u32 = 0xfeed_facf;
const LC_SEGMENT_64: u32 = 0x19;
const LC_UUID: u32 = 0x1b;

/// `struct mach_header_64` from `<mach-o/loader.h>`: its size and the offsets of `ncmds` and
/// `sizeofcmds`.
const HEADER_SIZE: usize = 32;
const HEADER_NCMDS: usize = 16;
const HEADER_SIZEOFCMDS: usize = 20;

/// `struct load_command` from `<mach-o/loader.h>`: the offset of `cmdsize`.
const COMMAND_CMDSIZE: usize = 4;

/// `struct segment_command_64` from `<mach-o/loader.h>`: its size and the offsets of `segname`,
/// `vmaddr`, `vmsize` and `nsects`.
const SEGMENT_COMMAND_SIZE: usize = 72;
const SEGMENT_SEGNAME: usize = 8;
const SEGMENT_NAME_SIZE: usize = 16;
const SEGMENT_VMADDR: usize = 24;
const SEGMENT_VMSIZE: usize = 32;
const SEGMENT_NSECTS: usize = 64;
/// `struct section_64` from `<mach-o/loader.h>`: its size and the offsets of `addr` and `size`.
/// `sectname` is its first field.
const SECTION_SIZE: usize = 80;
const SECTION_ADDR: usize = 32;
const SECTION_BYTES: usize = 40;
/// The section listing the classes an image defines.
const CLASS_LIST: &[u8] = b"__objc_classlist";
/// `SEG_LINKEDIT` from `<mach-o/loader.h>`. In the shared cache every image's `__LINKEDIT` is the
/// same range, so it is left out to keep segments from overlapping.
const LINKEDIT: &[u8] = b"__LINKEDIT";
/// `SEG_PAGEZERO` from `<mach-o/loader.h>`: the unmapped range below the executable, which no
/// address of the image falls in.
const PAGEZERO: &[u8] = b"__PAGEZERO";
const UUID_COMMAND_SIZE: usize = 24;
const UUID_SIZE: usize = 16;

//
// Segment
//

struct Segment {
  start: usize,
  end: usize,
  image: usize,
}

//
// LoadedImage
//

struct LoadedImage {
  name: String,
  header: usize,
  uuid: Option<[u8; UUID_SIZE]>,
}

//
// LoadCommands
//

#[derive(Default)]
struct LoadCommands {
  /// The (vmaddr, vmsize) of each mapped segment, zero-fill ones included.
  segments: Vec<(usize, usize)>,
  /// The (addr, size) of each `__objc_classlist` section.
  class_lists: Vec<(usize, usize)>,
  uuid: Option<[u8; UUID_SIZE]>,
}

//
// ImageTable
//

pub struct ImageTable {
  segments: Vec<Segment>,
  images: Vec<LoadedImage>,
}

impl ImageTable {
  pub fn survey() -> Self {
    let mut segments = Vec::new();
    let mut images = Vec::new();
    for index in 0 .. dyld::image_count() {
      let (Some((header, slide)), Some(path)) =
        (dyld::image_header(index), dyld::image_path(index))
      else {
        continue;
      };
      let image = images.len();
      let commands = load_commands(header);
      images.push(LoadedImage {
        name: binary_name(&path).to_string(),
        header,
        uuid: commands.uuid,
      });
      for (address, size) in commands.segments {
        let start = address.wrapping_add(slide);
        segments.push(Segment {
          start,
          end: start.saturating_add(size),
          image,
        });
      }
    }
    segments.sort_unstable_by_key(|segment| segment.start);
    log::debug!(
      "surveyed {} images, {} segments",
      images.len(),
      segments.len()
    );
    Self { segments, images }
  }

  pub const fn len(&self) -> usize {
    self.images.len()
  }

  /// The binary's name only: paths carry the app container and user directories.
  pub fn name(&self, image: usize) -> Option<&str> {
    self.images.get(image).map(|image| image.name.as_str())
  }

  pub fn uuid(&self, image: usize) -> Option<&[u8; UUID_SIZE]> {
    self.images.get(image)?.uuid.as_ref()
  }

  /// `address` relative to the image's mach header, which removes the ASLR slide.
  pub fn offset(&self, image: usize, address: usize) -> Option<u64> {
    let offset = address.checked_sub(self.images.get(image)?.header)?;
    Some(offset as u64)
  }

  /// The image owning `address`. Does not allocate.
  pub fn owner(&self, address: usize) -> Option<usize> {
    let index = self
      .segments
      .partition_point(|segment| segment.end <= address);
    self
      .segments
      .get(index)
      .filter(|segment| segment.start <= address)
      .map(|segment| segment.image)
  }

  /// Calls `visit` with each image overlapping [start, end) and the bytes of the overlap, once
  /// per overlapping segment. Does not allocate.
  pub fn for_each_overlap(&self, start: usize, end: usize, mut visit: impl FnMut(usize, u64)) {
    let first = self
      .segments
      .partition_point(|segment| segment.end <= start);
    for segment in self.segments[first ..]
      .iter()
      .take_while(|segment| segment.start < end)
    {
      let from = segment.start.max(start);
      let to = segment.end.min(end);
      if to > from {
        visit(segment.image, (to - from) as u64);
      }
    }
  }
}

/// The `__objc_classlist` sections of the loaded images, as (address, bytes): arrays of pointers
/// to the classes each image defines.
pub fn class_lists() -> Vec<(usize, usize)> {
  (0 .. dyld::image_count())
    .filter_map(dyld::image_header)
    .flat_map(|(header, slide)| {
      load_commands(header)
        .class_lists
        .into_iter()
        .map(move |(address, size)| (address.wrapping_add(slide), size))
    })
    .collect()
}

/// A Mach-O name field holds up to 16 bytes, NUL-padded.
fn name_is(field: &[u8], name: &[u8]) -> bool {
  field.split(|byte| *byte == 0).next() == Some(name)
}

fn is_left_out(name: &[u8]) -> bool {
  name_is(name, LINKEDIT) || name_is(name, PAGEZERO)
}

pub fn binary_name(path: &str) -> &str {
  path.rsplit('/').next().unwrap_or(path)
}

fn load_commands(header: usize) -> LoadCommands {
  let mut header_bytes = [0u8; HEADER_SIZE];
  if !safe_read::read(header, &mut header_bytes) || read_u32(&header_bytes, 0) != MH_MAGIC_64 {
    return LoadCommands::default();
  }
  let command_count = read_u32(&header_bytes, HEADER_NCMDS);
  let commands_size = read_u32(&header_bytes, HEADER_SIZEOFCMDS) as usize;
  let mut commands = vec![0u8; commands_size];
  if !safe_read::read(header + HEADER_SIZE, &mut commands) {
    return LoadCommands::default();
  }

  let mut parsed = LoadCommands::default();
  let mut offset = 0;
  for _ in 0 .. command_count {
    let command = read_u32(&commands, offset);
    let command_size = read_u32(&commands, offset + COMMAND_CMDSIZE) as usize;
    if command_size == 0 || offset + command_size > commands.len() {
      break;
    }
    if command == LC_SEGMENT_64 && command_size >= SEGMENT_COMMAND_SIZE {
      let address = read_u64(&commands, offset + SEGMENT_VMADDR);
      let size = read_u64(&commands, offset + SEGMENT_VMSIZE);
      if let (Ok(address), Ok(size)) = (usize::try_from(address), usize::try_from(size))
        && size > 0
        && !is_left_out(
          &commands[offset + SEGMENT_SEGNAME .. offset + SEGMENT_SEGNAME + SEGMENT_NAME_SIZE],
        )
      {
        parsed.segments.push((address, size));
      }
      parsed.class_lists.extend(class_list_sections(
        &commands[offset .. offset + command_size],
      ));
    }
    if command == LC_UUID && command_size >= UUID_COMMAND_SIZE {
      parsed.uuid = commands
        .get(offset + 8 .. offset + 8 + UUID_SIZE)
        .and_then(|bytes| bytes.try_into().ok());
    }
    offset += command_size;
  }
  parsed
}

/// The (addr, size) of the `__objc_classlist` sections a segment command lists.
fn class_list_sections(segment: &[u8]) -> impl Iterator<Item = (usize, usize)> + '_ {
  let count = read_u32(segment, SEGMENT_NSECTS) as usize;
  segment[SEGMENT_COMMAND_SIZE ..]
    .as_chunks::<SECTION_SIZE>()
    .0
    .iter()
    .take(count)
    .filter(|section| name_is(&section[.. SEGMENT_NAME_SIZE], CLASS_LIST))
    .filter_map(|section| {
      let address = usize::try_from(read_u64(section, SECTION_ADDR)).ok()?;
      let size = usize::try_from(read_u64(section, SECTION_BYTES)).ok()?;
      Some((address, size))
    })
}

fn read_u32(bytes: &[u8], offset: usize) -> u32 {
  bytes
    .get(offset .. offset + 4)
    .and_then(|slice| slice.try_into().ok())
    .map_or(0, u32::from_ne_bytes)
}

fn read_u64(bytes: &[u8], offset: usize) -> u64 {
  bytes
    .get(offset .. offset + 8)
    .and_then(|slice| slice.try_into().ok())
    .map_or(0, u64::from_ne_bytes)
}
