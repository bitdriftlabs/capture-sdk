// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

#import <MetricKit/MetricKit.h>

@class BitdriftPreviousCrash;

/// Abstracts the crash handler layer (KSCrash + BitdriftCrash) so that `DiagnosticEventReporter`
/// can enrich MetricKit reports without coupling to either handler directly.
/// `CrashReporterService` is the canonical conformer in production.
///
/// Adding, replacing, or removing a crash reporter (e.g. dropping KSCrash) requires changes
/// only in `CrashReporterService`; consumers like `DiagnosticEventReporter` and `Logger`
/// remain unaffected.
@protocol CrashReporting <NSObject>
/// Returns the date of the most recent crash captured by KSCrash, or nil if none is available.
- (NSDate *_Nullable)cachedCrashDate;
/// Returns structured data about the previous crash (kind, NSException info, date), or nil if none.
- (BitdriftPreviousCrash *_Nullable)cachedPreviousCrash;
/// Enriches a raw MetricKit report with supplemental data from a matching KSCrash report
/// (e.g. thread names). Returns the original report unchanged if no matching report is found.
- (NSDictionary<NSString *, id> *_Nonnull)enhancedMetricKitReport:(NSDictionary<NSString *, id> *_Nonnull)metricKitReport
                                      useStackOverlapMatching:(BOOL)useStackOverlapMatching
                                                   summaryOut:(NSDictionary<NSString *, NSString *> *_Nullable *_Nullable)summaryOut;
@end

typedef NS_ENUM(int8_t, CAPMemoryPressureLevel) {
    CAPMemoryPressureLevelUnknown = 0,
    CAPMemoryPressureLevelNormal = 1,
    CAPMemoryPressureLevelWarning = 2,
    CAPMemoryPressureLevelCritical = 3,
} NS_SWIFT_NAME(MemoryPressureLevel);

typedef NS_OPTIONS(NSUInteger, CAPDiagnosticType) {
  CAPDiagnosticTypeNone = 0,
  /** Application termination events */
  CAPDiagnosticTypeCrash = 1 << 0,
  /** Non-fatal app hangs */
  CAPDiagnosticTypeHang = 1 << 1,
  /** Non-fatal disk write exceptions */
  CAPDiagnosticTypeDiskWrite = 1 << 2,
  /** Non-fatal CPU usage exceptions */
  CAPDiagnosticTypeCPUException = 1 << 3,
};

typedef void (^CAPCrashEnrichmentSummaryHandler)(
    NSDictionary<NSString *, NSString *> *_Nullable summary);

@interface DiagnosticEventReporter : NSObject <MXMetricManagerSubscriber>
/**
 * Create a new reporter, including the write directory for any detected reports and library
 * metadata to include in the generated files
 *
 * @param path       destination directory for generated reports
 * @param sdkVersion current version of the Capture SDK
 * @param types      event types to report
 * @param seconds    number of seconds required to report `CAPDiagnosticTypeHang` events
 * @param memoryPressureLevel previous run's memory pressure level (CAPMemoryPressureLevel)
 * @param fileSizeOptimizationEnabled whether `client_feature.ios.optimize_fatal_issue_report_size`
 *                                    is enabled for generated fatal issue reports
 * @param useStackOverlapMatching whether to use the overlap-based thread matcher (finds the best
 * contiguous matching region from the stack base) instead of the exact matcher for crash enrichment
 * @param crashEnrichmentSummaryHandler block invoked after crash enrichment with the summary fields
 * to log
 * @param completion block to invoke when report processing is completed
 */
- (instancetype _Nonnull)initWithOutputDir:(NSURL *_Nonnull)path
                                sdkVersion:(NSString *_Nonnull)sdkVersion
                                eventTypes:(CAPDiagnosticType)types
                        minimumHangSeconds:(NSTimeInterval)seconds
                       memoryPressureLevel:(CAPMemoryPressureLevel)memoryPressureLevel
               fileSizeOptimizationEnabled:(BOOL)fileSizeOptimizationEnabled
                   useStackOverlapMatching:(BOOL)useStackOverlapMatching
                            crashReporting:(id<CrashReporting> _Nonnull)crashReporting
             crashEnrichmentSummaryHandler:(CAPCrashEnrichmentSummaryHandler _Nullable)crashEnrichmentSummaryHandler
                         completionHandler:(void (^_Nullable)())completion;

- (void)setMinimumHangSeconds:(NSTimeInterval)seconds;
@end
