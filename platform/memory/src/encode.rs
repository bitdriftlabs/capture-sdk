// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt
// Converts the report to bitdrift_public.protobuf.memory.v1.MemoryDump, generated from
// memory_dump.proto in the api repository.

#[cfg(test)]
#[path = "./encode_test.rs"]
mod tests;

use crate::images::ImageTable;
use crate::report::{
  BlockCount,
  CountersReport,
  HeapReport,
  Identity,
  Ledgers,
  Owners,
  RegionsReport,
  Report,
  TagRow,
  TypeKind,
  TypeRow,
  Unclassified,
  ZoneRow,
};
use bd_proto::protos::memory::memory_dump as proto;
use bd_proto::protos::memory::memory_dump::type_row::{Identity as ProtoIdentity, Kind};
use protobuf::Message;
use std::collections::HashMap;

//
// ImageNumbers
//

/// Numbers the images the rows refer to, in order of first reference, so the payload lists only
/// those rather than every loaded image.
#[derive(Default)]
struct ImageNumbers {
  numbers: HashMap<usize, u32>,
  images: Vec<usize>,
}

impl ImageNumbers {
  fn number(&mut self, image: usize) -> u32 {
    *self.numbers.entry(image).or_insert_with(|| {
      self.images.push(image);
      u32::try_from(self.images.len() - 1).unwrap_or(u32::MAX)
    })
  }
}

pub fn memory_dump(report: &Report, images: Option<&ImageTable>) -> anyhow::Result<Vec<u8>> {
  let mut numbers = ImageNumbers::default();
  let mut dump = proto::MemoryDump {
    counters: report.counters.as_ref().map(counters).into(),
    regions: report
      .regions
      .as_ref()
      .map(|report| regions(report, &mut numbers))
      .into(),
    heap: report
      .heap
      .as_ref()
      .map(|report| heap(report, &mut numbers))
      .into(),
    ..Default::default()
  };
  dump.images = numbers
    .images
    .into_iter()
    .map(|image| proto::Image {
      name: images
        .and_then(|images| images.name(image))
        .unwrap_or_default()
        .to_string(),
      uuid: images
        .and_then(|images| images.uuid(image))
        .map(|uuid| uuid.to_vec())
        .unwrap_or_default(),
      ..Default::default()
    })
    .collect();
  Ok(dump.write_to_bytes()?)
}

fn counters(report: &CountersReport) -> proto::Counters {
  proto::Counters {
    phys_footprint: report.phys_footprint,
    phys_footprint_peak: report.phys_footprint_peak,
    internal: report.internal,
    compressed: report.compressed,
    external: report.external,
    resident_size: report.resident_size,
    decompressions: report.decompressions,
    limit_bytes_remaining: report.limit_bytes_remaining,
    available_before_kill: report.available_before_kill,
    ledgers: report.ledgers.as_ref().map(ledgers).into(),
    ..Default::default()
  }
}

fn ledgers(report: &Ledgers) -> proto::Ledgers {
  proto::Ledgers {
    graphics: report.graphics,
    media: report.media,
    network: report.network,
    neural: report.neural,
    purgeable_volatile: report.purgeable_volatile,
    purgeable_nonvolatile: report.purgeable_nonvolatile,
    ..Default::default()
  }
}

fn regions(report: &RegionsReport, numbers: &mut ImageNumbers) -> proto::Regions {
  proto::Regions {
    regions: report.regions,
    truncated: report.truncated,
    private_dirty: report.private_dirty,
    shared_dirty: report.shared_dirty,
    compressed: report.compressed,
    charged: report.charged,
    virtual_size: report.virtual_size,
    by_tag: report.by_tag.iter().map(tag_row).collect(),
    by_image: report
      .by_image
      .iter()
      .map(|row| proto::ImageChargeRow {
        image: numbers.number(row.image),
        charged: row.charged,
        ..Default::default()
      })
      .collect(),
    ..Default::default()
  }
}

fn tag_row(row: &TagRow) -> proto::TagRow {
  proto::TagRow {
    name: row.name.clone(),
    tag: row.tag,
    regions: row.regions,
    private_dirty: row.private_dirty,
    shared_dirty: row.shared_dirty,
    compressed: row.compressed,
    virtual_size: row.virtual_size,
    charged: row.charged,
    ..Default::default()
  }
}

fn heap(report: &HeapReport, numbers: &mut ImageNumbers) -> proto::Heap {
  proto::Heap {
    truncated: report.truncated,
    skipped_blocks: report.skipped_blocks,
    total: Some(block_count(&report.total)).into(),
    largest_block_bytes: report.largest_block_bytes,
    named: Some(block_count(&report.named)).into(),
    swift_boxes: Some(block_count(&report.swift_boxes)).into(),
    images: Some(block_count(&report.images)).into(),
    reporter: Some(block_count(&report.reporter)).into(),
    unclassified: Some(unclassified(&report.unclassified)).into(),
    owners: report
      .owners
      .as_ref()
      .map(|report| owners(report, numbers))
      .into(),
    by_type: report
      .by_type
      .iter()
      .map(|row| type_row(row, numbers))
      .collect(),
    zones: report.zones.iter().map(zone_row).collect(),
    ..Default::default()
  }
}

fn block_count(count: &BlockCount) -> proto::BlockCount {
  proto::BlockCount {
    blocks: count.blocks,
    bytes: count.bytes,
    ..Default::default()
  }
}

fn unclassified(report: &Unclassified) -> proto::Unclassified {
  proto::Unclassified {
    blocks: report.blocks,
    bytes: report.bytes,
    histogram: report.histogram.clone(),
    ..Default::default()
  }
}

fn owners(report: &Owners, numbers: &mut ImageNumbers) -> proto::Owners {
  proto::Owners {
    blocks: report.blocks,
    bytes: report.bytes,
    shared_blocks: report.shared_blocks,
    table_overflow: report.table_overflow,
    by_type: report
      .by_type
      .iter()
      .map(|row| type_row(row, numbers))
      .collect(),
    ..Default::default()
  }
}

fn type_row(row: &TypeRow, numbers: &mut ImageNumbers) -> proto::TypeRow {
  let (identity, kind) = match &row.identity {
    Identity::Named { kind, name } => (
      ProtoIdentity::Name(name.clone()),
      match kind {
        TypeKind::Class => Kind::CLASS,
        TypeKind::Cxx => Kind::CXX,
      },
    ),
    Identity::Address { image, offset } => (
      ProtoIdentity::Address(proto::ImageAddress {
        image: numbers.number(*image),
        offset: *offset,
        ..Default::default()
      }),
      Kind::UNSPECIFIED,
    ),
  };
  proto::TypeRow {
    identity: Some(identity),
    kind: kind.into(),
    blocks: row.blocks,
    bytes: row.bytes,
    ..Default::default()
  }
}

fn zone_row(row: &ZoneRow) -> proto::ZoneRow {
  proto::ZoneRow {
    name: row.name.clone(),
    blocks: row.blocks,
    bytes: row.bytes,
    enumerated: row.enumerated,
    seen: row.seen,
    skipped: row.skipped,
    truncated: row.truncated,
    ..Default::default()
  }
}
