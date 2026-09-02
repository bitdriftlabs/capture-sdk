// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.txt

#import "apple_crash_shim.h"
#import <Foundation/Foundation.h>

bool bd_apple_crash_snapshot_nsexception(void *exception,
                                         BDAppleCrashExceptionMetadata *metadata,
                                         uint64_t *return_addresses,
                                         uint16_t return_addresses_capacity) {
  if (exception == NULL || metadata == NULL || return_addresses == NULL) {
    return false;
  }

  NSException *nsException = (__bridge NSException *)exception;
  metadata->name = nsException.name.UTF8String;
  metadata->reason = nsException.reason.UTF8String;

  NSArray<NSNumber *> *addresses = nsException.callStackReturnAddresses;
  NSUInteger count = MIN(addresses.count, (NSUInteger)return_addresses_capacity);
  metadata->frame_count = (uint16_t)count;
  for (NSUInteger index = 0; index < count; index++) {
    return_addresses[index] = addresses[index].unsignedLongLongValue;
  }

  return true;
}
