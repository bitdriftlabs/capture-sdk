// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt
// Encodes the report as bitdrift_public.protobuf.memory.v1.MemoryDump by hand, so the SDK carries
// no generated code. Field numbers mirror memory_dump.proto in the api repository.

#[cfg(test)]
#[path = "./encode_test.rs"]
mod tests;

use crate::images::ImageTable;
use crate::report::{
  BlockCount,
  CountersReport,
  HeapReport,
  Identity,
  RegionsReport,
  Report,
  TypeKind,
  TypeRow,
  ZoneRow,
};
use std::collections::HashMap;

mod memory_dump {
  pub const COUNTERS: u32 = 1;
  pub const REGIONS: u32 = 2;
  pub const HEAP: u32 = 3;
  pub const IMAGES: u32 = 4;
}

mod image {
  pub const NAME: u32 = 1;
  pub const UUID: u32 = 2;
}

mod image_address {
  pub const IMAGE: u32 = 1;
  pub const OFFSET: u32 = 2;
}

mod counters {
  pub const PHYS_FOOTPRINT: u32 = 1;
  pub const PHYS_FOOTPRINT_PEAK: u32 = 2;
  pub const INTERNAL: u32 = 3;
  pub const COMPRESSED: u32 = 4;
  pub const EXTERNAL: u32 = 5;
  pub const RESIDENT_SIZE: u32 = 6;
  pub const DECOMPRESSIONS: u32 = 7;
  pub const LIMIT_BYTES_REMAINING: u32 = 8;
  pub const AVAILABLE_BEFORE_KILL: u32 = 9;
  pub const LEDGERS: u32 = 10;
}

mod ledgers {
  pub const GRAPHICS: u32 = 1;
  pub const MEDIA: u32 = 2;
  pub const NETWORK: u32 = 3;
  pub const NEURAL: u32 = 4;
  pub const PURGEABLE_VOLATILE: u32 = 5;
  pub const PURGEABLE_NONVOLATILE: u32 = 6;
}

mod regions {
  pub const REGIONS: u32 = 1;
  pub const TRUNCATED: u32 = 2;
  pub const PRIVATE_DIRTY: u32 = 3;
  pub const SHARED_DIRTY: u32 = 4;
  pub const COMPRESSED: u32 = 5;
  pub const CHARGED: u32 = 6;
  pub const VIRTUAL_SIZE: u32 = 7;
  pub const BY_TAG: u32 = 8;
  pub const BY_IMAGE: u32 = 9;
}

mod tag_row {
  pub const NAME: u32 = 1;
  pub const TAG: u32 = 2;
  pub const REGIONS: u32 = 3;
  pub const PRIVATE_DIRTY: u32 = 4;
  pub const SHARED_DIRTY: u32 = 5;
  pub const COMPRESSED: u32 = 6;
  pub const VIRTUAL_SIZE: u32 = 7;
  pub const CHARGED: u32 = 8;
}

mod image_charge_row {
  pub const IMAGE: u32 = 1;
  pub const CHARGED: u32 = 2;
}

mod heap {
  pub const TRUNCATED: u32 = 1;
  pub const SKIPPED_BLOCKS: u32 = 2;
  pub const TOTAL: u32 = 3;
  pub const LARGEST_BLOCK_BYTES: u32 = 4;
  pub const NAMED: u32 = 5;
  pub const SWIFT_BOXES: u32 = 6;
  pub const IMAGES: u32 = 7;
  pub const REPORTER: u32 = 8;
  pub const UNCLASSIFIED: u32 = 9;
  pub const OWNERS: u32 = 10;
  pub const BY_TYPE: u32 = 11;
  pub const ZONES: u32 = 12;
}

mod block_count {
  pub const BLOCKS: u32 = 1;
  pub const BYTES: u32 = 2;
}

mod unclassified {
  pub const BLOCKS: u32 = 1;
  pub const BYTES: u32 = 2;
  pub const HISTOGRAM: u32 = 3;
}

mod owners {
  pub const BLOCKS: u32 = 1;
  pub const BYTES: u32 = 2;
  pub const SHARED_BLOCKS: u32 = 3;
  pub const TABLE_OVERFLOW: u32 = 4;
  pub const BY_TYPE: u32 = 5;
}

mod type_row {
  pub const NAME: u32 = 1;
  pub const ADDRESS: u32 = 2;
  pub const KIND: u32 = 3;
  pub const BLOCKS: u32 = 4;
  pub const BYTES: u32 = 5;

  pub const KIND_CLASS: u64 = 1;
  pub const KIND_CXX: u64 = 2;
}

mod zone_row {
  pub const NAME: u32 = 1;
  pub const BLOCKS: u32 = 2;
  pub const BYTES: u32 = 3;
  pub const ENUMERATED: u32 = 4;
  pub const SEEN: u32 = 5;
  pub const SKIPPED: u32 = 6;
  pub const TRUNCATED: u32 = 7;
}

const WIRE_VARINT: u64 = 0;
const WIRE_LENGTH_DELIMITED: u64 = 2;

//
// Writer
//

/// Proto3 encoding: scalars equal to their default are omitted, as a generated encoder would.
#[derive(Default)]
struct Writer {
  bytes: Vec<u8>,
}

impl Writer {
  #[allow(clippy::cast_possible_truncation)]
  fn varint(&mut self, mut value: u64) {
    while value >= 0x80 {
      self.bytes.push((value as u8) | 0x80);
      value >>= 7;
    }
    self.bytes.push(value as u8);
  }

  fn key(&mut self, field: u32, wire_type: u64) {
    self.varint(u64::from(field) << 3 | wire_type);
  }

  fn uint(&mut self, field: u32, value: u64) {
    if value != 0 {
      self.optional_uint(field, Some(value));
    }
  }

  fn optional_uint(&mut self, field: u32, value: Option<u64>) {
    if let Some(value) = value {
      self.key(field, WIRE_VARINT);
      self.varint(value);
    }
  }

  fn bool(&mut self, field: u32, value: bool) {
    self.uint(field, u64::from(value));
  }


  fn length_delimited(&mut self, field: u32, value: &[u8]) {
    self.key(field, WIRE_LENGTH_DELIMITED);
    self.varint(value.len() as u64);
    self.bytes.extend_from_slice(value);
  }

  fn string(&mut self, field: u32, value: &str) {
    if !value.is_empty() {
      self.length_delimited(field, value.as_bytes());
    }
  }

  fn packed(&mut self, field: u32, values: &[u64]) {
    if values.is_empty() {
      return;
    }
    let mut packed = Self::default();
    for value in values {
      packed.varint(*value);
    }
    self.length_delimited(field, &packed.bytes);
  }

  fn message(&mut self, field: u32, write: impl FnOnce(&mut Self)) {
    let mut nested = Self::default();
    write(&mut nested);
    self.length_delimited(field, &nested.bytes);
  }
}

//
// ImageNumbers
//

/// Numbers the images the rows refer to, in order of first reference, so the payload lists only
/// those rather than every loaded image.
#[derive(Default)]
struct ImageNumbers {
  numbers: HashMap<usize, u64>,
  images: Vec<usize>,
}

impl ImageNumbers {
  fn number(&mut self, image: usize) -> u64 {
    *self.numbers.entry(image).or_insert_with(|| {
      self.images.push(image);
      (self.images.len() - 1) as u64
    })
  }
}

pub fn memory_dump(report: &Report, images: Option<&ImageTable>) -> Vec<u8> {
  let mut writer = Writer::default();
  let mut numbers = ImageNumbers::default();
  if let Some(counters) = &report.counters {
    writer.message(memory_dump::COUNTERS, |writer| {
      write_counters(writer, counters);
    });
  }
  if let Some(regions) = &report.regions {
    writer.message(memory_dump::REGIONS, |writer| {
      write_regions(writer, regions, &mut numbers);
    });
  }
  if let Some(heap) = &report.heap {
    writer.message(memory_dump::HEAP, |writer| {
      write_heap(writer, heap, &mut numbers);
    });
  }
  for image in numbers.images {
    writer.message(memory_dump::IMAGES, |writer| {
      if let Some(images) = images {
        writer.string(image::NAME, images.name(image).unwrap_or_default());
        if let Some(uuid) = images.uuid(image) {
          writer.length_delimited(image::UUID, uuid);
        }
      }
    });
  }
  writer.bytes
}

fn write_counters(writer: &mut Writer, report: &CountersReport) {
  writer.uint(counters::PHYS_FOOTPRINT, report.phys_footprint);
  writer.optional_uint(counters::PHYS_FOOTPRINT_PEAK, report.phys_footprint_peak);
  writer.uint(counters::INTERNAL, report.internal);
  writer.uint(counters::COMPRESSED, report.compressed);
  writer.uint(counters::EXTERNAL, report.external);
  writer.uint(counters::RESIDENT_SIZE, report.resident_size);
  writer.optional_uint(counters::DECOMPRESSIONS, report.decompressions);
  writer.optional_uint(
    counters::LIMIT_BYTES_REMAINING,
    report.limit_bytes_remaining,
  );
  writer.optional_uint(
    counters::AVAILABLE_BEFORE_KILL,
    report.available_before_kill,
  );
  if let Some(report) = &report.ledgers {
    writer.message(counters::LEDGERS, |writer| {
      writer.uint(ledgers::GRAPHICS, report.graphics);
      writer.uint(ledgers::MEDIA, report.media);
      writer.uint(ledgers::NETWORK, report.network);
      writer.uint(ledgers::NEURAL, report.neural);
      writer.uint(ledgers::PURGEABLE_VOLATILE, report.purgeable_volatile);
      writer.uint(ledgers::PURGEABLE_NONVOLATILE, report.purgeable_nonvolatile);
    });
  }
}

fn write_regions(writer: &mut Writer, report: &RegionsReport, numbers: &mut ImageNumbers) {
  writer.uint(regions::REGIONS, u64::from(report.regions));
  writer.bool(regions::TRUNCATED, report.truncated);
  writer.uint(regions::PRIVATE_DIRTY, report.private_dirty);
  writer.uint(regions::SHARED_DIRTY, report.shared_dirty);
  writer.uint(regions::COMPRESSED, report.compressed);
  writer.uint(regions::CHARGED, report.charged);
  writer.uint(regions::VIRTUAL_SIZE, report.virtual_size);
  for row in &report.by_tag {
    writer.message(regions::BY_TAG, |writer| {
      writer.string(tag_row::NAME, &row.name);
      writer.uint(tag_row::TAG, u64::from(row.tag));
      writer.uint(tag_row::REGIONS, u64::from(row.regions));
      writer.uint(tag_row::PRIVATE_DIRTY, row.private_dirty);
      writer.uint(tag_row::SHARED_DIRTY, row.shared_dirty);
      writer.uint(tag_row::COMPRESSED, row.compressed);
      writer.uint(tag_row::VIRTUAL_SIZE, row.virtual_size);
      writer.uint(tag_row::CHARGED, row.charged);
    });
  }
  for row in &report.by_image {
    writer.message(regions::BY_IMAGE, |writer| {
      writer.uint(image_charge_row::IMAGE, numbers.number(row.image));
      writer.uint(image_charge_row::CHARGED, row.charged);
    });
  }
}

fn write_heap(writer: &mut Writer, report: &HeapReport, numbers: &mut ImageNumbers) {
  writer.bool(heap::TRUNCATED, report.truncated);
  writer.uint(heap::SKIPPED_BLOCKS, report.skipped_blocks);
  write_block_count(writer, heap::TOTAL, &report.total);
  writer.uint(heap::LARGEST_BLOCK_BYTES, report.largest_block_bytes);
  write_block_count(writer, heap::NAMED, &report.named);
  write_block_count(writer, heap::SWIFT_BOXES, &report.swift_boxes);
  write_block_count(writer, heap::IMAGES, &report.images);
  write_block_count(writer, heap::REPORTER, &report.reporter);
  writer.message(heap::UNCLASSIFIED, |writer| {
    writer.uint(unclassified::BLOCKS, report.unclassified.blocks);
    writer.uint(unclassified::BYTES, report.unclassified.bytes);
    writer.packed(unclassified::HISTOGRAM, &report.unclassified.histogram);
  });
  if let Some(report) = &report.owners {
    writer.message(heap::OWNERS, |writer| {
      writer.uint(owners::BLOCKS, report.blocks);
      writer.uint(owners::BYTES, report.bytes);
      writer.uint(owners::SHARED_BLOCKS, report.shared_blocks);
      writer.uint(owners::TABLE_OVERFLOW, report.table_overflow);
      for row in &report.by_type {
        writer.message(owners::BY_TYPE, |writer| {
          write_type_row(writer, row, numbers);
        });
      }
    });
  }
  for row in &report.by_type {
    writer.message(heap::BY_TYPE, |writer| {
      write_type_row(writer, row, numbers);
    });
  }
  for row in &report.zones {
    writer.message(heap::ZONES, |writer| write_zone_row(writer, row));
  }
}

fn write_block_count(writer: &mut Writer, field: u32, count: &BlockCount) {
  writer.message(field, |writer| {
    writer.uint(block_count::BLOCKS, count.blocks);
    writer.uint(block_count::BYTES, count.bytes);
  });
}

fn write_type_row(writer: &mut Writer, row: &TypeRow, numbers: &mut ImageNumbers) {
  match &row.identity {
    Identity::Named { kind, name } => {
      writer.length_delimited(type_row::NAME, name.as_bytes());
      writer.uint(
        type_row::KIND,
        match kind {
          TypeKind::Class => type_row::KIND_CLASS,
          TypeKind::Cxx => type_row::KIND_CXX,
        },
      );
    },
    Identity::Address { image, offset } => {
      writer.message(type_row::ADDRESS, |writer| {
        writer.uint(image_address::IMAGE, numbers.number(*image));
        writer.uint(image_address::OFFSET, *offset);
      });
    },
  }
  writer.uint(type_row::BLOCKS, row.blocks);
  writer.uint(type_row::BYTES, row.bytes);
}

fn write_zone_row(writer: &mut Writer, row: &ZoneRow) {
  writer.string(zone_row::NAME, &row.name);
  writer.uint(zone_row::BLOCKS, row.blocks);
  writer.uint(zone_row::BYTES, row.bytes);
  writer.bool(zone_row::ENUMERATED, row.enumerated);
  writer.bool(zone_row::SEEN, row.seen);
  writer.bool(zone_row::SKIPPED, row.skipped);
  writer.bool(zone_row::TRUNCATED, row.truncated);
}
