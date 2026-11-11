// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt
// Stage 3: the malloc heap's live blocks, grouped by type, by owning image and by the named object
// holding them. Zones are locked one at a time for a bounded budget; everything that allocates or
// makes syscalls happens while no zone is locked.

#[cfg(test)]
#[path = "./census_test.rs"]
mod tests;

mod enumerate;
mod owner_tables;
mod owners;
mod resolve;
mod rows;
mod tables;

use crate::images::ImageTable;
use crate::report::HeapReport;
use crate::sys::malloc::Zone;
use anyhow::Context;
use enumerate::ZoneState;
use owner_tables::OwnerTables;
use tables::{CandidateTable, ClassTable, ReadableRanges};

/// `ISA_MASK` from the Objective-C runtime's `runtime/isa.h` (objc4), not in the SDK.
#[cfg(all(target_arch = "aarch64", target_os = "ios", not(target_abi = "sim")))]
const ISA_MASK: usize = 0x0000_000f_ffff_fff8;
/// The simulator shares the macOS address space, where Swift generic metadata lives above the 36
/// bits a device uses.
#[cfg(not(all(target_arch = "aarch64", target_os = "ios", not(target_abi = "sim"))))]
const ISA_MASK: usize = 0x0000_7fff_ffff_fff8;

/// Power-of-two size buckets for the blocks that could not be named.
pub const SIZE_BUCKETS: usize = 32;

/// Zones that are never locked. With a web view open, the `WebKit` zone stayed locked for 9.9 s and
/// its enumerator reported nothing: the zone is private to `WebKit`.
const SKIPPED_ZONES: &[&str] = &["WebKit Malloc"];

//
// HeapOptions
//

#[derive(Clone, Copy)]
pub struct HeapOptions {
  /// Time the zone locks may be held, shared among the enumerated zones. A zone that spends its
  /// share stops recording; time a zone leaves unused goes to the zones after it.
  pub budget_ms: u64,
  /// Credit unnamed blocks to the named object that points at them.
  pub owners: bool,
}

//
// Summary
//

#[derive(Default)]
struct Summary {
  total_blocks: u64,
  total_bytes: u64,
  named_blocks: u64,
  named_bytes: u64,
  swift_box_blocks: u64,
  swift_box_bytes: u64,
  image_blocks: u64,
  image_bytes: u64,
  unclassified_blocks: u64,
  unclassified_bytes: u64,
  unclassified_histogram: [u64; SIZE_BUCKETS],
  largest_block_bytes: u64,
  truncated: bool,
  skipped_blocks: u64,
  lock_wait_ns: u64,
  lock_held_ns: u64,
  resolve_ns: u64,
  owned_blocks: u64,
  owned_bytes: u64,
  shared_owned_blocks: u64,
  owner_table_overflow: u64,
  candidate_table_overflow: u64,
  owners_ns: u64,
}

impl Summary {
  const fn unclassified(&mut self, size: u64) {
    self.unclassified_blocks += 1;
    self.unclassified_bytes += size;
  }

  /// Moves blocks out of the unclassified tallies once resolution names them.
  const fn reclassify(&mut self, blocks: u64, bytes: u64) {
    self.unclassified_blocks -= blocks;
    self.unclassified_bytes -= bytes;
  }
}

const fn size_bucket(size: u64) -> usize {
  let bucket = if size <= 1 {
    0
  } else {
    63 - size.leading_zeros() as usize
  };
  if bucket < SIZE_BUCKETS {
    bucket
  } else {
    SIZE_BUCKETS - 1
  }
}

/// Blocks in use in the zones the census locks, before the locks. Some zones report no statistics.
fn blocks_in_use(zones: &[ZoneState]) -> usize {
  zones
    .iter()
    .filter(|zone| zone.will_lock())
    .map(|zone| zone.zone.blocks_in_use())
    .sum()
}

//
// Census
//

struct Census<'a> {
  options: HeapOptions,
  images: Option<&'a ImageTable>,
  classes: ClassTable,
  candidates: CandidateTable,
  ranges: ReadableRanges,
  zones: Vec<ZoneState>,
  owners: Option<OwnerTables>,
  /// Names of the classes no image lists, found by resolution.
  names: Vec<String>,
  summary: Summary,
  #[cfg(test)]
  before_resolve: Option<Box<dyn FnOnce()>>,
}

pub fn capture(options: HeapOptions, images: Option<&ImageTable>) -> anyhow::Result<HeapReport> {
  Ok(Census::new(options, images)?.run())
}

impl<'a> Census<'a> {
  fn new(options: HeapOptions, images: Option<&'a ImageTable>) -> anyhow::Result<Self> {
    let zones: Vec<ZoneState> = Zone::all().into_iter().map(ZoneState::new).collect();
    let blocks = blocks_in_use(&zones);
    let owners = if options.owners {
      Some(OwnerTables::new(blocks).context("mapping the owner tables")?)
    } else {
      None
    };
    Ok(Self {
      options,
      images,
      classes: ClassTable::new().context("mapping the class table")?,
      candidates: CandidateTable::new(blocks).context("mapping the candidate table")?,
      ranges: ReadableRanges::new().context("mapping the readable ranges")?,
      zones,
      owners,
      names: Vec::new(),
      summary: Summary::default(),
      #[cfg(test)]
      before_resolve: None,
    })
  }

  fn run(mut self) -> HeapReport {
    // Every syscall and allocation happen outside the zone locks: the recorder reads blocks and
    // filters candidates against the survey taken before each lock and writes into the tables
    // without allocating.
    self.enumerate();

    // Regions may have been unmapped since the pre-lock survey.
    self.ranges.survey();
    #[cfg(test)]
    if let Some(before_resolve) = self.before_resolve.take() {
      before_resolve();
    }
    self.resolve();
    self.attribute_owners();
    let summary = &self.summary;
    log::debug!(
      "heap census: {} blocks, {} bytes, truncated: {}, locks held {} ms, waited {} ms, resolve \
       {} ms, owners {} ms, candidate table overflow {}",
      summary.total_blocks,
      summary.total_bytes,
      summary.truncated,
      summary.lock_held_ns / 1_000_000,
      summary.lock_wait_ns / 1_000_000,
      summary.resolve_ns / 1_000_000,
      summary.owners_ns / 1_000_000,
      summary.candidate_table_overflow
    );
    self.report()
  }
}
