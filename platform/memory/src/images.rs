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
const HEADER_SIZE: usize = 32;
const SEGMENT_COMMAND_SIZE: usize = 72;

//
// Segment
//

struct Segment {
  start: usize,
  end: usize,
  image: usize,
}

//
// ImageTable
//

pub struct ImageTable {
  segments: Vec<Segment>,
  names: Vec<String>,
}

impl ImageTable {
  pub fn survey() -> Self {
    let mut segments = Vec::new();
    let mut names = Vec::new();
    for index in 0 .. dyld::image_count() {
      let (Some((header, slide)), Some(path)) =
        (dyld::image_header(index), dyld::image_path(index))
      else {
        continue;
      };
      let image = names.len();
      names.push(binary_name(&path).to_string());
      for (address, size) in file_backed_segments(header) {
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
      names.len(),
      segments.len()
    );
    Self { segments, names }
  }

  pub const fn len(&self) -> usize {
    self.names.len()
  }

  /// The binary's name only: paths carry the app container and user directories.
  pub fn name(&self, image: usize) -> Option<&str> {
    self.names.get(image).map(String::as_str)
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

/// The (vmaddr, vmsize) of each segment with file content.
fn file_backed_segments(header: usize) -> Vec<(usize, usize)> {
  let mut header_bytes = [0u8; HEADER_SIZE];
  if !safe_read::read(header, &mut header_bytes) || read_u32(&header_bytes, 0) != MH_MAGIC_64 {
    return Vec::new();
  }
  let command_count = read_u32(&header_bytes, 16);
  let commands_size = read_u32(&header_bytes, 20) as usize;
  let mut commands = vec![0u8; commands_size];
  if !safe_read::read(header + HEADER_SIZE, &mut commands) {
    return Vec::new();
  }

  let mut segments = Vec::new();
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
        segments.push((address, size));
      }
    }
    offset += command_size;
  }
  segments
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
