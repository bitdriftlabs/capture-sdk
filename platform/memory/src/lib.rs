// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt
//! Memory diagnostics for the `ios_memory_dump` command: kernel counters, the VM map and a census
//! of the malloc heap, captured from inside the process.

#![deny(
  clippy::expect_used,
  clippy::panic,
  clippy::todo,
  clippy::unimplemented,
  clippy::unreachable,
  clippy::unwrap_used
)]

#[cfg(test)]
#[path = "./lib_test.rs"]
mod tests;

mod census;
mod counters;
mod encode;
mod images;
mod regions;
mod report;
mod safe_read;
mod sys;
mod tags;
#[cfg(test)]
mod test_support;

pub use census::HeapOptions;
use images::ImageTable;
use report::{HeapReport, Report};
use std::time::Instant;

//
// Stages
//

#[derive(Clone, Copy, Default)]
pub struct Stages {
  pub counters: bool,
  pub regions: bool,
  pub heap: bool,
}

impl Stages {
  /// The stages in execution order, as named in the command arguments.
  fn names(self) -> Vec<&'static str> {
    [
      (self.counters, "counters"),
      (self.regions, "regions"),
      (self.heap, "heap"),
    ]
    .into_iter()
    .filter_map(|(requested, name)| requested.then_some(name))
    .collect()
  }
}

//
// Request
//

pub struct Request {
  pub stages: Stages,
  pub heap: HeapOptions,
}

//
// Dump
//

pub struct Dump {
  /// A `bitdrift_public.protobuf.memory.v1.MemoryDump`.
  pub attachment: Vec<u8>,
  /// Fields for the command result: timings and the flags worth searching on.
  pub context: Vec<(String, String)>,
}

pub fn capture(request: &Request) -> anyhow::Result<Dump> {
  let mut context = Vec::new();
  let mut report = Report {
    counters: None,
    regions: None,
    heap: None,
  };

  if let Some(footprint) = counters::phys_footprint() {
    context.push(("phys_footprint_before".to_string(), footprint.to_string()));
  }

  // Surveyed once, before the stages that attribute addresses to images.
  // TODO(ademarco): worth moving to a shared cache once we use this in bd_crash_reporter
  let images = (request.stages.regions || request.stages.heap).then(ImageTable::survey);

  if request.stages.counters {
    let started = Instant::now();
    report.counters = Some(counters::capture()?);
    context.push(timing("counters", started));
  }
  if request.stages.regions {
    let started = Instant::now();
    report.regions = Some(regions::walk(images.as_ref()));
    context.push(timing("regions", started));
  }
  if request.stages.heap {
    let started = Instant::now();
    let heap = census::capture(request.heap, images.as_ref());
    context.push(timing("heap", started));
    context.extend(heap_context(&heap));
    report.heap = Some(heap);
  }

  if let Some(footprint) = counters::phys_footprint() {
    context.push(("phys_footprint_after".to_string(), footprint.to_string()));
  }
  context.push(("stages".to_string(), request.stages.names().join(",")));

  Ok(Dump {
    attachment: encode::memory_dump(&report, images.as_ref()),
    context,
  })
}

fn timing(stage: &str, started: Instant) -> (String, String) {
  (
    format!("{stage}_ms"),
    format!("{:.1}", started.elapsed().as_secs_f64() * 1_000.0),
  )
}

fn heap_context(heap: &HeapReport) -> [(String, String); 3] {
  let zones = |include: fn(&report::ZoneRow) -> bool| {
    heap
      .zones
      .iter()
      .filter(|zone| include(zone))
      .map(|zone| zone.name.as_str())
      .collect::<Vec<_>>()
      .join(",")
  };
  [
    ("heap_truncated".to_string(), heap.truncated.to_string()),
    // Zones enumerated without ever reporting a batch keep their blocks out of the census.
    (
      "heap_zones_not_seen".to_string(),
      zones(|zone| zone.enumerated && !zone.seen),
    ),
    ("heap_zones_skipped".to_string(), zones(|zone| zone.skipped)),
  ]
}
