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
use bd_proto::protos::workflow::workflow_command::device_command_argument_definition::Type;
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

fn camel_case(name: &str) -> String {
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

/// A built-in's enum name, wire value, key, and arguments as (name, Kotlin type).
type BuiltinCommand = (String, i32, String, Vec<(String, &'static str)>);

fn builtin_commands() -> Vec<BuiltinCommand> {
  WellKnownCommandType::enum_descriptor()
    .values()
    .filter(|value| value.value() != 0)
    .map(|value| {
      let metadata = well_known_command_options
        .get(value.proto().options.as_ref().expect("command options"))
        .expect("well-known command metadata");
      let arguments = metadata
        .argument_definitions
        .iter()
        .map(|argument| {
          // The server fills every default, so an argument is absent only when it is optional
          // without one; the generated classes have no nullable fields for that yet.
          assert!(
            !argument.optional || argument.default_value.is_some(),
            "optional argument {} without a default is not supported",
            argument.name
          );
          let kotlin_type = match argument.type_.enum_value() {
            Ok(Type::STRING) => "String",
            Ok(Type::BINARY) => "ByteArray",
            Ok(Type::UINT64) => "ULong",
            Ok(Type::DOUBLE) => "Double",
            Ok(Type::INT64) => "Long",
            Ok(Type::BOOL) => "Boolean",
            other => panic!("unsupported type {other:?} for argument {}", argument.name),
          };
          (argument.name.clone(), kotlin_type)
        })
        .collect();
      (
        value.name().to_string(),
        value.value(),
        metadata.key,
        arguments,
      )
    })
    .collect()
}

/// An enum entry; one with arguments parses them into its generated class, so handlers never spell
/// argument names.
fn generate_kotlin_entry(name: &str, value: i32, key: &str, arguments: &[(String, &str)]) {
  if arguments.is_empty() {
    println!("    {name}({value}, {key:?}),");
    return;
  }
  println!(
    "    {name}({value}, {key:?}) {{
        override fun parse(scope: CommandScope) =
            {}Arguments(",
    pascal_case(name)
  );
  for (argument, kotlin_type) in arguments {
    println!(
      "                {} = scope.argument<{kotlin_type}>({argument:?}),",
      camel_case(argument)
    );
  }
  println!("            )\n    }},");
}

fn generate_kotlin_arguments(name: &str, arguments: &[(String, &str)]) {
  println!("\ninternal data class {}Arguments(", pascal_case(name));
  for (argument, kotlin_type) in arguments {
    println!("    val {}: {kotlin_type},", camel_case(argument));
  }
  println!(") : BuiltinCommandArguments");
}

fn pascal_case(name: &str) -> String {
  let camel = camel_case(name);
  let mut chars = camel.chars();
  chars
    .next()
    .map(|first| first.to_ascii_uppercase().to_string() + chars.as_str())
    .unwrap_or_default()
}

fn generate_builtin_commands(language: &str) {
  let commands = builtin_commands();
  // The Kotlin output is checked in for Gradle, so it carries the repository license header.
  for line in [
    "// capture-sdk - bitdrift's client SDK",
    "// Copyright Bitdrift, Inc. All rights reserved.",
    "//",
    "// Use of this source code is governed by a source available license that can be found in the",
    "// LICENSE file or at:",
    "// https://polyformproject.org/licenses/strict/1.0.0",
    "",
  ] {
    println!("{line}");
  }
  println!("// Generated from workflow_command.proto; do not edit.");
  match language {
    "kotlin" => {
      println!("package io.bitdrift.capture.commands\n");
      println!("enum class BuiltinCommandType(val wireValue: Int, val key: String) {{");
      for (name, value, key, arguments) in &commands {
        generate_kotlin_entry(name, *value, key, arguments);
      }
      println!(
        "    ;

    /** This command's arguments as its proto definition declares them, or null when it has none. \
         */
    internal open fun parse(scope: CommandScope): BuiltinCommandArguments? = null
}}

/**
 * The arguments of a built-in that declares any, generated from its proto definition. Open, so a
 * consumer handles only the arguments it serves and a new command never breaks an unrelated one.
 */
internal interface BuiltinCommandArguments"
      );
      for (name, _, _, arguments) in &commands {
        if !arguments.is_empty() {
          generate_kotlin_arguments(name, arguments);
        }
      }
    },
    "swift" => {
      println!("enum BuiltinCommandType: Int32 {{");
      for (name, value, ..) in &commands {
        println!("    case {} = {value}", camel_case(name));
      }
      println!("\n    var key: String {{\n        switch self {{");
      for (name, _, key, _) in &commands {
        println!("        case .{}: {key:?}", camel_case(name));
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
