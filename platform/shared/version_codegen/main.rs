use std::env::args;
use std::fs::read_to_string;
use std::path::Path;

pub fn main() {
  let version_path = args().nth_back(0).unwrap();
  eprintln!("version_path: {}", &version_path);
  let version = read_to_string(Path::new(&version_path)).unwrap();

  println!(
    "package io.bitdrift.capture
// THIS IS A GENERATED FILE
object BuildConstants {{
    const val SDK_VERSION = \"{version}\"
}}",
  );
}
