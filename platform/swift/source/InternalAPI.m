// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

#import "InternalAPI.h"
#import <objc/runtime.h>


#pragma mark Library Specific

// Place library-specific things (declarations, imports, etc) here.
// Library-specific API implementations are at the end of this file.
// Everything else in between should not be touched.

#define LIBRARY_NAME Capture


#pragma mark Base Implementation

#define CONCAT(a, ...)   CONCAT2(a, __VA_ARGS__)
#define CONCAT2(a, ...)  a ## __VA_ARGS__
#define STRINGIFY(s)     STRINGIFY2(s)
#define STRINGIFY2(s)    #s

#define API_CLASS    CONCAT(BD_InternalAPI_, LIBRARY_NAME)
#define PROXY_CLASS  CONCAT(BD_Proxy_, LIBRARY_NAME)
#define LIB_DOMAIN   @"io.bitdrift." STRINGIFY(LIBRARY_NAME)


/**
 * Proxy that no-ops whenever an unimplemented method is called.
 */
@interface PROXY_CLASS : NSProxy

@property(nonatomic,strong) id proxied;

@end

@implementation PROXY_CLASS

+ (instancetype)proxyTo:(id)objectToProxy {
    if (objectToProxy == nil) {
        return nil;
    }

    PROXY_CLASS *proxy = [PROXY_CLASS alloc];
    proxy.proxied = objectToProxy;
    return proxy;
}

- (void)forwardInvocation:(NSInvocation *)invocation {
    if ([self.proxied respondsToSelector:invocation.selector]) {
        [invocation setTarget:self.proxied];
        [invocation invoke];
    }
}

-(NSMethodSignature*)methodSignatureForSelector:(SEL)selector {
    NSMethodSignature *signature = [self.proxied methodSignatureForSelector:selector];
    if (signature != nil) {
        return signature;
    }

    // We must return a real signature or else it will crash, so use NSObject.init because it always exists.
    return [NSObject instanceMethodSignatureForSelector:@selector(init)];
}

- (BOOL)isEqual:(id)object                       { return [self.proxied isEqual:object]; }
- (NSUInteger)hash                               { return [self.proxied hash]; }
- (Class)superclass                              { return [self.proxied superclass]; }
- (Class)class                                   { return [self.proxied class]; }
- (BOOL)isProxy                                  { return YES; }
- (BOOL)isKindOfClass:(Class)aClass              { return [self.proxied isKindOfClass:aClass]; }
- (BOOL)isMemberOfClass:(Class)aClass            { return [self.proxied isMemberOfClass:aClass]; }
- (BOOL)conformsToProtocol:(Protocol *)aProtocol { return [self.proxied conformsToProtocol:aProtocol]; }
- (BOOL)respondsToSelector:(SEL)aSelector        { return [self.proxied respondsToSelector:aSelector]; }
- (NSString *)description                        { return [self.proxied description]; }
- (NSString *)debugDescription                   { return [self.proxied debugDescription]; }

@end


@implementation API_CLASS

+ (instancetype)instance {
    static API_CLASS *instance;
    static dispatch_once_t once;
    dispatch_once(&once, ^{ instance = [[self alloc] init]; });
    return instance;
}

/**
 * "null" method that gets mapped if a client requests an API name that doesn't exist.
 */
- (id)nullMethod {
    return nil;
}

/**
 * Expose an API on this class, assigning it to the specified selector so that it can be called normally.
 * If the API name is not found, `nullMethod` will be mapped to the selector.
 *
 * @param apiName A string describing the selector of the internal method to map.
 * @param asSelector The selector to map this method to (if found)
 * @return `nil` on success, or an error if the objc runtime is seriously broken (almost impossible).
 */
- (NSError *)exposeAPI:(NSString * _Nonnull)apiName asSelector:(SEL)asSelector {
    if(class_getInstanceMethod(self.class, asSelector) != nil) {
        NSLog(@"WARNING: Class %@ already implements selector '%@'. Keeping existing mapping.", self.class, NSStringFromSelector(asSelector));
        return nil;
    }

    SEL selectorToClone = @selector(nullMethod);

    SEL foundSelector = NSSelectorFromString(apiName);
    if(class_getInstanceMethod(self.class, foundSelector) != nil) {
        selectorToClone = foundSelector;
    } else {
        NSLog(@"WARNING: Class %@ doesn't have method '%@' to clone. Mapping to a null implementation.", self.class, apiName);
    }

#define NSERROR(FMT, ...) [NSError errorWithDomain:LIB_DOMAIN code:0 userInfo:@{ \
    NSLocalizedDescriptionKey:[NSString stringWithFormat:FMT, __VA_ARGS__] }];

    // Note: These errors should never happen unless the objective-c runtime is seriously broken.

    Method method = class_getInstanceMethod(self.class, selectorToClone);
    if(method == nil) {
        return NSERROR(@"class_getInstanceMethod(%@, %@) failed", self.class, NSStringFromSelector(selectorToClone));
    }

    IMP implementation = method_getImplementation(method);
    if(implementation == nil) {
        return NSERROR(@"method_getImplementation(%@) failed", NSStringFromSelector(selectorToClone));
    }

    const char *encoding = method_getTypeEncoding(method);
    if(encoding == nil) {
        return NSERROR(@"method_getTypeEncoding(%@) failed", NSStringFromSelector(selectorToClone));
    }

    if(!class_addMethod(self.class, asSelector, implementation, encoding)) {
        return NSERROR(@"class_addMethod(%@, %@, ...) failed", self.class, NSStringFromSelector(asSelector));
    }

    return nil;
}

- (void)forwardInvocation:(NSInvocation *)invocation {
    // If a particular API call will be made often, probably best to not log here.
    NSLog(@"WARNING: Called nonexistent API '%@', which will no-op", NSStringFromSelector(invocation.selector));
}

-(NSMethodSignature*)methodSignatureForSelector:(SEL)selector {
    // We must return a real signature or else it will crash, so use NSObject.init because it always exists.
    return [NSObject instanceMethodSignatureForSelector:@selector(init)];
}


#pragma mark API implementation


#ifdef DEBUG
// These are called by the unit tests. Do not remove them!

- (void)exampleWithVoidReturn_v1 {
}

- (NSString *)exampleWithIDReturn_v1 {
    return @"This is an example API";
}

- (NSString *)exampleWithProxyReturn_v1 {
    return (NSString *)[PROXY_CLASS proxyTo:@"This is a proxied string"];
}
#endif

@end
