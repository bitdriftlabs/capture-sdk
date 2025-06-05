// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

/// Mechanism for exposing internal APIs to other libraries without exposing them publicly.
///
/// Place your exposable APIs in the `API Implementation` section at the bottom of the .m file.
/// They DON'T need to be declared in this header (although you can if you want to call them locally).
///
/// Be sure to version all APIs so that their behaviors can be safely modified in future,
/// and also proxy any returned non-standard-library objects so that they are safe to change in future.
///
/// ## How to use it (from a client)
///
/// Clients call `exposeAPI` to attach an internal (versioned) API method to the selector of their choice. e.g.
/// ```
/// NSError *error = [self.instance exposeAPI:@"example_v1" asSelector:@selector(example)];
/// ```
/// This code will add a new method with the selector `example` that actually calls the implementation of `example_v1` (if found).
/// See section `Implementing a client` for how to set up a client-library class to do this.
///
/// Note: In the following situations, calling the exposed method will no-op:
///  * The host API class is not found (e.g. this library hasn't been linked in)
///  * The requested API is not found (maybe it was misspelled, or has been removed)
///
///
/// ## Cloning this internal API hosting code for use in another library
///
/// Copy all code in the .m file except for these sections, which must be tailored to your implementation:
///  * `Library Specific`
///  * `API Implementation`
/// The rest is implementation-agnostic code.
///
///
/// ## Implementing a client
///
/// A client class (in the calling library) would look like this:
///
/// **ExampleClient.h**
///
/// ```objc
/// #import <Foundation/Foundation.h>
///
/// NS_ASSUME_NONNULL_BEGIN
///
/// @interface ExampleClient : NSObject
///
/// + (instancetype _Nullable)instance;
///
/// #pragma mark APIs we will expose from the host library
///
/// - (NSString *)example;
///
/// @end
///
/// NS_ASSUME_NONNULL_END
/// ```
///
/// **ExampleClient.m:**
///
/// ```objc
/// #import "ExampleClient.h"
///
/// #pragma clang diagnostic push
/// #pragma clang diagnostic ignored "-Wincomplete-implementation"
/// @implementation ExampleClient
///
/// #pragma mark Host library and APIs to expose
///
/// static NSString *hostLibraryAPIClassName = @"BD_InternalAPI_Capture";
///
/// static void exposeAPIs(id self) {
///     // Example of exposing APIs on initialize
///     NSError *error = [self exposeAPI:@"example_v1" asSelector:@selector(example)];
///     if(error != nil) {
///         // This would only happen if the ObjC runtime is very broken.
///         NSLog(@"API \"%@\" is unsafe to call", NSStringFromSelector(@selector(example)));
///     }
/// }
///
///
/// #pragma mark General code - Do not change
///
/// static id instance = nil;
///
/// + (void)initialize {
///     Class cls = NSClassFromString(hostLibraryAPIClassName);
///     static dispatch_once_t once;
///     dispatch_once(&once, ^{
///         instance = [cls instance];
///         if(instance != nil) {
///             exposeAPIs(self.instance);
///         } else {
///             NSLog(@"WARNING: API class %@ was not found. Calls to this API will no-op.", hostLibraryAPIClassName);
///             instance = [[self alloc] init];
///         }
///     });
/// }
///
/// + (instancetype _Nullable)instance {
///     return instance;
/// }
///
/// - (void)forwardInvocation:(NSInvocation *)invocation {
///     // If API calls will be made often, probably best to not log here.
///     NSLog(@"WARNING: API class %@ was not found. Called selector '%@' is a no-op.", hostLibraryAPIClassName, /// NSStringFromSelector(invocation.selector));
/// }
///
/// -(NSMethodSignature*)methodSignatureForSelector:(SEL)selector {
///     // We must return a real signature or else it will crash, so use NSObject.init because it always exists.
///     return [NSObject instanceMethodSignatureForSelector:@selector(init)];
/// }
///
/// @end
/// ```

#import <Foundation/Foundation.h>

NS_ASSUME_NONNULL_BEGIN

@interface BD_InternalAPI_Capture : NSObject

+ (instancetype)instance;

// If you need to inject data into this internal API, create a `configure` method to do so:
// - (void)configureWithFribblefrabble:(Fribblefrabble *)frib;

@end

NS_ASSUME_NONNULL_END
