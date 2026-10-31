// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/licenses/strict/1.0.0

#[cfg(test)]
#[path = "./main_test.rs"]
mod tests;

use bd_proto::protos::workflow::workflow_command::WellKnownCommandType;
use bd_proto::protos::workflow::workflow_command::exts::well_known_command_options;
use protobuf::EnumFull;
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

fn swift_command_name(name: &str) -> String {
  name
    .split('_')
    .filter(|word| !word.is_empty())
    .enumerate()
    .map(|(index, word)| {
      let lowercase = word.to_ascii_lowercase();
      if index == 0 {
        lowercase
      } else {
        let (first, rest) = lowercase.split_at(1);
        format!("{}{rest}", first.to_ascii_uppercase())
      }
    })
    .collect()
}

fn builtin_commands() -> Vec<(String, i32, String)> {
  WellKnownCommandType::enum_descriptor()
    .values()
    .filter(|value| value.value() != 0)
    .map(|value| {
      let metadata = well_known_command_options
        .get(value.proto().options.as_ref().expect("command options"))
        .expect("well-known command metadata");
      (value.name().to_string(), value.value(), metadata.key)
    })
    .collect()
}

fn generate_builtin_commands(language: &str) {
  let commands = builtin_commands();
  println!("// Generated from workflow_command.proto; do not edit.");
  match language {
    "kotlin" => {
      println!("package io.bitdrift.capture.commands\n");
      println!("enum class BuiltinCommandType(val wireValue: Int, val key: String) {{");
      for (name, value, key) in commands {
        println!("    {name}({value}, {key:?}),");
      }
      println!("}}");
    },
    "swift" => {
      println!("enum BuiltinCommandType: Int32 {{");
      for (name, value, _) in &commands {
        println!("    case {} = {value}", swift_command_name(name));
      }
      println!("\n    var key: String {{\n        switch self {{");
      for (name, _, key) in &commands {
        println!("        case .{}: {key:?}", swift_command_name(name));
      }
      println!("        }}\n    }}\n}}");
    },
    _ => panic!("invalid language"),
  }
}

// Expected usage: ./main descriptor.json|builtin kotlin|swift > output
fn main() {
  static LOG_TYPE_FQN: &str = "bitdrift_public_fbs_logging_v1_LogType";
  let descriptor_json_file = std::env::args().nth(1).expect("invalid usage");
  let language = std::env::args().nth(2).expect("invalid usage");
  if descriptor_json_file == "builtin" {
    generate_builtin_commands(&language);
    return;
  }
  let data = std::fs::read(descriptor_json_file).expect("failed to read descriptor file");

  let schema: Schema = serde_json::from_slice(&data).unwrap();

  let log_type_def = schema
    .definitions
    .get(LOG_TYPE_FQN)
    .expect("did not find definition for {LOG_TYPE_FQN}");

  let enum_cases = log_type_def.r#enum.as_ref().unwrap();
  match language.as_str() {
    "kotlin" => generate_kotlin(enum_cases),
    "swift" => generate_swift(enum_cases),
    _ => panic!("invalid usage"),
  }
}
