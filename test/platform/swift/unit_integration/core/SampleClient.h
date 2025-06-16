// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

/// An example client to `BD_InternalAPI_Capture` (see `InternalAPI.m`).
/// We've exposed the `exposeAPI` method so that the unit tests can attempt to map more methods

#import <Foundation/Foundation.h>

NS_ASSUME_NONNULL_BEGIN

@interface SampleClient : NSObject

+ (instancetype _Nullable)instance;

- (NSError *)exposeAPI:(NSString * _Nonnull)apiName asSelector:(SEL)asSelector;


#pragma mark APIs we will expose from the host library

- (NSString *)example; // Example selector. Not used in unit tests.

// These are used in the unit tests:

- (NSString *)idReturnMethod1;
- (NSString *)idReturnMethod2;

- (void)voidReturnMethod1;
- (void)voidReturnMethod2;

- (NSString *)proxyReturnMethod1;

@end

NS_ASSUME_NONNULL_END
