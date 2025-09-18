// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

/// This client is exactly the same as SampleClient, except that it searches for the nonexistent
/// library `BD_InternalAPI_SomeNonexistentLibrary`, simulating what happens when
/// the target library hasn't been linked into the project.

#import <Foundation/Foundation.h>

NS_ASSUME_NONNULL_BEGIN

@interface TargetNotFoundClient : NSObject

+ (instancetype _Nullable)instance;

- (NSError *)exposeAPI:(NSString * _Nonnull)apiName asSelector:(SEL)asSelector;


#pragma mark APIs we will expose from the host library

- (NSString *)example; // Example selector. Not used in unit tests.

// These are used in the unit tests:

- (NSString *)idReturnMethod1;
- (NSString *)idReturnMethod2;

- (void)voidReturnMethod1;
- (void)voidReturnMethod2;

@end

NS_ASSUME_NONNULL_END
