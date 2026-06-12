// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

#import <Foundation/Foundation.h>

NS_ASSUME_NONNULL_BEGIN

#ifdef __cplusplus
extern "C" {
#endif

FOUNDATION_EXPORT void hello_world_crash_objc_exception(void);
FOUNDATION_EXPORT void hello_world_crash_cxx_exception(void);
FOUNDATION_EXPORT void hello_world_crash_objc_msg_send(void);
FOUNDATION_EXPORT void hello_world_crash_released_object(void);
FOUNDATION_EXPORT void hello_world_crash_corrupt_malloc(void);
FOUNDATION_EXPORT void hello_world_crash_stack_overflow(void);
FOUNDATION_EXPORT void hello_world_crash_unrecognized_selector(void);
FOUNDATION_EXPORT void hello_world_crash_kvo(void);
FOUNDATION_EXPORT void hello_world_crash_stack_smash(void);
FOUNDATION_EXPORT void hello_world_crash_nsa_range_exception(void);
FOUNDATION_EXPORT void hello_world_crash_nil_argument(void);
FOUNDATION_EXPORT void hello_world_crash_objc_through_cpp(void);
FOUNDATION_EXPORT uint64_t hello_world_cached_kscrash_timestamp(void);

#ifdef __cplusplus
}
#endif

NS_ASSUME_NONNULL_END
