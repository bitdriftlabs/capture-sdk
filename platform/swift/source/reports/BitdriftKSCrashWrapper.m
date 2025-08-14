// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

//#define BITDRIFT_OMIT_KSCRASH 1

#import "BitdriftKSCrashWrapper.h"

#ifndef BITDRIFT_OMIT_KSCRASH
#import "../crash_handler//BitdriftKSCrashHandler.h"
#endif

@implementation BitdriftKSCrashWrapper

+ (bool)configureWithCrashReportFilePath:(NSURL *)basePath {
#ifndef BITDRIFT_OMIT_KSCRASH
    return [BitdriftKSCrashHandler configureWithCrashReportPath:basePath];
#else
    return true;
#endif
}

+ (NSDictionary<NSString *, id> *)enhancedMetricKitReport:(NSDictionary<NSString *, id> *)metricKitReport {
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

@end
