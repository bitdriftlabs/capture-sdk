// AI workflow instrumentation library for Android
// This crate provides instrumentation for AI workflows in the Android SDK.

#![deny(
  clippy::expect_used,
  clippy::panic,
  clippy::todo,
  clippy::unimplemented,
  clippy::unreachable,
  clippy::unwrap_used
)]


#[no_mangle]
pub extern "C" fn get_string_length(s: *const u8, len: usize) -> usize {
    if s.is_null() {
        return 0;
    }
    unsafe {
        let slice = std::slice::from_raw_parts(s, len);
        slice.len()
    }
}

