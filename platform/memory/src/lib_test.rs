// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt
#![allow(clippy::unwrap_used)]

use super::*;
use serde_json::Value;

fn capture_json(stages: Stages) -> (Value, Vec<(String, String)>) {
  let dump = capture(&Request {
    stages,
    heap: HeapOptions {
      budget_ms: 1_000,
      owners: true,
    },
  })
  .unwrap();
  (
    serde_json::from_slice(&dump.attachment).unwrap(),
    dump.context,
  )
}

fn context_value<'a>(context: &'a [(String, String)], key: &str) -> Option<&'a str> {
  context
    .iter()
    .find(|(name, _)| name == key)
    .map(|(_, value)| value.as_str())
}

#[test]
fn only_requested_stages_are_reported() {
  let (json, context) = capture_json(Stages {
    counters: true,
    ..Stages::default()
  });
  assert_eq!(json["schema_version"], 1);
  assert!(json["counters"]["phys_footprint"].as_u64().unwrap() > 0);
  assert!(json.get("regions").is_none());
  assert!(json.get("heap").is_none());
  assert_eq!(context_value(&context, "stages"), Some("counters"));
  assert!(context_value(&context, "counters_ms").is_some());
}

#[test]
fn footprint_is_reported_around_the_stages() {
  let (_, context) = capture_json(Stages::default());
  assert!(context_value(&context, "phys_footprint_before").is_some());
  assert!(context_value(&context, "phys_footprint_after").is_some());
}
