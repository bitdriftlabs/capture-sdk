// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt
// The JSON attachment of an `ios_memory_dump` invocation: one section per stage that ran.

use serde::Serialize;

pub const SCHEMA_VERSION: u32 = 1;

//
// Report
//

#[derive(Serialize)]
pub struct Report {
  pub schema_version: u32,
  #[serde(skip_serializing_if = "Option::is_none")]
  pub counters: Option<CountersReport>,
  #[serde(skip_serializing_if = "Option::is_none")]
  pub regions: Option<RegionsReport>,
  #[serde(skip_serializing_if = "Option::is_none")]
  pub heap: Option<HeapReport>,
}

//
// CountersReport
//

#[derive(Serialize)]
pub struct CountersReport {
  pub phys_footprint: u64,
  #[serde(skip_serializing_if = "Option::is_none")]
  pub phys_footprint_peak: Option<u64>,
  pub internal: u64,
  pub compressed: u64,
  pub external: u64,
  pub resident_size: u64,
  #[serde(skip_serializing_if = "Option::is_none")]
  pub decompressions: Option<u64>,
  #[serde(skip_serializing_if = "Option::is_none")]
  pub limit_bytes_remaining: Option<u64>,
  #[serde(skip_serializing_if = "Option::is_none")]
  pub available_before_kill: Option<u64>,
  #[serde(skip_serializing_if = "Option::is_none")]
  pub ledgers: Option<Ledgers>,
}

//
// Ledgers
//

/// The kernel's attribution of the footprint by category. Each value includes its compressed
/// bytes.
#[derive(Serialize)]
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

#[derive(Serialize)]
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

#[derive(Serialize)]
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

#[derive(Serialize)]
pub struct ImageChargeRow {
  pub name: String,
  pub charged: u64,
}

//
// HeapReport
//

#[derive(Serialize)]
pub struct HeapReport {
  /// The lock budget ran out: the figures are a floor, not an inventory.
  pub truncated: bool,
  pub total_blocks: u64,
  pub total_bytes: u64,
  pub named_blocks: u64,
  pub named_bytes: u64,
  pub swift_box_blocks: u64,
  pub swift_box_bytes: u64,
  pub image_blocks: u64,
  pub image_bytes: u64,
  /// The tables of the dump itself, counted in the totals but in no other category.
  pub reporter_blocks: u64,
  pub reporter_bytes: u64,
  pub unclassified_blocks: u64,
  pub unclassified_bytes: u64,
  /// Unclassified blocks per power-of-two size: index `n` counts sizes in [2^n, 2^(n+1)).
  pub unclassified_histogram: Vec<u64>,
  pub largest_block_bytes: u64,
  pub skipped_blocks: u64,
  pub candidate_overflow: u64,
  pub unreadable_candidates: u64,
  /// Unclassified blocks credited to the named object pointing at them. A subset of
  /// `unclassified_*`, never an addition to it.
  pub owned_blocks: u64,
  pub owned_bytes: u64,
  pub shared_owned_blocks: u64,
  pub owner_table_overflow: u64,
  pub lock_held_ms: f64,
  pub lock_wait_ms: f64,
  pub resolve_ms: f64,
  pub owners_ms: f64,
  pub by_type: Vec<TypeRow>,
  /// Types by the unclassified bytes they own, empty when owners are not attributed.
  pub by_owner: Vec<TypeRow>,
  pub by_image: Vec<ImageBlocksRow>,
  pub zones: Vec<ZoneRow>,
}

#[derive(Serialize, Clone, Copy, PartialEq, Eq, Hash, Debug)]
#[serde(rename_all = "snake_case")]
pub enum TypeKind {
  Class,
  Cxx,
}

#[derive(Serialize, Debug)]
pub struct TypeRow {
  pub name: String,
  pub kind: TypeKind,
  pub blocks: u64,
  pub bytes: u64,
}

#[derive(Serialize, Debug)]
pub struct ImageBlocksRow {
  pub name: String,
  pub blocks: u64,
  pub bytes: u64,
}

#[derive(Serialize, Debug)]
#[allow(clippy::struct_excessive_bools)]
pub struct ZoneRow {
  pub name: String,
  pub blocks: u64,
  pub bytes: u64,
  pub lock_held_ms: f64,
  /// The zone was locked and enumerated.
  pub enumerated: bool,
  /// The enumerator reported at least one batch.
  pub seen: bool,
  /// The zone is never locked.
  pub skipped: bool,
  /// The zone spent its budget share before its enumeration finished.
  pub truncated: bool,
}

#[allow(clippy::cast_precision_loss)]
pub fn milliseconds(nanoseconds: u64) -> f64 {
  (nanoseconds as f64 / 1_000.0).round() / 1_000.0
}
