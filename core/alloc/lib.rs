#[global_allocator]
static GLOBAL: std::alloc::System = std::alloc::System;

//#[alloc_error_handler]
//fn oom(layout: std::alloc::Layout) -> ! {
//  core::intrinsics::abort()
//}
