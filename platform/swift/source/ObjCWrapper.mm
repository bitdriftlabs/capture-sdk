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
