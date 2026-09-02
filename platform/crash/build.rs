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
