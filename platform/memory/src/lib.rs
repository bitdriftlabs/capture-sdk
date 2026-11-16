// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt
//! Memory diagnostics for the `ios_memory_dump` command, captured from inside the process.

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

mod report;

use report::{Report, SCHEMA_VERSION};

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
  let report = Report {
    schema_version: SCHEMA_VERSION,
  };

  context.push(("stages".to_string(), request.stages.names().join(",")));

  Ok(Dump {
    attachment: serde_json::to_vec(&report)?,
    context,
  })
}
