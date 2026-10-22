// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt
// The only census code that runs with a malloc zone locked. Nothing the recorder does may
// allocate, take a lock, call into the ObjC runtime, make a syscall or panic: an allocation from
// the locked zone deadlocks the process.

use super::owner_tables::OwnerTables;
use super::tables::{CandidateTable, ClassTable, ReadableRanges};
use super::{Census, ISA_MASK, SKIPPED_ZONES, Summary, size_bucket};
use crate::sys::mach::now_ns;
use crate::sys::malloc::{MALLOC_PTR_IN_USE_RANGE_TYPE, RangeSink, Zone};
use libc::vm_range_t;

/// Swift metadata kinds of heap objects that have no class: closure boxes, existentials, errors.
const SWIFT_BOX_KINDS: [usize; 3] = [0x400, 0x500, 0x501];

/// The dump's own tables are all at least this large; smaller blocks skip the comparison.
const REPORTER_BLOCK_MIN_SIZE: u64 = 16 * 1024;
const REPORTER_BLOCK_CAPACITY: usize = 8;

//
// ZoneSummary
//

#[derive(Default)]
#[allow(clippy::struct_excessive_bools)]
pub struct ZoneSummary {
  pub name: String,
  pub blocks: u64,
  pub bytes: u64,
  pub lock_held_ns: u64,
  pub enumerated: bool,
  pub seen: bool,
  pub skipped: bool,
  pub truncated: bool,
}

//
// ZoneState
//

pub struct ZoneState {
  pub zone: Zone,
  pub summary: ZoneSummary,
}

impl ZoneState {
  pub fn new(zone: Zone) -> Self {
    let name = zone.name();
    let skipped = SKIPPED_ZONES.contains(&name.as_str());
    Self {
      zone,
      summary: ZoneSummary {
        name,
        skipped,
        ..ZoneSummary::default()
      },
    }
  }

  pub fn will_lock(&self) -> bool {
    !self.summary.skipped && self.zone.is_enumerable()
  }
}

//
// Recorder
//

struct Recorder<'t> {
  classes: &'t mut ClassTable,
  candidates: &'t mut CandidateTable,
  ranges: &'t ReadableRanges,
  owners: Option<&'t mut OwnerTables>,
  summary: &'t mut Summary,
  zone: &'t mut ZoneSummary,
  /// Start addresses of the dump's own tables.
  reporter_blocks: [usize; REPORTER_BLOCK_CAPACITY],
  deadline_ns: u64,
  over_budget: bool,
}

impl RangeSink for Recorder<'_> {
  fn record(&mut self, type_mask: u32, ranges: &[vm_range_t]) {
    self.zone.seen = true;

    // Checked per batch: batches carry hundreds of blocks.
    if !self.over_budget && now_ns() >= self.deadline_ns {
      self.over_budget = true;
      self.summary.truncated = true;
      self.zone.truncated = true;
    }
    if self.over_budget {
      self.summary.skipped_blocks += ranges.len() as u64;
      return;
    }

    // Bitwise: large allocations report MALLOC_PTR_IN_USE_RANGE_TYPE |
    // MALLOC_PTR_REGION_RANGE_TYPE, and comparing for equality drops every block above ~16 KB.
    if type_mask & MALLOC_PTR_IN_USE_RANGE_TYPE == 0 {
      return;
    }
    for range in ranges {
      self.record_block(range.address, range.size as u64);
    }
  }
}

impl Recorder<'_> {
  fn record_block(&mut self, address: usize, size: u64) {
    let summary = &mut *self.summary;
    summary.total_blocks += 1;
    summary.total_bytes += size;
    summary.largest_block_bytes = summary.largest_block_bytes.max(size);
    self.zone.blocks += 1;
    self.zone.bytes += size;

    if size < size_of::<usize>() as u64 || address == 0 {
      summary.unclassified(size);
      summary.unclassified_histogram[size_bucket(size)] += 1;
      return;
    }
    if size >= REPORTER_BLOCK_MIN_SIZE && self.reporter_blocks.contains(&address) {
      summary.reporter_blocks += 1;
      summary.reporter_bytes += size;
      return;
    }

    // SAFETY: the zone is locked, so the block is live, word-aligned and at least a word long.
    let first_word = unsafe { (address as *const usize).read() };
    let candidate = first_word & ISA_MASK;

    // A block smaller than its class cannot be an instance of it.
    if let Some(class) = self.classes.find(candidate)
      && self.classes.entries[class].instance_size as u64 <= size
    {
      let tally = &mut self.classes.tallies[class];
      tally.blocks += 1;
      tally.bytes += size;
      summary.named_blocks += 1;
      summary.named_bytes += size;
      if let Some(owners) = self.owners.as_deref_mut() {
        owners.record_named(address, size, class, summary);
      }
      return;
    }

    if SWIFT_BOX_KINDS.contains(&first_word) {
      summary.swift_box_blocks += 1;
      summary.swift_box_bytes += size;
      return;
    }

    summary.unclassified(size);
    let unnamed = self
      .owners
      .as_deref_mut()
      .and_then(|owners| owners.record_unnamed(address, size, summary));

    // Only first words pointing into readable memory are worth resolving; anything else would
    // saturate the table with buffer contents.
    if candidate > 0x1000
      && candidate.trailing_zeros() >= 3
      && self.ranges.readable_end(candidate).is_some()
    {
      if self.candidates.record(candidate, size) {
        if let Some(unnamed) = unnamed {
          unnamed.candidate = candidate;
        }
        // The histogram is completed after resolution, from the candidates left unnamed.
        return;
      }
      summary.candidate_overflow += 1;
    }
    summary.unclassified_histogram[size_bucket(size)] += 1;
  }
}

impl Census<'_> {
  fn reporter_blocks(&self) -> [usize; REPORTER_BLOCK_CAPACITY] {
    let mut blocks = [0; REPORTER_BLOCK_CAPACITY];
    let mut addresses = vec![
      self.classes.entries.as_ptr() as usize,
      self.classes.tallies.as_ptr() as usize,
      self.candidates.slots.as_ptr() as usize,
      self.ranges.as_ptr(),
    ];
    if let Some(owners) = &self.owners {
      addresses.extend(owners.table_addresses());
    }
    for (slot, address) in blocks.iter_mut().zip(addresses) {
      *slot = address;
    }
    blocks
  }

  /// Locks each zone in turn and records its live blocks.
  pub(super) fn enumerate(&mut self) {
    let reporter_blocks = self.reporter_blocks();
    let mut zones_left = self.zones.iter().filter(|zone| zone.will_lock()).count() as u64;
    let mut budget_left_ns = self.options.budget_ms.saturating_mul(1_000_000);

    // One zone locked at a time. A zone's enumerator may allocate from another zone (the nano zone
    // does), and with that zone locked too the process aborts on a recursive lock. Blocks
    // allocated while another zone is enumerated may be missed.
    for state in &mut self.zones {
      if !state.will_lock() {
        continue;
      }
      // Each zone gets an equal share of what is left. A zone that overruns its share cannot be
      // interrupted, but the overrun is not taken from the zones after it.
      let share_ns = budget_left_ns / zones_left;
      zones_left -= 1;
      if share_ns == 0 {
        state.summary.truncated = true;
        self.summary.truncated = true;
        continue;
      }

      let ZoneState {
        zone,
        summary: zone_summary,
      } = state;
      let wait_started = now_ns();
      let Some(lock) = zone.lock() else {
        continue;
      };
      let held_from = now_ns();
      let mut recorder = Recorder {
        classes: &mut self.classes,
        candidates: &mut self.candidates,
        ranges: &self.ranges,
        owners: self.owners.as_mut(),
        summary: &mut self.summary,
        zone: &mut *zone_summary,
        reporter_blocks,
        deadline_ns: held_from + share_ns,
        over_budget: false,
      };
      lock.enumerate(&mut recorder);
      drop(lock);
      let held_ns = now_ns() - held_from;

      budget_left_ns -= held_ns.min(share_ns);
      self.summary.lock_wait_ns += held_from - wait_started;
      self.summary.lock_held_ns += held_ns;
      zone_summary.lock_held_ns = held_ns;
      zone_summary.enumerated = true;
    }
  }
}
