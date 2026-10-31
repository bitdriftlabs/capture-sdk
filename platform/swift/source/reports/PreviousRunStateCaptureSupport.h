// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/licenses/strict/1.0.0

#pragma once

#import <Foundation/Foundation.h>

NS_ASSUME_NONNULL_BEGIN

@interface BDPreviousRunStateCaptureSupport : NSObject

+ (nullable NSString *)mainBinaryUUID;
+ (nullable NSString *)osBuildVersion;
+ (uint64_t)systemBootTime;

@end

NS_ASSUME_NONNULL_END
