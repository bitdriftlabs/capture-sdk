// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

#pragma once

#import <Foundation/Foundation.h>

NS_ASSUME_NONNULL_BEGIN

@interface BDPreviousRunInfoSnapshot : NSObject

@property(nonatomic, readonly, copy) NSString *appVersion;
@property(nonatomic, readonly, copy) NSString *osVersion;
@property(nonatomic, readonly, copy) NSString *binaryUUID;
@property(nonatomic, readonly) uint64_t bootTime;
@property(nonatomic, readonly) BOOL wasCleanExit;

- (instancetype)initWithAppVersion:(NSString *)appVersion
                         osVersion:(NSString *)osVersion
                        binaryUUID:(NSString *)binaryUUID
                          bootTime:(uint64_t)bootTime
                      wasCleanExit:(BOOL)wasCleanExit;

@end

@interface BDPreviousRunInfoRepository : NSObject

- (nullable instancetype)initWithDirectory:(NSURL *)directory error:(NSError **)error NS_DESIGNATED_INITIALIZER;
- (instancetype)init NS_UNAVAILABLE;

- (nullable BDPreviousRunInfoSnapshot *)loadPreviousRunInfoAndReturnError:(NSError **)error;
- (BOOL)prepareCurrentRunInfoWithAppVersion:(NSString *)appVersion
                                  osVersion:(NSString *)osVersion
                                 binaryUUID:(NSString *)binaryUUID
                                   bootTime:(uint64_t)bootTime
                                      error:(NSError **)error;
- (void)markTerminating;

@end

NS_ASSUME_NONNULL_END
