// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

use serde::Deserialize;
use std::collections::HashMap;

#[derive(Deserialize)]
struct Definition {
  r#enum: Option<Vec<String>>,
}

#[derive(Deserialize)]
struct Schema {
  definitions: HashMap<String, Definition>,
}

fn generate_swift(types: &[String]) {
  let cases = types
    .iter()
    .enumerate()
    .map(|(i, t)| format!("      case {} = {}", t.to_lowercase(), i))
    .collect::<Vec<String>>()
    .join("\n\n");
  println!(
    "// THIS IS A GENERATED FILE.
// See bitdrift_public.fbs.logging.v1.LogType in https://github.com/bitdriftlabs/proto// \
     for the source enum.

extension Logger {{
    enum LogType: UInt32 {{
{cases}
    }}
}}",
  );
}

fn generate_kotlin(types: &[String]) {
  let cases = types
    .iter()
    .enumerate()
    .map(|(i, t)| format!("  {}({}),", t.to_uppercase(), i))
    .collect::<Vec<String>>()
    .join("\n");

  println!(
    "package io.bitdrift.capture
// THIS IS A GENERATED FILE.
// See bitdrift_public.fbs.logging.v1.LogType \
     in https://github.com/bitdriftlabs/proto// for the source enum.

enum class LogType(val value: Int) {{
{cases}
}}",
  );
}

// Expected usage: ./main descriptor.json kotlin|swift > output
fn main() {
  static LOG_TYPE_FQN: &str = "bitdrift_public_fbs_logging_v1_LogType";
  let descriptor_json_file = std::env::args().nth(1).expect("invalid usage");
  let data = std::fs::read(descriptor_json_file).expect("failed to read descriptor file");

  let schema: Schema = serde_json::from_slice(&data).unwrap();

  let log_type_def = schema
    .definitions
    .get(LOG_TYPE_FQN)
    .expect("did not find definition for {LOG_TYPE_FQN}");

  let enum_cases = log_type_def.r#enum.as_ref().unwrap();
  match std::env::args().nth(2).expect("invalid usage").as_str() {
    "kotlin" => generate_kotlin(enum_cases),
    "swift" => generate_swift(enum_cases),
    _ => panic!("invalid usage"),
  }
}
