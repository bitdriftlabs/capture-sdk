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
