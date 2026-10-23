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
use crate::report::{
  BlockCount,
  Dropped,
  HeapReport,
  HeapTimings,
  Identity,
  Owners,
  TypeKind,
  TypeRow,
  Unclassified,
  ZoneRow,
  milliseconds,
};
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
  fn named(&self, kind: TypeKind, name: usize) -> Option<Identity> {
    let name = self.names.get(name)?.clone();
    Some(Identity::Named { kind, name })
  }

  pub(super) fn report(self) -> HeapReport {
    let mut types: HashMap<Identity, Totals> = HashMap::new();
    let mut owners: HashMap<Identity, Totals> = HashMap::new();

    for (entry, tally) in self.classes.entries.iter().zip(&self.classes.tallies) {
      if tally.blocks == 0 && tally.owned_blocks == 0 {
        continue;
      }
      let key = Identity::Named {
        kind: TypeKind::Class,
        name: entry.name.to_string_lossy().into_owned(),
      };
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
      let identity = match candidate.resolution {
        Resolution::Unresolved => continue,
        Resolution::Image => self
          .images
          .and_then(|table| table.offset(candidate.name, candidate.metadata))
          .map(|offset| Identity::Address {
            image: candidate.name,
            offset,
          }),
        Resolution::Class => self.named(TypeKind::Class, candidate.name),
        Resolution::Cxx => self.named(TypeKind::Cxx, candidate.name),
      };
      if let Some(identity) = identity {
        types
          .entry(identity)
          .or_default()
          .add(candidate.blocks, candidate.bytes);
      }
    }

    let summary = self.summary;
    HeapReport {
      truncated: summary.truncated,
      total: BlockCount {
        blocks: summary.total_blocks,
        bytes: summary.total_bytes,
      },
      largest_block_bytes: summary.largest_block_bytes,
      named: BlockCount {
        blocks: summary.named_blocks,
        bytes: summary.named_bytes,
      },
      swift_boxes: BlockCount {
        blocks: summary.swift_box_blocks,
        bytes: summary.swift_box_bytes,
      },
      images: BlockCount {
        blocks: summary.image_blocks,
        bytes: summary.image_bytes,
      },
      reporter: BlockCount {
        blocks: summary.reporter_blocks,
        bytes: summary.reporter_bytes,
      },
      unclassified: Unclassified {
        blocks: summary.unclassified_blocks,
        bytes: summary.unclassified_bytes,
        histogram: summary.unclassified_histogram.to_vec(),
      },
      owners: self.options.owners.then(|| Owners {
        blocks: summary.owned_blocks,
        bytes: summary.owned_bytes,
        shared_blocks: summary.shared_owned_blocks,
        table_overflow: summary.owner_table_overflow,
        by_type: type_rows(owners),
      }),
      dropped: Dropped {
        skipped_blocks: summary.skipped_blocks,
        candidate_overflow: summary.candidate_overflow,
        unreadable_candidates: summary.unreadable_candidates,
      },
      timings: HeapTimings {
        lock_held_ms: milliseconds(summary.lock_held_ns),
        lock_wait_ms: milliseconds(summary.lock_wait_ns),
        resolve_ms: milliseconds(summary.resolve_ns),
        owners_ms: milliseconds(summary.owners_ns),
      },
      by_type: type_rows(types),
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

fn type_rows(totals: HashMap<Identity, Totals>) -> Vec<TypeRow> {
  let mut rows: Vec<TypeRow> = totals
    .into_iter()
    .map(|(identity, totals)| TypeRow {
      identity,
      blocks: totals.blocks,
      bytes: totals.bytes,
    })
    .collect();
  rows.sort_by(|left, right| {
    right
      .bytes
      .cmp(&left.bytes)
      .then(left.identity.cmp(&right.identity))
  });
  rows
}
