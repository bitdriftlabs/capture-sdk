// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

#import <XCTest/XCTest.h>
#import "SampleClient.h"
#import "TargetNotFoundClient.h"

@interface InternalAPITests : XCTestCase

@end

@implementation InternalAPITests

// NOTE: Methods can only be mapped once, so each test must use a DIFFERENT selector!

- (void)testIDReturn_MethodFound {
    NSError *error = [SampleClient.instance exposeAPI:@"exampleWithIDReturn_v1" asSelector:@selector(idReturnMethod1)];
    XCTAssertNil(error);
    NSString *result = [SampleClient.instance idReturnMethod1];
    XCTAssertEqualObjects(result, @"This is an example API");

    // Calling again should no-op
    error = [SampleClient.instance exposeAPI:@"exampleWithIDReturn_v1" asSelector:@selector(idReturnMethod1)];
    XCTAssertNil(error);
    result = [SampleClient.instance idReturnMethod1];
    XCTAssertEqualObjects(result, @"This is an example API");
}

- (void)testIDReturn_MethodNotFound {
    NSError *error = [SampleClient.instance exposeAPI:@"exampleWithIDReturn_v99" asSelector:@selector(idReturnMethod2)];
    XCTAssertNil(error);
    NSString *ph = [self placeholder];
    NSString *result = [SampleClient.instance idReturnMethod2];
    XCTAssertNil(result);
    // Make sure no stack/return corruption
    XCTAssertEqualObjects(ph, @"placeholder");

    // Calling again should no-op
    error = [SampleClient.instance exposeAPI:@"exampleWithIDReturn_v99" asSelector:@selector(idReturnMethod2)];
    XCTAssertNil(error);
    ph = [self placeholder];
    result = [SampleClient.instance idReturnMethod2];
    XCTAssertNil(result);
    // Make sure no stack/return corruption
    XCTAssertEqualObjects(ph, @"placeholder");
}

- (NSString *)placeholder {
    return @"placeholder";
}

- (void)testIDReturn_TargetNotFound {
    // This client will not find the host class, so everything should no-op.
    NSError *error = [TargetNotFoundClient.instance exposeAPI:@"exampleWithIDReturn_v1" asSelector:@selector(idReturnMethod1)];
    XCTAssertNil(error);
    NSString *ph = [self placeholder];
    NSString *result = [TargetNotFoundClient.instance idReturnMethod1];
    XCTAssertNil(result);
    // Make sure no stack/return corruption
    XCTAssertEqualObjects(ph, @"placeholder");

    // Calling again should no-op
    error = [TargetNotFoundClient.instance exposeAPI:@"exampleWithIDReturn_v1" asSelector:@selector(idReturnMethod1)];
    XCTAssertNil(error);
    ph = [self placeholder];
    result = [TargetNotFoundClient.instance idReturnMethod1];
    XCTAssertNil(result);
    // Make sure no stack/return corruption
    XCTAssertEqualObjects(ph, @"placeholder");
}

- (void)testVoidReturn_MethodFound {
    NSError *error = [SampleClient.instance exposeAPI:@"exampleWithVoidReturn_v1" asSelector:@selector(voidReturnMethod1)];
    XCTAssertNil(error);
    NSString *ph = [self placeholder];
    [SampleClient.instance voidReturnMethod1];
    // Make sure no stack/return corruption
    XCTAssertEqualObjects(ph, @"placeholder");

    // Calling again should no-op
    error = [SampleClient.instance exposeAPI:@"exampleWithVoidReturn_v1" asSelector:@selector(voidReturnMethod1)];
    XCTAssertNil(error);
    ph = [self placeholder];
    [SampleClient.instance voidReturnMethod1];
    // Make sure no stack/return corruption
    XCTAssertEqualObjects(ph, @"placeholder");
}

- (void)testVoidReturn_MethodNotFound {
    NSError *error = [SampleClient.instance exposeAPI:@"exampleWithVoidReturn_v99" asSelector:@selector(voidReturnMethod2)];
    XCTAssertNil(error);
    NSString *ph = [self placeholder];
    [SampleClient.instance voidReturnMethod2];
    // Make sure no stack/return corruption
    XCTAssertEqualObjects(ph, @"placeholder");

    // Calling again should no-op
    error = [SampleClient.instance exposeAPI:@"exampleWithVoidReturn_v99" asSelector:@selector(voidReturnMethod2)];
    XCTAssertNil(error);
    ph = [self placeholder];
    [SampleClient.instance voidReturnMethod2];
    // Make sure no stack/return corruption
    XCTAssertEqualObjects(ph, @"placeholder");
}

- (void)testVoidReturn_TargetNotFound {
    // This client will not find the host class, so everything should no-op.
    NSError *error = [TargetNotFoundClient.instance exposeAPI:@"exampleWithVoidReturn_v1" asSelector:@selector(voidReturnMethod1)];
    XCTAssertNil(error);
    NSString *ph = [self placeholder];
    [TargetNotFoundClient.instance voidReturnMethod1];
    // Make sure no stack/return corruption
    XCTAssertEqualObjects(ph, @"placeholder");

    // Calling again should no-op
    error = [TargetNotFoundClient.instance exposeAPI:@"exampleWithVoidReturn_v1" asSelector:@selector(voidReturnMethod1)];
    XCTAssertNil(error);
    ph = [self placeholder];
    [TargetNotFoundClient.instance voidReturnMethod1];
    // Make sure no stack/return corruption
    XCTAssertEqualObjects(ph, @"placeholder");
}

- (void)testProxyReturn_MethodFound {
    // This tests returning a proxy to an object.
    // We're proxying a string, which is unnecessary, but if you're
    // returning a custom object you should be wrapping it in a proxy.
    NSError *error = [SampleClient.instance exposeAPI:@"exampleWithProxyReturn_v1" asSelector:@selector(proxyReturnMethod1)];
    XCTAssertNil(error);
    NSString *result = [SampleClient.instance proxyReturnMethod1];
    XCTAssertEqualObjects(result, @"This is a proxied string");
    XCTAssertEqual(24, result.length);

    // The proxy object should no-op on nonexistent method calls.
    NSURLSession *asSession = (NSURLSession *)result;
    [asSession setSessionDescription:@"Blah"];
    XCTAssertNil(asSession.configuration);

    // Calling again should no-op
    error = [SampleClient.instance exposeAPI:@"exampleWithProxyReturn_v1" asSelector:@selector(proxyReturnMethod1)];
    XCTAssertNil(error);
    result = [SampleClient.instance proxyReturnMethod1];
    XCTAssertEqualObjects(result, @"This is a proxied string");

    // The proxy object should still no-op on nonexistent method calls.
    asSession = (NSURLSession *)result;
    [asSession setSessionDescription:@"Blah"];
    XCTAssertNil(asSession.configuration);
}

@end
