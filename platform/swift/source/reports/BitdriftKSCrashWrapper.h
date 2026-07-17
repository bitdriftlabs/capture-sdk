// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

#pragma once

#import <Foundation/Foundation.h>

NS_ASSUME_NONNULL_BEGIN

@protocol KSCrashHandling <NSObject>
- (BOOL)configureWithCrashReportDirectory:(NSURL *)crashReportDir error:(NSError **)error;
- (BOOL)startCrashReporterWithError:(NSError **)error;
- (void)stopCrashReporter;
- (NSNumber *_Nullable)didCrashLastLaunch;
- (NSDate *_Nullable)cachedCrashDate;
- (NSDictionary<NSString *, id> *)enhancedMetricKitReport:(NSDictionary<NSString *, id> *)metricKitReport
                                      useStackOverlapMatching:(BOOL)useStackOverlapMatching
                                                   summaryOut:(NSDictionary<NSString *, NSString *> * _Nullable * _Nullable)summaryOut;
@end

@interface BitdriftKSCrashWrapper: NSObject <KSCrashHandling>

// MARK: - Instance methods (conforms to KSCrashHandling)

- (BOOL)configureWithCrashReportDirectory:(NSURL *)crashReportDir error:(NSError **)error;
- (BOOL)startCrashReporterWithError:(NSError **)error;
- (void)stopCrashReporter;
- (NSNumber *_Nullable)didCrashLastLaunch;
- (NSDate *_Nullable)cachedCrashDate;
- (NSDictionary<NSString *, id> *)enhancedMetricKitReport:(NSDictionary<NSString *, id> *)metricKitReport
                                      useStackOverlapMatching:(BOOL)useStackOverlapMatching
                                                   summaryOut:(NSDictionary<NSString *, NSString *> * _Nullable * _Nullable)summaryOut;

// MARK: - Static methods

/**
 * Configure this class.
 *
 * Note: This method MUST be called before calling any other method in this class.
 *
 * @param crashReportDir The path to the directory where KSCrash will store reports (directory).
 * @param error Filled if NO is returned.
 * @return true if the crash handler was successfully initialized.
 */
+ (BOOL)configureWithCrashReportDirectory:(NSURL *)crashReportDir error:(NSError **)error;

/**
 * Enhance a MetricKit report with data gleaned from a matching KSCrash report:
 * - Adds a "name" field to any threads that had names or dispatch queue names associated with them.
 *
 * If no KSCrash report is found, or if the KSCrash report doesn't match the MetricKit report,
 * this function returns the original MetricKit report.
 *
 * @param metricKitReport The result of MXDiagnostic.dictionaryRepresentation
 * @param useStackOverlapMatching Whether to use the overlap-based thread matcher (finds the best contiguous matching
 *        region from the stack base) instead of the exact matcher
 * @return The enhanced report (or the original metricKitReport if something went wrong).
 */
 + (NSDictionary<NSString *, id> *)enhancedMetricKitReport:(NSDictionary<NSString *, id> *)metricKitReport
                                       useStackOverlapMatching:(BOOL)useStackOverlapMatching
                                                summaryOut:(NSDictionary<NSString *, NSString *> * _Nullable * _Nullable)summaryOut;

/**
 * Start the in-process crash reporter, which captures supplemental
 * information that we can tack on to the MetricKit report.
 *
 * @param error Filled if NO is returned.
 * @return true on success.
 */
+ (BOOL)startCrashReporterWithError:(NSError **)error;

/**
 * Returns whether KSCrash detected a crash on the previous app launch.
 *
 * Returns `nil` when the crash reporter has not been configured yet.
 */
+ (NSNumber *_Nullable)didCrashLastLaunch;

/**
 * Returns the date of the last cached crash captured via KSCrash.
 *
 * @return date in case there's one in the report; if there's no crash report or information is missing,
 * it'll return `nil`
 */
+ (NSDate * _Nullable)cachedCrashDate;

+ (void)stopCrashReporter;

@end

NS_ASSUME_NONNULL_END
