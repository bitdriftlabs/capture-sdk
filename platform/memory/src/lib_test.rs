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
fn requested_stages_are_named_in_execution_order() {
  let (json, context) = capture_json(Stages {
    counters: true,
    heap: true,
    ..Stages::default()
  });
  assert_eq!(json["schema_version"], 1);
  assert_eq!(context_value(&context, "stages"), Some("counters,heap"));
}
