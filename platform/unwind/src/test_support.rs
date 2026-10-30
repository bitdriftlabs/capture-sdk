// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

use std::alloc::{GlobalAlloc, Layout, System};
use std::cell::Cell;

std::thread_local! {
  static ALLOCATIONS: Cell<Option<usize>> = const { Cell::new(None) };
}

//
// TrackingAllocator
//

struct TrackingAllocator;

#[global_allocator]
static ALLOCATOR: TrackingAllocator = TrackingAllocator;

fn record_allocation() {
  // Each test thread tracks only its own critical section, so the test runner and parallel tests
  // cannot make the count flaky. try_with also handles allocation during thread teardown.
  let _ = ALLOCATIONS.try_with(|count| {
    if let Some(value) = count.get() {
      count.set(Some(value + 1));
    }
  });
}

// SAFETY: allocation/deallocation is forwarded unchanged to System; the TLS count owns no memory
// and does not allocate or change the returned pointers.
unsafe impl GlobalAlloc for TrackingAllocator {
  unsafe fn alloc(&self, layout: Layout) -> *mut u8 {
    record_allocation();
    // SAFETY: forwards the allocator contract from the caller.
    unsafe { System.alloc(layout) }
  }

  unsafe fn alloc_zeroed(&self, layout: Layout) -> *mut u8 {
    record_allocation();
    // SAFETY: forwards the allocator contract from the caller.
    unsafe { System.alloc_zeroed(layout) }
  }

  unsafe fn realloc(&self, pointer: *mut u8, layout: Layout, size: usize) -> *mut u8 {
    record_allocation();
    // SAFETY: forwards the live allocation and requested layout from the caller.
    unsafe { System.realloc(pointer, layout, size) }
  }

  unsafe fn dealloc(&self, pointer: *mut u8, layout: Layout) {
    // SAFETY: forwards the live allocation and its layout from the caller.
    unsafe { System.dealloc(pointer, layout) }
  }
}

pub fn count_allocations<T>(operation: impl FnOnce() -> T) -> (T, usize) {
  struct Reset;
  impl Drop for Reset {
    fn drop(&mut self) {
      ALLOCATIONS.with(|count| count.set(None));
    }
  }

  ALLOCATIONS.with(|count| count.set(Some(0)));
  let reset = Reset;
  let result = operation();
  let count = ALLOCATIONS.with(|count| count.get().unwrap_or(0));
  drop(reset);
  (result, count)
}
