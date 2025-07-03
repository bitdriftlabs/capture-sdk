// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

#![feature(linkage)]
#![feature(rustc_attrs)]
#![allow(internal_features)]

use core::alloc::Layout;
use std::alloc::System;

/// Use the system allocator (via std) as the global allocator.
#[global_allocator]
static GLOBAL: System = System;

/// Signal to the compiler that we *do not* want the old `_rust_alloc_*` C shim fallback.
#[no_mangle]
pub static __rust_no_alloc_shim_is_unstable: u8 = 0;

/// Required by the allocator ABI. Called on allocation failure.
///
/// # Safety
///
/// It might be safe to call panic!() but to avoid a circular dependency in case that's
/// allocating memory itself, and to avoid potential issues with unwinding,
/// we use the safer `std::process::abort()` here.
#[rustc_std_internal_symbol]
#[linkage = "weak"]
pub unsafe fn __rust_alloc_error_handler(_layout: Layout) -> ! {
  // Abort by default, same as System allocator's default
  std::process::abort()
}

/// Required by the allocator ABI. Determines panic behavior on OOM.
/// Returning 0 means "do not panic, just abort".
#[rustc_std_internal_symbol]
#[allow(non_upper_case_globals)]
#[linkage = "weak"]
pub static __rust_alloc_error_handler_should_panic: u8 = 0;
