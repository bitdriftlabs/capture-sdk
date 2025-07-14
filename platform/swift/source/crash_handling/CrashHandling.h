// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

#pragma once

#import <Foundation/Foundation.h>

#include <stdbool.h>
#include <unistd.h>

#ifdef __cplusplus
extern "C" {
#endif

bool bitdrift_install_crash_handler(NSURL *basePath);
void bitdrift_uninstall_crash_handler(void);

/**
 * Attempt to begin handling a crash.
 * Returns false if we're already handling a crash.
 */
bool bitdrift_begin_handling_crash(void);

NSDictionary *bitdrift_getLastReport(NSURL *basePath);

#ifdef __cplusplus
}
#endif
