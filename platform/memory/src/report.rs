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
