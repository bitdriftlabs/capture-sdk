// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

#pragma once

#import <Foundation/Foundation.h>

NS_ASSUME_NONNULL_BEGIN

@interface BitdriftKSCrashWrapper: NSObject

/**
 * Configure the crash reporter.
 * @param basePath The base path to store KSCrash reports under (directory).
 * @return true if the crash handler was successfully initialized.
 */
+ (bool)configureWithBasePath:(NSURL *)basePath;

+ (bool)startCrashReporter;
+ (void)stopCrashReporter;

/**
 * Enhance a MetricKit report with data gleaned from a matching KSCrash report:
 * - Adds a "name" field to any threads that had names or dispatch queue names associated with them.
 *
 * If no KSCrash report is found, or if the KSCrash report doesn't match the MetricKit report,
 * this function returns the original MetricKit report.
 *
 * @param metricKitReport The result of MXDiagnostic.dictionaryRepresentation
 * @return The enhanced report (or the original metricKitReport if something went wrong).
 */
+ (NSDictionary<NSString *, id> *)enhancedMetricKitReport:(NSDictionary<NSString *, id> *)metricKitReport;

@end

NS_ASSUME_NONNULL_END
