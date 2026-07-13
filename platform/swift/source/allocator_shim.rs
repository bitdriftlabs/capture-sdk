#![feature(alloc_error_handler)]
#![no_std]

use core::alloc::{GlobalAlloc, Layout};
use core::ffi::{c_int, c_void};
use core::ptr;

unsafe extern "C" {
  fn abort() -> !;
  fn free(ptr: *mut c_void);
  fn posix_memalign(memptr: *mut *mut c_void, alignment: usize, size: usize) -> c_int;
}

struct SystemAllocator;

unsafe impl GlobalAlloc for SystemAllocator {
  unsafe fn alloc(&self, layout: Layout) -> *mut u8 {
    if layout.size() == 0 {
      return layout.align() as *mut u8;
    }

    let mut allocation = ptr::null_mut();
    let alignment = layout.align().max(core::mem::size_of::<*mut c_void>());
    if unsafe { posix_memalign(&mut allocation, alignment, layout.size()) } == 0 {
      allocation.cast()
    } else {
      ptr::null_mut()
    }
  }

  unsafe fn dealloc(&self, ptr: *mut u8, layout: Layout) {
    if layout.size() != 0 {
      unsafe { free(ptr.cast()) };
    }
  }

  unsafe fn alloc_zeroed(&self, layout: Layout) -> *mut u8 {
    let allocation = unsafe { self.alloc(layout) };
    if !allocation.is_null() {
      unsafe { ptr::write_bytes(allocation, 0, layout.size()) };
    }
    allocation
  }

  unsafe fn realloc(&self, ptr: *mut u8, layout: Layout, new_size: usize) -> *mut u8 {
    if new_size > isize::MAX as usize {
      return ptr::null_mut();
    }

    let new_layout = unsafe { Layout::from_size_align_unchecked(new_size, layout.align()) };
    let allocation = unsafe { self.alloc(new_layout) };
    if !allocation.is_null() {
      unsafe { ptr::copy_nonoverlapping(ptr, allocation, layout.size().min(new_size)) };
      unsafe { self.dealloc(ptr, layout) };
    }
    allocation
  }
}

#[global_allocator]
static ALLOCATOR: SystemAllocator = SystemAllocator;

#[alloc_error_handler]
fn allocation_error(_layout: Layout) -> ! {
  unsafe { abort() }
}

#[panic_handler]
fn panic(_info: &core::panic::PanicInfo) -> ! {
  unsafe { abort() }
}
