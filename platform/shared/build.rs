// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

use std::path::Path;
use std::{env, fs};

fn main() {
  let out_dir = env::var_os("OUT_DIR").unwrap();
  let dest_path = Path::new(&out_dir).join("version.rs");

  let version = fs::read_to_string(Path::new(".sdk_version")).unwrap();

  fs::write(&dest_path, format!("\"{version}\"")).unwrap();
  println!("cargo::rerun-if-changed=build.rs");
  println!("cargo::rerun-if-changed=.sdk_version");
}
