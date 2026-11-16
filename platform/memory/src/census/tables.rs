// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt
// Tables the census fills while zones are locked. Each one is allocated in full before the first
// lock and written without allocating afterwards.

use crate::sys::objc::registered_classes;
use crate::sys::vm_region::{VM_PROT_READ, region_short_info};
use std::ffi::CStr;

const CANDIDATE_CAPACITY_BITS: u32 = 17;
const CANDIDATE_CAPACITY: usize = 1 << CANDIDATE_CAPACITY_BITS;
/// A hard probe limit keeps a saturated table from turning each insert into a full scan while a
/// zone is locked.
const CANDIDATE_PROBE_LIMIT: usize = 32;
const RANGE_CAPACITY: usize = 8192;

//
// ClassEntry
//

pub struct ClassEntry {
  pub pointer: usize,
  pub instance_size: usize,
  pub name: &'static CStr,
}

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

/// The registered classes sorted by address, searched by bisection.
pub struct ClassTable {
  pub entries: Vec<ClassEntry>,
  pub tallies: Vec<Tally>,
}

impl ClassTable {
  pub fn new() -> Self {
    let mut entries: Vec<ClassEntry> = registered_classes()
      .into_iter()
      .map(|class| ClassEntry {
        pointer: class.pointer,
        instance_size: class.instance_size,
        name: class.name,
      })
      .collect();
    entries.sort_unstable_by_key(|entry| entry.pointer);
    let tallies = vec![Tally::default(); entries.len()];
    Self { entries, tallies }
  }

  /// Index of the class at `pointer`. No allocations.
  pub fn find(&self, pointer: usize) -> Option<usize> {
    self
      .entries
      .binary_search_by_key(&pointer, |entry| entry.pointer)
      .ok()
  }
}

//
// Resolution
//

#[derive(Clone, Copy, PartialEq, Eq, Debug)]
#[repr(u8)]
pub enum Resolution {
  Unresolved = 0,
  /// An `ObjC` or Swift class the runtime does not list. `name` indexes the census names.
  Class,
  /// A polymorphic C++ type named through RTTI. `name` indexes the census names.
  Cxx,
  /// No type, only the image owning the first word. `name` is the image index.
  Image,
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

/// Open-addressed table of candidates, sized for the tens of thousands of distinct first words a
/// large app produces. Zero-filled on allocation, so only the pages touched are committed.
pub struct CandidateTable {
  pub slots: Box<[Candidate]>,
}

impl CandidateTable {
  pub fn new() -> Self {
    let slots = Box::<[Candidate]>::new_zeroed_slice(CANDIDATE_CAPACITY);
    // SAFETY: every field of `Candidate` is valid when zeroed: integers, and `Unresolved = 0`.
    let slots = unsafe { slots.assume_init() };
    Self { slots }
  }

  const fn slot(metadata: usize) -> usize {
    ((metadata as u64).wrapping_mul(0x9E37_79B9_7F4A_7C15) >> (64 - CANDIDATE_CAPACITY_BITS))
      as usize
  }

  fn probe(metadata: usize) -> impl Iterator<Item = usize> {
    let start = Self::slot(metadata);
    (0 .. CANDIDATE_PROBE_LIMIT).map(move |probe| (start + probe) & (CANDIDATE_CAPACITY - 1))
  }

  /// Adds a block under `metadata`. Returns false when the table is full. No allocations.
  pub fn record(&mut self, metadata: usize, size: u64) -> bool {
    for index in Self::probe(metadata) {
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
    for index in Self::probe(metadata) {
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

//
// ReadableRanges
//

/// The readable ranges of the address space, sorted and merged.
pub struct ReadableRanges {
  ranges: Vec<(usize, usize)>,
}

impl ReadableRanges {
  pub fn new() -> Self {
    Self {
      ranges: Vec::with_capacity(RANGE_CAPACITY),
    }
  }

  pub fn as_ptr(&self) -> usize {
    self.ranges.as_ptr() as usize
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
          _ => self.ranges.push((address, end)),
        }
      }
      address += region.size;
    }
  }

  /// End of the readable range containing `address`. No syscalls, no allocations.
  pub fn readable_end(&self, address: usize) -> Option<usize> {
    let index = self.ranges.partition_point(|range| range.1 <= address);
    self
      .ranges
      .get(index)
      .filter(|range| range.0 <= address)
      .map(|range| range.1)
  }
}
