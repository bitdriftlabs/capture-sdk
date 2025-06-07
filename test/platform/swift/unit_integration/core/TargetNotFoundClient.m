// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

#import "TargetNotFoundClient.h"

#pragma clang diagnostic push
#pragma clang diagnostic ignored "-Wincomplete-implementation"
@implementation TargetNotFoundClient

#pragma mark Host library and APIs to expose

static NSString *hostLibraryAPIClassName = @"BD_InternalAPI_SomeNonexistentLibrary";

static void exposeAPIs(id self) {
    NSError *error = [self exposeAPI:@"example_v1" asSelector:@selector(example)];
    if(error != nil) {
        // This would only happen if the ObjC runtime is very broken.
        NSLog(@"API \"%@\" is unsafe to call", NSStringFromSelector(@selector(example)));
    }
}


#pragma mark General code - Do not change

static id instance = nil;

+ (void)initialize {
    Class cls = NSClassFromString(hostLibraryAPIClassName);
    static dispatch_once_t once;
    dispatch_once(&once, ^{
        instance = [cls instance];
        if(instance != nil) {
            exposeAPIs(self.instance);
        } else {
            NSLog(@"Note: API class %@ was not found. Calls to this API will no-op.", hostLibraryAPIClassName);
            instance = [[self alloc] init];
        }
    });
}

+ (instancetype _Nullable)instance {
    return instance;
}

- (void)forwardInvocation:(NSInvocation *)invocation {
    NSLog(@"WARNING: API class %@ was not found. Called selector '%@' is a no-op.", hostLibraryAPIClassName, NSStringFromSelector(invocation.selector));
}

-(NSMethodSignature*)methodSignatureForSelector:(SEL)selector {
    // We must return a real signature or else it will crash, so use NSObject.init because it always exists.
    return [NSObject instanceMethodSignatureForSelector:@selector(init)];
}

@end
