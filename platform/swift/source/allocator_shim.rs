// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

#![allow(internal_features)]
#![allow(non_upper_case_globals)]
#![feature(linkage)]
#![feature(rustc_attrs)]
#![no_std]

// This reproduces the no-std allocator shim rules_rust 0.65 supplied before the migration.
// Apple links this rlib after Rust compilation, so it needs the symbols rustc normally provides
// during its final link. The implementations remain in rust-std-mobile's custom stdlib.
unsafe extern "C" {
  #[rustc_std_internal_symbol]
  fn __rdl_alloc(size: usize, align: usize) -> *mut u8;

  #[rustc_std_internal_symbol]
  fn __rdl_dealloc(ptr: *mut u8, size: usize, align: usize);

  #[rustc_std_internal_symbol]
  fn __rdl_realloc(ptr: *mut u8, old_size: usize, align: usize, new_size: usize) -> *mut u8;

  #[rustc_std_internal_symbol]
  fn __rdl_alloc_zeroed(size: usize, align: usize) -> *mut u8;
}

#[linkage = "weak"]
#[rustc_std_internal_symbol]
fn __rust_alloc(size: usize, align: usize) -> *mut u8 {
  unsafe { __rdl_alloc(size, align) }
}

#[linkage = "weak"]
#[rustc_std_internal_symbol]
fn __rust_dealloc(ptr: *mut u8, size: usize, align: usize) {
  unsafe { __rdl_dealloc(ptr, size, align) }
}

#[linkage = "weak"]
#[rustc_std_internal_symbol]
fn __rust_realloc(ptr: *mut u8, old_size: usize, align: usize, new_size: usize) -> *mut u8 {
  unsafe { __rdl_realloc(ptr, old_size, align, new_size) }
}

#[linkage = "weak"]
#[rustc_std_internal_symbol]
fn __rust_alloc_zeroed(size: usize, align: usize) -> *mut u8 {
  unsafe { __rdl_alloc_zeroed(size, align) }
}

#[linkage = "weak"]
#[rustc_std_internal_symbol]
fn __rust_alloc_error_handler(_size: usize, _align: usize) {
  panic!()
}

// Rust 1.95 retains these compatibility exports alongside the allocator entry points.
#[linkage = "weak"]
#[rustc_std_internal_symbol]
static mut __rust_alloc_error_handler_should_panic: u8 = 1;

#[linkage = "weak"]
#[rustc_std_internal_symbol]
fn __rust_alloc_error_handler_should_panic_v2() -> u8 {
  1
}

#[linkage = "weak"]
#[rustc_std_internal_symbol]
static mut __rust_no_alloc_shim_is_unstable: u8 = 0;

#[linkage = "weak"]
#[rustc_std_internal_symbol]
fn __rust_no_alloc_shim_is_unstable_v2() {}
