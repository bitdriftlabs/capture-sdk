// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

#import "CrashSupport.h"
#import <Foundation/Foundation.h>
#import <stdexcept>
#import <stdlib.h>

extern "C" uint64_t capture_cached_kscrash_timestamp(void);

void hello_world_crash_objc_exception(void) {
    @throw [NSException exceptionWithName:NSGenericException
                                   reason:@"Uncaught ObjC exception from hello_world"
                                 userInfo:@{NSLocalizedDescriptionKey: @"Triggered by hello_world"}];
}

void hello_world_crash_cxx_exception(void) {
    throw std::runtime_error("Uncaught C++ exception from hello_world");
}

@interface HelloWorldZombieTarget : NSObject
- (void)doSomething;
@end

@implementation HelloWorldZombieTarget
- (void)doSomething {}
@end

void hello_world_crash_objc_msg_send(void) {
    __unsafe_unretained id zombie;
    @autoreleasepool {
#pragma clang diagnostic push
#pragma clang diagnostic ignored "-Warc-unsafe-retained-assign"
        zombie = [[HelloWorldZombieTarget alloc] init];
#pragma clang diagnostic pop
    }
    [zombie doSomething];
}

void hello_world_crash_released_object(void) {
    NSObject *obj = [NSObject new];
    void *rawPtr = (void *)CFBridgingRetain(obj);
    *((uintptr_t *)rawPtr) = 0xDEADBEEF;
    CFRelease(rawPtr);
    [obj description];
}

void hello_world_crash_corrupt_malloc(void) {
    uint8_t *buf = (uint8_t *)malloc(16);
    memset(buf - 8, 0xAA, 32);
    free(buf);
}

#pragma clang diagnostic push
#pragma clang diagnostic ignored "-Winfinite-recursion"
__attribute__((noinline))
static void hello_world_do_stack_overflow(void) {
    hello_world_do_stack_overflow();
}
#pragma clang diagnostic pop

void hello_world_crash_stack_overflow(void) {
    hello_world_do_stack_overflow();
}

void hello_world_crash_unrecognized_selector(void) {
    NSObject *obj = [[NSObject alloc] init];
#pragma clang diagnostic push
#pragma clang diagnostic ignored "-Warc-performSelector-leaks"
    [obj performSelector:NSSelectorFromString(@"helloWorldNonExistentMethod")];
#pragma clang diagnostic pop
}

void hello_world_crash_kvo(void) {
    NSObject *observed = [[NSObject alloc] init];
    NSObject *observer = [[NSObject alloc] init];
    [observed removeObserver:observer forKeyPath:@"helloWorldKeyPath"];
}

__attribute__((noinline))
void hello_world_crash_stack_smash(void) {
    volatile char buf[8];
    for (int i = 0; i < 256; i++) {
        ((volatile char *)buf)[i] = (char)0xAA;
    }
}

uint64_t hello_world_cached_kscrash_timestamp(void) {
    return capture_cached_kscrash_timestamp();
}
