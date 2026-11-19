// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

use std::env;

fn main() {
  if env::var("CARGO_CFG_TARGET_VENDOR").as_deref() != Ok("apple") {
    return;
  }

  cc::Build::new()
    .file("apple_crash_shim.m")
    .flag("-fobjc-arc")
    .compile("apple_crash_shim");
  println!("cargo:rustc-link-lib=framework=Foundation");
}
