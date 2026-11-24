// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt
// Tables the census fills while zones are locked. Each one is allocated in full before the first
// lock and written without allocating afterwards.

use crate::mapped::MappedVec;
use crate::sys::objc::known_classes;
use crate::sys::vm_region::{VM_PROT_READ, region_short_info};

const MIN_CANDIDATE_CAPACITY_BITS: u32 = 14;
const MAX_CANDIDATE_CAPACITY_BITS: u32 = 17;
/// A hard probe limit keeps a saturated table from turning each insert into a full scan while a
/// zone is locked.
const CANDIDATE_PROBE_LIMIT: usize = 32;
const RANGE_CAPACITY: usize = 8192;

//
// Tally
//

#[derive(Clone, Copy, Default)]
pub struct Tally {
  pub blocks: u64,
  pub bytes: u64,
  /// Unnamed blocks this class was credited with. Ownership, not type: these bytes are already
  /// counted as unclassified.
  pub owned_blocks: u64,
  pub owned_bytes: u64,
}

//
// ClassTable
//

/// The classes the loaded images define, sorted by address and searched by bisection.
pub struct ClassTable {
  pub pointers: MappedVec<usize>,
  pub tallies: MappedVec<Tally>,
}

impl ClassTable {
  pub fn new() -> Option<Self> {
    let mut pointers = known_classes()?;
    pointers.sort_unstable();
    // SAFETY: a zeroed `Tally` is the default one.
    let tallies = unsafe { MappedVec::zeroed(pointers.len()) }?;
    Some(Self { pointers, tallies })
  }

  /// Index of the class at `pointer`. No allocations.
  pub fn find(&self, pointer: usize) -> Option<usize> {
    self.pointers.binary_search(&pointer).ok()
  }
}

//
// Resolution
//

#[derive(Clone, Copy, PartialEq, Eq, Debug)]
#[repr(u8)]
pub enum Resolution {
  Unresolved = 0,
  /// An `ObjC` or Swift class no image lists. `name` indexes the census names.
  Class,
  /// No type, only the image owning the first word. `name` is the image index.
  Image,
  /// A Swift heap object with no class, counted without a name.
  SwiftBox,
}

//
// Candidate
//

/// A first word that matched no known class, with the blocks that share it. The all-zero value is
/// an empty slot.
#[derive(Clone, Copy)]
pub struct Candidate {
  pub metadata: usize,
  pub blocks: u64,
  pub bytes: u64,
  pub resolution: Resolution,
  pub name: usize,
}

//
// CandidateTable
//

/// Open-addressed table of candidates. Zero-filled on allocation, but hashing spreads the
/// candidates over every page, so the whole table is committed.
pub struct CandidateTable {
  pub slots: MappedVec<Candidate>,
  capacity_bits: u32,
}

impl CandidateTable {
  pub fn new(blocks: usize) -> Option<Self> {
    let capacity_bits = candidate_capacity_bits(blocks);
    // SAFETY: every field of `Candidate` is valid when zeroed: integers, and `Unresolved = 0`.
    let slots = unsafe { MappedVec::zeroed(1 << capacity_bits) }?;
    Some(Self {
      slots,
      capacity_bits,
    })
  }

  fn probe(&self, metadata: usize) -> impl Iterator<Item = usize> + use<> {
    let start = metadata.wrapping_mul(0x9E37_79B9_7F4A_7C15) >> (usize::BITS - self.capacity_bits);
    let mask = (1 << self.capacity_bits) - 1;
    (0 .. CANDIDATE_PROBE_LIMIT).map(move |probe| (start + probe) & mask)
  }

  /// Adds a block under `metadata`. Returns false when the table is full. No allocations.
  pub fn record(&mut self, metadata: usize, size: u64) -> bool {
    for index in self.probe(metadata) {
      let candidate = &mut self.slots[index];
      if candidate.metadata == metadata {
        candidate.blocks += 1;
        candidate.bytes += size;
        return true;
      }
      if candidate.metadata == 0 {
        candidate.metadata = metadata;
        candidate.blocks = 1;
        candidate.bytes = size;
        return true;
      }
    }
    false
  }

  pub fn find(&self, metadata: usize) -> Option<&Candidate> {
    for index in self.probe(metadata) {
      let candidate = &self.slots[index];
      if candidate.metadata == metadata {
        return Some(candidate);
      }
      if candidate.metadata == 0 {
        return None;
      }
    }
    None
  }

  pub fn was_resolved(&self, metadata: usize) -> bool {
    self
      .find(metadata)
      .is_some_and(|candidate| candidate.resolution != Resolution::Unresolved)
  }
}

/// Slots for half the heap's blocks, a power of two within bounds. Distinct first words stay well
/// under that: an iPhone app with 64K blocks had 10K.
const fn candidate_capacity_bits(blocks: usize) -> u32 {
  let bits = (blocks / 2).next_power_of_two().trailing_zeros();
  if bits < MIN_CANDIDATE_CAPACITY_BITS {
    MIN_CANDIDATE_CAPACITY_BITS
  } else if bits > MAX_CANDIDATE_CAPACITY_BITS {
    MAX_CANDIDATE_CAPACITY_BITS
  } else {
    bits
  }
}

//
// ReadableRanges
//

/// The readable ranges of the address space, sorted and merged.
pub struct ReadableRanges {
  ranges: MappedVec<(usize, usize)>,
}

impl ReadableRanges {
  pub fn new() -> Option<Self> {
    Some(Self {
      ranges: MappedVec::with_capacity(RANGE_CAPACITY)?,
    })
  }

  /// Surveys the address space. Makes syscalls, so never call it with a zone locked.
  pub fn survey(&mut self) {
    self.ranges.clear();
    let mut address = 0;
    let mut depth = 0;
    while self.ranges.len() < RANGE_CAPACITY {
      let Ok(region) = region_short_info(address, depth) else {
        break;
      };
      if region.size == 0 {
        break;
      }
      address = region.address;
      depth = region.depth;
      if region.info.is_submap != 0 {
        depth += 1;
        continue;
      }
      if region.info.protection & VM_PROT_READ != 0 {
        let end = address + region.size;
        match self.ranges.last_mut() {
          Some(last) if last.1 == address => last.1 = end,
          _ => {
            let _ = self.ranges.push((address, end));
          },
        }
      }
      address += region.size;
    }
  }

  /// End of the readable range containing `address`. No syscalls, no allocations.
  pub fn readable_end(&self, address: usize) -> Option<usize> {
    self.readable_range(address).map(|range| range.1)
  }

  /// Whether `address` is readable, checking `last_range` before searching and keeping the range
  /// found there. No syscalls, no allocations.
  pub fn is_readable(&self, address: usize, last_range: &mut (usize, usize)) -> bool {
    if (last_range.0 .. last_range.1).contains(&address) {
      return true;
    }
    let Some(range) = self.readable_range(address) else {
      return false;
    };
    *last_range = range;
    true
  }

  /// The readable range containing `address`, as [start, end). No syscalls, no allocations.
  fn readable_range(&self, address: usize) -> Option<(usize, usize)> {
    let index = self.ranges.partition_point(|range| range.1 <= address);
    self
      .ranges
      .get(index)
      .filter(|range| range.0 <= address)
      .copied()
  }
}
