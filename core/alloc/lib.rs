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
#[rustc_std_internal_symbol]
pub unsafe fn __rust_alloc_error_handler(_layout: Layout) -> ! {
  // Abort by default, same as System allocator's default
  panic!("Allocation error")
}

/// Required by the allocator ABI. Determines panic behavior on OOM.
/// Returning 0 means "do not panic, just abort".
#[rustc_std_internal_symbol]
pub static __rust_alloc_error_handler_should_panic: u8 = 0;
