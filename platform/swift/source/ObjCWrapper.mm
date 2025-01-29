// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

#import <Foundation/Foundation.h>
#import "ObjCWrapper.h"

@implementation ObjCWrapper

+ (BOOL)doTry:(void(^)())block error:(NSError **)err {
    @try {
        block();
        return YES;
    }
    @catch (NSException *exception) {
        *err = [NSError errorWithDomain:@"io.bitdrift"
                                   code:-1
                               userInfo:@{@"exception": exception}];
        return NO;
    }
}

@end
