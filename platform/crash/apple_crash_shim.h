// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.txt

#import <stdbool.h>
#import <stdint.h>

typedef struct {
  const char *name;
  const char *reason;
  uint16_t frame_count;
} BDAppleCrashExceptionMetadata;

// Copies the NSException metadata into buffers owned by the caller. The returned string pointers
// belong to the exception and remain valid for the duration of the call.
bool bd_apple_crash_snapshot_nsexception(
    void *exception,
    BDAppleCrashExceptionMetadata *metadata,
    uint64_t *return_addresses,
    uint16_t return_addresses_capacity);
