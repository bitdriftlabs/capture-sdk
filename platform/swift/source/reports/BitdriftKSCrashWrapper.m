// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

#import "BitdriftKSCrashWrapper.h"
#import "../crash_handler/BitdriftKSCrashHandler.h"

@implementation BitdriftKSCrashWrapper

// MARK: - Instance methods

- (BOOL)configureWithCrashReportDirectory:(NSURL *)crashReportDir error:(NSError **)error {
    return [BitdriftKSCrashHandler configureWithCrashReportDirectory:crashReportDir error:error];
}

- (BOOL)startCrashReporterWithError:(NSError **)error {
    return [BitdriftKSCrashHandler startCrashReporterWithError:error];
}

- (void)stopCrashReporter {
    [BitdriftKSCrashHandler stopCrashReporter];
}

- (NSNumber *_Nullable)didCrashLastLaunch {
    return [BitdriftKSCrashHandler didCrashLastLaunch];
}

- (NSDate *_Nullable)cachedCrashDate {
    return [BitdriftKSCrashHandler cachedCrashDate];
}

- (NSDictionary<NSString *, id> *)enhancedMetricKitReport:(NSDictionary<NSString *, id> *)metricKitReport
                                      useStackOverlapMatching:(BOOL)useStackOverlapMatching
                                                   summaryOut:(NSDictionary<NSString *, NSString *> * _Nullable * _Nullable)summaryOut {
    return [BitdriftKSCrashHandler enhancedMetricKitReport:metricKitReport
                                    useStackOverlapMatching:useStackOverlapMatching
                                                summaryOut:summaryOut];
}

// MARK: - Static methods

+ (BOOL)configureWithCrashReportDirectory:(NSURL *)basePath error:(NSError **)error {
    return [BitdriftKSCrashHandler configureWithCrashReportDirectory:basePath error:error];
}

+ (NSDictionary<NSString *, id> *)enhancedMetricKitReport:(NSDictionary<NSString *, id> *)metricKitReport
                                      useStackOverlapMatching:(BOOL)useStackOverlapMatching
                                                   summaryOut:(NSDictionary<NSString *, NSString *> * _Nullable * _Nullable)summaryOut {
    return [BitdriftKSCrashHandler enhancedMetricKitReport:metricKitReport
                                    useStackOverlapMatching:useStackOverlapMatching
                                                summaryOut:summaryOut];
}

+ (BOOL)startCrashReporterWithError:(NSError **)error {
    return [BitdriftKSCrashHandler startCrashReporterWithError:error];
}

+ (NSNumber *_Nullable)didCrashLastLaunch {
    return [BitdriftKSCrashHandler didCrashLastLaunch];
}

+ (NSDate * _Nullable)cachedCrashDate {
    return [BitdriftKSCrashHandler cachedCrashDate];
}

+ (void)stopCrashReporter {
    [BitdriftKSCrashHandler stopCrashReporter];
}

@end
