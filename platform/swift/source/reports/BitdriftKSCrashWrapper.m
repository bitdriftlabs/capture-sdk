//
//  BitdriftKSCrashWrapper.m
//  Capture
//
//  Created by Karl Stenerud on 22.07.25.
//

#import "BitdriftKSCrashWrapper.h"

// TODO: Temporary
#import "../kscrash/BitdriftKSCrashHandler.h"

@implementation BitdriftKSCrashWrapper

+ (bool)configureWithBasePath:(NSURL *)basePath {
    return [BitdriftKSCrashHandler configureWithBasePath:basePath];
}

+ (NSDictionary *)enhancedMetricKitReport:(NSDictionary *)metricKitReport {
    return [BitdriftKSCrashHandler enhancedMetricKitReport:metricKitReport];
}

+ (bool)start {
    return [BitdriftKSCrashHandler start];
}

+ (void)stop {
    [BitdriftKSCrashHandler stop];
}

@end
