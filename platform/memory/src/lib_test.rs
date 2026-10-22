// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt
#![allow(clippy::unwrap_used)]

use super::*;
use crate::test_support::memory_lock;
use serde_json::Value;

fn capture_json(stages: Stages) -> (Value, Vec<(String, String)>) {
  let _lock = memory_lock();
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
    regions: true,
    ..Stages::default()
  });
  assert_eq!(json["schema_version"], 1);
  assert!(json.get("counters").is_none());
  assert!(json.get("heap").is_none());
  assert!(json["regions"]["compressed"].is_u64());
  assert!(json["regions"].get("mode").is_none());
  assert_eq!(context_value(&context, "stages"), Some("regions"));
  assert!(context_value(&context, "regions_ms").is_some());
}

#[test]
fn every_stage_runs_in_order() {
  let (json, context) = capture_json(Stages {
    counters: true,
    regions: true,
    heap: true,
  });
  assert!(json["counters"]["phys_footprint"].as_u64().unwrap() > 0);
  assert!(!json["heap"]["by_type"].as_array().unwrap().is_empty());
  assert!(!json["heap"]["by_image"].as_array().unwrap().is_empty());
  assert_eq!(
    context_value(&context, "stages"),
    Some("counters,regions,heap")
  );
  assert_eq!(context_value(&context, "heap_truncated"), Some("false"));
  assert!(context_value(&context, "phys_footprint_before").is_some());
  assert!(context_value(&context, "phys_footprint_after").is_some());
}

#[test]
fn payload_carries_no_addresses() {
  let (json, _) = capture_json(Stages {
    regions: true,
    heap: true,
    ..Stages::default()
  });
  let payload = json.to_string();
  assert!(!payload.contains("0x"));
  assert!(!payload.contains("/Users/"));
}
