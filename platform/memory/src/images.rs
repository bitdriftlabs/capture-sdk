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

const MH_MAGIC_64: u32 = 0xfeed_facf;
const LC_SEGMENT_64: u32 = 0x19;
const LC_UUID: u32 = 0x1b;
const HEADER_SIZE: usize = 32;
const SEGMENT_COMMAND_SIZE: usize = 72;
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
  /// The (vmaddr, vmsize) of each segment with file content.
  segments: Vec<(usize, usize)>,
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

pub fn binary_name(path: &str) -> &str {
  path.rsplit('/').next().unwrap_or(path)
}

fn load_commands(header: usize) -> LoadCommands {
  let mut header_bytes = [0u8; HEADER_SIZE];
  if !safe_read::read(header, &mut header_bytes) || read_u32(&header_bytes, 0) != MH_MAGIC_64 {
    return LoadCommands::default();
  }
  let command_count = read_u32(&header_bytes, 16);
  let commands_size = read_u32(&header_bytes, 20) as usize;
  let mut commands = vec![0u8; commands_size];
  if !safe_read::read(header + HEADER_SIZE, &mut commands) {
    return LoadCommands::default();
  }

  let mut parsed = LoadCommands::default();
  let mut offset = 0;
  for _ in 0 .. command_count {
    let command = read_u32(&commands, offset);
    let command_size = read_u32(&commands, offset + 4) as usize;
    if command_size == 0 || offset + command_size > commands.len() {
      break;
    }
    if command == LC_SEGMENT_64 && command_size >= SEGMENT_COMMAND_SIZE {
      let address = read_u64(&commands, offset + 24);
      let size = read_u64(&commands, offset + 32);
      let file_size = read_u64(&commands, offset + 48);
      if let (Ok(address), Ok(size)) = (usize::try_from(address), usize::try_from(size))
        && size > 0
        && file_size > 0
      {
        parsed.segments.push((address, size));
      }
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
