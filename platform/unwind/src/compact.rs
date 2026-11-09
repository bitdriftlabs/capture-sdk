// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

//! Lookup in linked Mach-O `__unwind_info` sections, without allocating or borrowing mapped bytes.
//!
//! Layout: [`compact_unwind_encoding.h`][layout].
//!
//! [layout]:
//! https://github.com/llvm/llvm-project/raw/main/libunwind/include/mach-o/compact_unwind_encoding.h
//! Readers must keep the section consistent. Lookups validate the tables and selected interval;
//! they do not certify every entry of an untrusted section. Searches are bounded by table sizes.

#[cfg(test)]
#[path = "./compact_test.rs"]
pub(crate) mod tests;

use crate::memory::{AddressRange, MemoryReader};

const SECTION_VERSION: u32 = 1;
const REGULAR_PAGE: u32 = 2;
const COMPRESSED_PAGE: u32 = 3;
const PAGE_SIZE: u64 = 4096;
const HEADER_SIZE: u64 = 28;
const INDEX_ENTRY_SIZE: u64 = 12;
const COMPRESSED_FUNCTION_MASK: u32 = 0x00ff_ffff;

//
// LookupError
//

#[derive(Clone, Copy, Debug, Eq, PartialEq)]
pub enum LookupError {
  UnreadableMemory,
  InvalidSection,
  UnsupportedVersion,
  UnsupportedPage,
}

//
// CompactUnwind
//

/// A section range and its image's loaded Mach-O header address. All offsets in the section are
/// relative either to the section, to a second-level page or to the loaded image header.
#[derive(Clone, Copy, Debug)]
pub struct CompactUnwind {
  pub section: AddressRange,
  pub image_base: u64,
}

impl CompactUnwind {
  /// `pc` is the instruction being looked up. Callers subtract one from return addresses, but
  /// must leave the sampled PC unchanged. `None` means that no entry covers this instruction.
  pub fn lookup(&self, memory: &impl MemoryReader, pc: u64) -> Result<Option<u32>, LookupError> {
    // Function offsets in the index are relative to the loaded Mach-O header, not to this section.
    // Addresses before the image base or beyond the format's 32-bit offsets cannot match an entry.
    let Some(offset) = pc
      .checked_sub(self.image_base)
      .and_then(|v| u32::try_from(v).ok())
    else {
      return Ok(None);
    };
    // All reader offsets below are section-relative unless explicitly combined with a page offset.
    // Check declared arrays against the section before using their counts in searches.
    let reader = SectionReader {
      memory,
      range: self.section,
    };
    reader.bounds(0, HEADER_SIZE)?;
    if reader.u32(0)? != SECTION_VERSION {
      return Err(LookupError::UnsupportedVersion);
    }
    let common = u64::from(reader.u32(4)?);
    let common_count = reader.u32(8)?;
    reader.bounds(common, u64::from(common_count) * 4)?;
    let index = u64::from(reader.u32(20)?);
    let count = reader.u32(24)?;
    reader.bounds(index, u64::from(count) * INDEX_ENTRY_SIZE)?;
    // First-level entries select second-level pages, not individual unwind rules. Each contains
    // a function offset, page offset and LSDA index offset; this backtrace lookup does not use
    // LSDA. The final entry is a sentinel giving the end of the last page's covered range, so a
    // usable index needs at least one page entry plus that sentinel.
    if count < 2 {
      return Err(LookupError::InvalidSection);
    }
    // Choose the last page whose starting function offset is <= the target. A target before the
    // first entry has no predecessor; selecting the sentinel means it is beyond the covered range.
    let Some(selected) = predecessor(count, offset, |i| {
      reader.u32(index + u64::from(i) * INDEX_ENTRY_SIZE)
    })?
    else {
      return Ok(None);
    };
    if selected == count - 1 {
      return Ok(None);
    }
    let entry = index + u64::from(selected) * INDEX_ENTRY_SIZE;
    let start = reader.u32(entry)?;
    let end = reader.u32(entry + INDEX_ENTRY_SIZE)?;
    // The next first-level entry supplies this page's exclusive end, including for the last page.
    // Validate the selected interval; binary search alone does not validate an untrusted table.
    if start >= end || offset < start || offset >= end {
      return Err(LookupError::InvalidSection);
    }
    let page = u64::from(reader.u32(entry + 4)?);
    if page < HEADER_SIZE {
      return Err(LookupError::InvalidSection);
    }
    let kind = reader.u32(page)?;
    let header_size = match kind {
      REGULAR_PAGE => 8,
      COMPRESSED_PAGE => 12,
      _ => return Err(LookupError::UnsupportedPage),
    };
    reader.bounds(page, header_size)?;
    let entries = u64::from(reader.u16(page + 4)?);
    let entries_count = u32::from(reader.u16(page + 6)?);
    // Regular entries store a 32-bit function offset followed by a 32-bit encoding. Compressed
    // entries pack a 24-bit function delta and an 8-bit encoding index into one word.
    let stride = if kind == REGULAR_PAGE { 8 } else { 4 };
    if entries_count == 0 || entries < header_size {
      return Err(LookupError::InvalidSection);
    }
    reader.page_bounds(page, entries, u64::from(entries_count) * stride)?;
    // Normalize both layouts to image-relative function offsets for the same predecessor search.
    // Compressed deltas start at this page's first-level function offset, not at the page's bytes.
    let function = |i| {
      let value = reader.u32(page + entries + u64::from(i) * stride)?;
      if kind == REGULAR_PAGE {
        Ok(value)
      } else {
        start
          .checked_add(value & COMPRESSED_FUNCTION_MASK)
          .ok_or(LookupError::InvalidSection)
      }
    };
    // Within the selected page, choose the last rule starting at or before the target.
    let Some(selected) = predecessor(entries_count, offset, function)? else {
      return Ok(None);
    };
    let function_start = function(selected)?;
    // The next rule bounds this interval. For the page's last rule, use the end from level one.
    let function_end = if selected + 1 < entries_count {
      function(selected + 1)?
    } else {
      end
    };
    if function_start < start
      || function_start >= function_end
      || function_end > end
      || offset < function_start
      || offset >= function_end
    {
      return Err(LookupError::InvalidSection);
    }
    let entry = page + entries + u64::from(selected) * stride;
    if kind == REGULAR_PAGE {
      return reader.u32(entry + 4).map(Some);
    }
    // A compressed encoding index addresses a logical concatenation: section-wide common
    // encodings first, followed by encodings stored locally in this second-level page.
    let encoding_index = reader.u32(entry)? >> 24;
    if encoding_index < common_count {
      return reader.u32(common + u64::from(encoding_index) * 4).map(Some);
    }
    let local = u64::from(reader.u16(page + 8)?);
    let local_count = u32::from(reader.u16(page + 10)?);
    let local_index = encoding_index - common_count;
    if local < header_size || local_index >= local_count {
      return Err(LookupError::InvalidSection);
    }
    reader.page_bounds(page, local, u64::from(local_count) * 4)?;
    reader
      .u32(page + local + u64::from(local_index) * 4)
      .map(Some)
  }
}

/// Returns the index of the last value <= target in an ascending table, or None if none qualifies.
/// `value` reads a table entry by index and propagates read errors without inventing a value.
/// The search halves the remaining interval even for unsorted input; that bounds its work, but
/// does not guarantee a correct predecessor or certify the table's ordering in that case.
fn predecessor(
  count: u32,
  target: u32,
  value: impl Fn(u32) -> Result<u32, LookupError>,
) -> Result<Option<u32>, LookupError> {
  // Search [low, high): high is exclusive and may equal count, which is not a readable index.
  let mut low = 0;
  let mut high = count;
  while low < high {
    let mid = low + (high - low) / 2;
    if value(mid)? <= target {
      // This entry qualifies. Search to its right for a later qualifying entry.
      low = mid + 1;
    } else {
      // In a sorted table, this entry and everything after it are too large.
      high = mid;
    }
  }
  // low is the index of the first value > target, or count if all qualify. Subtract one for
  // its predecessor; checked_sub returns None when low is zero, including for an empty table.
  Ok(low.checked_sub(1))
}

//
// SectionReader
//

struct SectionReader<'a, M> {
  memory: &'a M,
  range: AddressRange,
}

impl<M: MemoryReader> SectionReader<'_, M> {
  const fn bounds(&self, offset: u64, length: u64) -> Result<(), LookupError> {
    if offset > self.range.size() || length > self.range.size() - offset {
      return Err(LookupError::InvalidSection);
    }
    Ok(())
  }

  const fn page_bounds(&self, page: u64, offset: u64, length: u64) -> Result<(), LookupError> {
    if offset > PAGE_SIZE || length > PAGE_SIZE - offset {
      return Err(LookupError::InvalidSection);
    }
    self.bounds(page + offset, length)
  }

  fn read<const N: usize>(&self, offset: u64) -> Result<[u8; N], LookupError> {
    self.bounds(offset, N as u64)?;
    let mut bytes = [0; N];
    if !self.memory.read(self.range.start() + offset, &mut bytes) {
      return Err(LookupError::UnreadableMemory);
    }
    Ok(bytes)
  }

  fn u32(&self, offset: u64) -> Result<u32, LookupError> {
    self.read(offset).map(u32::from_le_bytes)
  }

  fn u16(&self, offset: u64) -> Result<u16, LookupError> {
    self.read(offset).map(u16::from_le_bytes)
  }
}
