//
//  BitdriftKSCrashWrapper.m
//  Capture
//
//  Created by Karl Stenerud on 22.07.25.
//

//#define BITDRIFT_OMIT_KSCRASH 1

#import "BitdriftKSCrashWrapper.h"

#ifndef BITDRIFT_OMIT_KSCRASH
#import "../kscrash/BitdriftKSCrashHandler.h"
#endif

@implementation BitdriftKSCrashWrapper

+ (bool)configureWithBasePath:(NSURL *)basePath {
#ifndef BITDRIFT_OMIT_KSCRASH
    return [BitdriftKSCrashHandler configureWithBasePath:basePath];
#else
    return true;
#endif
}

+ (NSDictionary *)enhancedMetricKitReport:(NSDictionary *)metricKitReport {
#ifndef BITDRIFT_OMIT_KSCRASH
    return [BitdriftKSCrashHandler enhancedMetricKitReport:metricKitReport];
#else
    return metricKitReport;
#endif
}

+ (bool)startCrashReporter {
#ifndef BITDRIFT_OMIT_KSCRASH
    return [BitdriftKSCrashHandler startCrashReporter];
#else
    return true;
#endif
}

+ (void)stopCrashReporter {
#ifndef BITDRIFT_OMIT_KSCRASH
    [BitdriftKSCrashHandler stopCrashReporter];
#endif
}

@end
