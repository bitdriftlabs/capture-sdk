// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt
// The report of an `ios_memory_dump` invocation, one section per stage that ran. Rows attributed
// to an image hold its `ImageTable` index.

//
// Report
//

pub struct Report {
  pub counters: Option<CountersReport>,
  pub regions: Option<RegionsReport>,
  pub heap: Option<HeapReport>,
}

//
// CountersReport
//

pub struct CountersReport {
  pub phys_footprint: u64,
  pub phys_footprint_peak: Option<u64>,
  pub internal: u64,
  pub compressed: u64,
  pub external: u64,
  pub resident_size: u64,
  pub decompressions: Option<u64>,
  pub limit_bytes_remaining: Option<u64>,
  pub available_before_kill: Option<u64>,
  pub ledgers: Option<Ledgers>,
}

//
// Ledgers
//

/// The kernel's attribution of the footprint by category. Each value includes its compressed
/// bytes.
pub struct Ledgers {
  pub graphics: u64,
  pub media: u64,
  pub network: u64,
  pub neural: u64,
  pub purgeable_volatile: u64,
  pub purgeable_nonvolatile: u64,
}

//
// RegionsReport
//

pub struct RegionsReport {
  pub regions: u32,
  /// The map changed while it was being read, so the totals are approximate.
  pub truncated: bool,
  pub private_dirty: u64,
  pub shared_dirty: u64,
  pub compressed: u64,
  /// What weighs on the footprint: private dirty plus compressed bytes.
  pub charged: u64,
  pub virtual_size: u64,
  pub by_tag: Vec<TagRow>,
  pub by_image: Vec<ImageChargeRow>,
}

pub struct TagRow {
  pub name: String,
  pub tag: u32,
  pub regions: u32,
  pub private_dirty: u64,
  pub shared_dirty: u64,
  pub compressed: u64,
  pub virtual_size: u64,
  pub charged: u64,
}

pub struct ImageChargeRow {
  pub image: usize,
  pub charged: u64,
}

//
// HeapReport
//

pub struct HeapReport {
  /// The lock budget ran out: the figures are a floor, not an inventory.
  pub truncated: bool,
  /// Blocks a zone reported after its budget ran out, in no total.
  pub skipped_blocks: u64,
  pub total: BlockCount,
  pub largest_block_bytes: u64,
  pub named: BlockCount,
  pub swift_boxes: BlockCount,
  pub images: BlockCount,
  /// The tables of the dump itself, counted in the totals but in no other category.
  pub reporter: BlockCount,
  pub unclassified: Unclassified,
  pub owners: Option<Owners>,
  pub by_type: Vec<TypeRow>,
  pub zones: Vec<ZoneRow>,
}

#[derive(Debug)]
pub struct BlockCount {
  pub blocks: u64,
  pub bytes: u64,
}

#[derive(Debug)]
pub struct Unclassified {
  pub blocks: u64,
  pub bytes: u64,
  /// Blocks per power-of-two size: index `n` counts sizes in [2^n, 2^(n+1)).
  pub histogram: Vec<u64>,
}

/// Unclassified blocks credited to the named object pointing at them. A subset of
/// `unclassified`, never an addition to it.
#[derive(Debug)]
pub struct Owners {
  pub blocks: u64,
  pub bytes: u64,
  pub shared_blocks: u64,
  pub table_overflow: u64,
  /// Types by the unclassified bytes they own.
  pub by_type: Vec<TypeRow>,
}

#[derive(Clone, Copy, PartialEq, Eq, PartialOrd, Ord, Hash, Debug)]
pub enum TypeKind {
  Class,
  Cxx,
}

/// What the first word of a row's blocks points to.
#[derive(Clone, PartialEq, Eq, PartialOrd, Ord, Hash, Debug)]
pub enum Identity {
  Named {
    kind: TypeKind,
    name: String,
  },
  /// Not named on device: an offset from the image's mach header, for the backend to symbolicate.
  Address {
    image: usize,
    offset: u64,
  },
}

#[derive(Debug)]
pub struct TypeRow {
  pub identity: Identity,
  pub blocks: u64,
  pub bytes: u64,
}

#[derive(Debug)]
#[allow(clippy::struct_excessive_bools)]
pub struct ZoneRow {
  pub name: String,
  pub blocks: u64,
  pub bytes: u64,
  /// The zone was locked and enumerated.
  pub enumerated: bool,
  /// The enumerator reported at least one batch.
  pub seen: bool,
  /// The zone is never locked.
  pub skipped: bool,
  /// The zone spent its budget share before its enumeration finished.
  pub truncated: bool,
}
