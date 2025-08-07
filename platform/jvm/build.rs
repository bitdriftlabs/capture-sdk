fn main() {
  // We only want to enable the `-Zfmt-debug=shallow` flag on release builds
  // and only if we're on the nightly channel or if RUSTC_BOOTSTRAP is set.
  //
  // This ensures that this is set during release builds whenever the argument is available.
  #[cfg(not(debug_assertions))]
  {
    if is_nightly_channel() || std::env::var("RUSTC_BOOTSTRAP").as_deref() == Ok("1") {
      println!("cargo:rustflag=-Zfmt-debug=shallow");
      panic!("nightly!");
    }
  }
}

#[cfg(not(debug_assertions))]
fn is_nightly_channel() -> bool {
  std::process::Command::new("rustc")
    .arg("--version")
    .output()
    .map(|output| String::from_utf8_lossy(&output.stdout).contains("nightly"))
    .unwrap_or(false)
}
