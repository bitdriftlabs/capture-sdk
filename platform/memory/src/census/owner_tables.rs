// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt
// The tables owner attribution fills: unnamed blocks are targets, named blocks are sources.

use super::Summary;
use super::enumerate::ZoneState;
use super::tables::{CandidateTable, ClassTable};

/// Above this size an unnamed block is credited only to an object pointing at its start. A large
/// block spans so much of the address space that any number in a named object lands inside one.
const INTERIOR_MAX_SIZE: u64 = 16 * 1024;

const MIN_CAPACITY: usize = 1 << 18;

/// Clears only the top byte, which the hardware ignores and memory tagging uses. Masking a word
/// down to the isa bits turns string bytes and byte counts into heap addresses.
const TOP_BYTE_MASK: usize = 0x00ff_ffff_ffff_ffff;

//
// UnnamedBlock
//

/// A block with no class: a possible target of attribution.
pub struct UnnamedBlock {
  pub address: usize,
  pub size: u64,
  /// The candidate it was filed under, or 0. A candidate resolution named belongs to another
  /// bucket and must not also be credited to an owner.
  pub candidate: usize,
  /// The named block last credited, so an object pointing twice at a buffer counts once.
  referrer: usize,
  owner: Option<u32>,
  /// Named objects that pointed at the block as well as the owner did.
  referrers: u32,
  owner_points_at_start: bool,
}

//
// NamedBlock
//

/// A block of a known class: a source of attribution.
pub struct NamedBlock {
  pub address: usize,
  pub size: u32,
  pub class: u32,
}

//
// OwnerTables
//

pub struct OwnerTables {
  pub unnamed: Vec<UnnamedBlock>,
  pub named: Vec<NamedBlock>,
}

impl OwnerTables {
  /// Sized from the zones' own block counts, with room for the heap to grow before the locks. Some
  /// zones report no statistics, hence the floor. Only the entries written are committed.
  pub fn new(zones: &[ZoneState]) -> Self {
    let blocks: usize = zones
      .iter()
      .filter(|zone| zone.will_lock())
      .map(|zone| zone.zone.blocks_in_use())
      .sum();
    let capacity = (blocks + blocks / 4).max(MIN_CAPACITY);
    Self {
      unnamed: Vec::with_capacity(capacity),
      named: Vec::with_capacity(capacity),
    }
  }

  pub fn table_addresses(&self) -> [usize; 2] {
    [self.unnamed.as_ptr() as usize, self.named.as_ptr() as usize]
  }

  /// Remembers a named block as a source. Never grows the table.
  pub fn record_named(&mut self, address: usize, size: u64, class: usize, summary: &mut Summary) {
    let Ok(class) = u32::try_from(class) else {
      return;
    };
    if self.named.len() == self.named.capacity() {
      summary.owner_table_overflow += 1;
      return;
    }
    self.named.push(NamedBlock {
      address,
      size: u32::try_from(size).unwrap_or(u32::MAX),
      class,
    });
  }

  /// Remembers an unnamed block as a target. Never grows the table.
  pub fn record_unnamed(
    &mut self,
    address: usize,
    size: u64,
    summary: &mut Summary,
  ) -> Option<&mut UnnamedBlock> {
    if self.unnamed.len() == self.unnamed.capacity() {
      summary.owner_table_overflow += 1;
      return None;
    }
    self.unnamed.push(UnnamedBlock {
      address,
      size,
      candidate: 0,
      referrer: 0,
      owner: None,
      referrers: 0,
      owner_points_at_start: false,
    });
    self.unnamed.last_mut()
  }

  /// The unnamed block containing `address`, so interior pointers find their buffer.
  fn find_unnamed(&mut self, address: usize) -> Option<&mut UnnamedBlock> {
    let index = self
      .unnamed
      .partition_point(|block| block.address as u64 + block.size <= address as u64);
    self
      .unnamed
      .get_mut(index)
      .filter(|block| block.address <= address)
  }

  pub fn scan_words(
    &mut self,
    source: &NamedBlock,
    copy: &[u8],
    classes: &mut ClassTable,
    candidates: &CandidateTable,
    summary: &mut Summary,
    bounds: (usize, usize),
  ) {
    // Word zero is the isa, which points at a class and never at storage.
    let (words, _) = copy.as_chunks::<{ size_of::<usize>() }>();
    for word in words.iter().skip(1) {
      let word = usize::from_ne_bytes(*word) & TOP_BYTE_MASK;
      if word < bounds.0 || word >= bounds.1 {
        continue;
      }
      let Some(target) = self.find_unnamed(word) else {
        continue;
      };
      if target.referrer == source.address {
        continue;
      }
      let points_at_start = word == target.address;
      if !points_at_start && target.size > INTERIOR_MAX_SIZE {
        continue;
      }
      // Resolution already moved this block to a C++ type or an image.
      if target.candidate != 0 && candidates.was_resolved(target.candidate) {
        continue;
      }
      target.referrer = source.address;

      match target.owner {
        None => {
          credit(target, source.class, points_at_start, classes);
          summary.owned_blocks += 1;
          summary.owned_bytes += target.size;
        },
        Some(owner) if points_at_start && !target.owner_points_at_start => {
          let tally = &mut classes.tallies[owner as usize];
          tally.owned_blocks -= 1;
          tally.owned_bytes -= target.size;
          if target.referrers >= 2 {
            summary.shared_owned_blocks -= 1;
          }
          credit(target, source.class, points_at_start, classes);
        },
        Some(_) if points_at_start == target.owner_points_at_start => {
          target.referrers += 1;
          if target.referrers == 2 {
            summary.shared_owned_blocks += 1;
          }
        },
        Some(_) => {},
      }
    }
  }
}

fn credit(target: &mut UnnamedBlock, class: u32, points_at_start: bool, classes: &mut ClassTable) {
  target.owner = Some(class);
  target.owner_points_at_start = points_at_start;
  target.referrers = 1;
  let tally = &mut classes.tallies[class as usize];
  tally.owned_blocks += 1;
  tally.owned_bytes += target.size;
}
