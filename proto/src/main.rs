// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

#[cfg(test)]
#[path = "./main_test.rs"]
mod tests;

use protobuf::reflect::{EnumValueDescriptor, FileDescriptor, ReflectValueRef};
use protobuf::{Message, UnknownValueRef};
use protobuf_parse::Parser;
use std::path::Path;

const WORKFLOW_COMMAND_PROTO: &str = "bitdrift_public/protobuf/workflow/v1/workflow_command.proto";

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

fn builtin_commands(schema: &Path) -> Vec<BuiltinCommand> {
  // The input tree mirrors the proto import paths. The other well-known imports
  // (descriptor, duration, and timestamp) are provided by the protobuf parser.
  let imports = schema
    .ancestors()
    .nth(Path::new(WORKFLOW_COMMAND_PROTO).components().count())
    .expect("missing proto import root");
  let parsed = Parser::new()
    .pure()
    .include(imports)
    .input(schema)
    .parse_and_typecheck()
    .expect("parse workflow command schema");
  let files =
    FileDescriptor::new_dynamic_fds(parsed.file_descriptors, &[]).expect("resolve schema types");
  let workflow = files
    .iter()
    .find(|file| file.name() == WORKFLOW_COMMAND_PROTO)
    .expect("missing workflow command schema");
  let command_type = workflow
    .enum_by_package_relative_name("WellKnownCommandType")
    .expect("missing command enum");
  let options = workflow
    .message_by_package_relative_name("WellKnownCommandOptions")
    .expect("missing command options type");
  let extension = workflow
    .proto()
    .extension
    .iter()
    .find(|field| field.name() == "well_known_command_options")
    .expect("missing command options extension");
  let option_number = u32::try_from(extension.number()).expect("invalid extension number");
  let key_field = options.field_by_name("key").expect("missing key field");
  let arguments_field = options
    .field_by_name("argument_definitions")
    .expect("missing arguments field");
  let argument = workflow
    .message_by_package_relative_name("DeviceCommandArgumentDefinition")
    .expect("missing argument type");
  let name_field = argument
    .field_by_name("name")
    .expect("missing argument name field");
  let type_field = argument
    .field_by_name("type")
    .expect("missing argument type field");

  command_type
    .values()
    .filter(|value| value.value() != 0)
    .map(|value| {
      let metadata = value.proto().options.as_ref().expect("command options");
      let Some(UnknownValueRef::LengthDelimited(bytes)) =
        metadata.unknown_fields().get(option_number)
      else {
        panic!("missing command options for {}", value.name());
      };
      let metadata = options
        .parse_from_bytes(bytes)
        .expect("decode command options");
      let ReflectValueRef::String(key) = key_field.get_singular_field_or_default(&*metadata) else {
        panic!("invalid key for {}", value.name());
      };
      // Only the profiling handlers use generated parsers. Other built-ins keep their own
      // parsing, including platform-specific, optional and dynamically typed arguments.
      let arguments = if matches!(
        value.name(),
        "STACK_SAMPLING_TRACE" | "HEAP_PROFILE_TRACE" | "SYSTEM_TRACE" | "MEMORY_DUMP"
      ) {
        arguments_field
          .get_repeated(&*metadata)
          .into_iter()
          .map(|field| {
            let ReflectValueRef::Message(field) = field else {
              panic!("invalid argument for {}", value.name());
            };
            let ReflectValueRef::String(name) = name_field.get_singular_field_or_default(&*field)
            else {
              panic!("invalid argument name for {}", value.name());
            };
            let ReflectValueRef::Enum(types, number) =
              type_field.get_singular_field_or_default(&*field)
            else {
              panic!("invalid type for argument {name}");
            };
            let kotlin_type = match types
              .value_by_number(number)
              .as_ref()
              .map(EnumValueDescriptor::name)
            {
              Some("STRING") => "String",
              Some("BINARY") => "ByteArray",
              Some("UINT64") => "ULong",
              Some("DOUBLE") => "Double",
              Some("INT64") => "Long",
              Some("BOOL") => "Boolean",
              other => panic!("unsupported type {other:?} for argument {name}"),
            };
            (name.to_owned(), kotlin_type)
          })
          .collect()
      } else {
        Vec::new()
      };
      (
        value.name().to_owned(),
        value.value(),
        key.to_owned(),
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
  let Some(first) = chars.next() else {
    return String::new();
  };
  first.to_ascii_uppercase().to_string() + chars.as_str()
}

fn generate_builtin_commands(schema: &Path, language: &str) {
  let commands = builtin_commands(schema);
  // The Kotlin output is checked in for Gradle, so it carries the repository license header.
  for line in [
    "// capture-sdk - bitdrift's client SDK",
    "// Copyright Bitdrift, Inc. All rights reserved.",
    "//",
    "// Use of this source code is governed by a source available license that can be found in the",
    "// LICENSE file or at:",
    "// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt",
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

    /** This command's generated arguments, or null if it does not use a generated parser. */
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

fn main() {
  let mut args = std::env::args().skip(1);
  let schema = args.next().expect("missing workflow command schema");
  let language = args.next().expect("missing language");
  generate_builtin_commands(Path::new(&schema), &language);
}
