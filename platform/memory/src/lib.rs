// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt
//! Memory diagnostics for the `ios_memory_dump` command: kernel counters and the VM map, captured
//! from inside the process.

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

mod counters;
mod images;
mod regions;
mod report;
mod safe_read;
mod sys;
mod tags;
#[cfg(test)]
mod test_support;

use images::ImageTable;
use report::{Report, SCHEMA_VERSION};
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
  /// The JSON report.
  pub attachment: Vec<u8>,
  /// Fields for the command result: timings and the flags worth searching on.
  pub context: Vec<(String, String)>,
}

pub fn capture(request: &Request) -> anyhow::Result<Dump> {
  let mut context = Vec::new();
  let mut report = Report {
    schema_version: SCHEMA_VERSION,
    counters: None,
    regions: None,
  };

  if let Some(footprint) = counters::phys_footprint() {
    context.push(("phys_footprint_before".to_string(), footprint.to_string()));
  }

  // TODO(ademarco): worth moving to a shared cache once we use this in bd_crash_reporter
  let images = request.stages.regions.then(ImageTable::survey);

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

  if let Some(footprint) = counters::phys_footprint() {
    context.push(("phys_footprint_after".to_string(), footprint.to_string()));
  }
  context.push(("stages".to_string(), request.stages.names().join(",")));

  Ok(Dump {
    attachment: serde_json::to_vec(&report)?,
    context,
  })
}

fn timing(stage: &str, started: Instant) -> (String, String) {
  (
    format!("{stage}_ms"),
    format!("{:.1}", started.elapsed().as_secs_f64() * 1_000.0),
  )
}
