// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt
// Turns the census tables into the report: types whose names collide are reported together, and
// every list is sorted by bytes, largest first.

use super::Census;
use super::tables::Resolution;
use crate::report::{HeapReport, ImageBlocksRow, TypeKind, TypeRow, ZoneRow, milliseconds};
use std::collections::HashMap;

//
// Totals
//

#[derive(Default)]
struct Totals {
  blocks: u64,
  bytes: u64,
}

impl Totals {
  const fn add(&mut self, blocks: u64, bytes: u64) {
    self.blocks += blocks;
    self.bytes += bytes;
  }
}

impl Census<'_> {
  pub(super) fn report(self) -> HeapReport {
    let mut types: HashMap<(TypeKind, String), Totals> = HashMap::new();
    let mut owners: HashMap<(TypeKind, String), Totals> = HashMap::new();
    let mut images: HashMap<String, Totals> = HashMap::new();

    for (entry, tally) in self.classes.entries.iter().zip(&self.classes.tallies) {
      if tally.blocks == 0 && tally.owned_blocks == 0 {
        continue;
      }
      let key = (TypeKind::Class, entry.name.to_string_lossy().into_owned());
      if tally.blocks > 0 {
        types
          .entry(key.clone())
          .or_default()
          .add(tally.blocks, tally.bytes);
      }
      if tally.owned_blocks > 0 {
        owners
          .entry(key)
          .or_default()
          .add(tally.owned_blocks, tally.owned_bytes);
      }
    }

    for candidate in self
      .candidates
      .slots
      .iter()
      .filter(|slot| slot.metadata != 0)
    {
      let kind = match candidate.resolution {
        Resolution::Unresolved => continue,
        Resolution::Image => {
          if let Some(name) = self.images.and_then(|table| table.name(candidate.name)) {
            images
              .entry(name.to_string())
              .or_default()
              .add(candidate.blocks, candidate.bytes);
          }
          continue;
        },
        Resolution::Class => TypeKind::Class,
        Resolution::Cxx => TypeKind::Cxx,
      };
      if let Some(name) = self.names.get(candidate.name) {
        types
          .entry((kind, name.clone()))
          .or_default()
          .add(candidate.blocks, candidate.bytes);
      }
    }

    let summary = self.summary;
    HeapReport {
      truncated: summary.truncated,
      total_blocks: summary.total_blocks,
      total_bytes: summary.total_bytes,
      named_blocks: summary.named_blocks,
      named_bytes: summary.named_bytes,
      swift_box_blocks: summary.swift_box_blocks,
      swift_box_bytes: summary.swift_box_bytes,
      image_blocks: summary.image_blocks,
      image_bytes: summary.image_bytes,
      reporter_blocks: summary.reporter_blocks,
      reporter_bytes: summary.reporter_bytes,
      unclassified_blocks: summary.unclassified_blocks,
      unclassified_bytes: summary.unclassified_bytes,
      unclassified_histogram: summary.unclassified_histogram.to_vec(),
      largest_block_bytes: summary.largest_block_bytes,
      skipped_blocks: summary.skipped_blocks,
      candidate_overflow: summary.candidate_overflow,
      unreadable_candidates: summary.unreadable_candidates,
      owned_blocks: summary.owned_blocks,
      owned_bytes: summary.owned_bytes,
      shared_owned_blocks: summary.shared_owned_blocks,
      owner_table_overflow: summary.owner_table_overflow,
      lock_held_ms: milliseconds(summary.lock_held_ns),
      lock_wait_ms: milliseconds(summary.lock_wait_ns),
      resolve_ms: milliseconds(summary.resolve_ns),
      owners_ms: milliseconds(summary.owners_ns),
      by_type: type_rows(types),
      by_owner: type_rows(owners),
      by_image: image_rows(images),
      zones: self
        .zones
        .into_iter()
        .map(|state| {
          let zone = state.summary;
          ZoneRow {
            name: zone.name,
            blocks: zone.blocks,
            bytes: zone.bytes,
            lock_held_ms: milliseconds(zone.lock_held_ns),
            enumerated: zone.enumerated,
            seen: zone.seen,
            skipped: zone.skipped,
            truncated: zone.truncated,
          }
        })
        .collect(),
    }
  }
}

fn type_rows(totals: HashMap<(TypeKind, String), Totals>) -> Vec<TypeRow> {
  let mut rows: Vec<TypeRow> = totals
    .into_iter()
    .map(|((kind, name), totals)| TypeRow {
      name,
      kind,
      blocks: totals.blocks,
      bytes: totals.bytes,
    })
    .collect();
  rows.sort_by(|left, right| {
    right
      .bytes
      .cmp(&left.bytes)
      .then(left.name.cmp(&right.name))
  });
  rows
}

fn image_rows(totals: HashMap<String, Totals>) -> Vec<ImageBlocksRow> {
  let mut rows: Vec<ImageBlocksRow> = totals
    .into_iter()
    .map(|(name, totals)| ImageBlocksRow {
      name,
      blocks: totals.blocks,
      bytes: totals.bytes,
    })
    .collect();
  rows.sort_by(|left, right| {
    right
      .bytes
      .cmp(&left.bytes)
      .then(left.name.cmp(&right.name))
  });
  rows
}
