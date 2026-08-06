// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

#import "DiagnosticEventReporter.h"
#import "BitdriftCrashHandler.h"
#import "DiagnosticReportWriter.h"
#import "MetricKitDiagnosticParsing.h"

// Name to use for `MXHangDiagnostic` and 0x8badf00d events if no better name is detected
static NSString *const DEFAULT_HANG_NAME = @"App Hang";

// MARK: - DiagnosticEventReporter

@interface DiagnosticEventReporter ()
@property (nonnull, strong) NSURL *dir;
@property (nonnull, strong, nonatomic) NSMeasurement <NSUnitDuration *> *minimumHangDuration;
@property (nullable, strong, nonatomic) void (^completionHandler)();
@property (nullable, copy, nonatomic) CAPCrashEnrichmentSummaryHandler crashEnrichmentSummaryHandler;
@property CAPDiagnosticType diagnosticTypes;
@property BOOL useStackOverlapMatching;
@property (nonnull, strong, nonatomic) id<CrashReporting> crashReporting;
@property (nonnull, strong, nonatomic) MetricKitDiagnosticParsing *parsing;
@property (nonnull, strong, nonatomic) DiagnosticReportWriter *writer;
@end

@implementation DiagnosticEventReporter

- (instancetype _Nonnull)initWithOutputDir:(NSURL *_Nonnull)dir
                                sdkVersion:(NSString *_Nonnull)sdkVersion
                                eventTypes:(CAPDiagnosticType)types
                        minimumHangSeconds:(NSTimeInterval)seconds
                       memoryPressureLevel:(CAPMemoryPressureLevel)memoryPressureLevel
               fileSizeOptimizationEnabled:(BOOL)fileSizeOptimizationEnabled
                   useStackOverlapMatching:(BOOL)useStackOverlapMatching
                            crashReporting:(id<CrashReporting> _Nonnull)crashReporting
             crashEnrichmentSummaryHandler:(CAPCrashEnrichmentSummaryHandler _Nullable)crashEnrichmentSummaryHandler
                         completionHandler:(void (^_Nullable)())completionHandler {
  if (self = [super init]) {
    self.dir = dir;
    self.diagnosticTypes = types;
    self.completionHandler = completionHandler;
    self.useStackOverlapMatching = useStackOverlapMatching;
    self.crashReporting = crashReporting;
    self.crashEnrichmentSummaryHandler = crashEnrichmentSummaryHandler;
    self.parsing = [MetricKitDiagnosticParsing new];
    self.writer = [[DiagnosticReportWriter alloc] initWithOutputDir:dir
                                                           sdkVersion:sdkVersion
                                         fileSizeOptimizationEnabled:fileSizeOptimizationEnabled
                                                  memoryPressureLevel:memoryPressureLevel
                                                          fileManager:[NSFileManager defaultManager]];
    [self setMinimumHangSeconds:seconds];
  }
  return self;
}

- (void)setMinimumHangSeconds:(NSTimeInterval)seconds {
  self.minimumHangDuration = [[NSMeasurement alloc] initWithDoubleValue:seconds
                                                                   unit:[NSUnitDuration baseUnit]];
}

- (void)didReceiveDiagnosticPayloads:(NSArray<MXDiagnosticPayload *> * _Nonnull)payloads API_AVAILABLE(ios(14.0), macos(12.0)) {
  NSFileManager *fileManager = [NSFileManager defaultManager];
  if (![fileManager createDirectoryAtPath:[self.dir path] withIntermediateDirectories:YES attributes:0 error:nil]) {
    return;
  }

  for (MXDiagnosticPayload *payload in payloads) {
    NSTimeInterval timestamp = [payload.timeStampEnd timeIntervalSince1970];
    if ((self.diagnosticTypes & CAPDiagnosticTypeCrash) > 0) {
      BitdriftPreviousCrash *capturedCrash = [self.crashReporting cachedPreviousCrash];
      NSDate *crashDate = [self.crashReporting cachedCrashDate];
      for (MXCrashDiagnostic *event in payload.crashDiagnostics) {
        NSTimeInterval eventTimestamp = crashDate ? crashDate.timeIntervalSince1970 : timestamp;
        [self processDiagnostic:event atTimestamp:eventTimestamp capturedCrash:capturedCrash];
      }
    }

    if ((self.diagnosticTypes & CAPDiagnosticTypeHang) > 0) {
      for (MXHangDiagnostic *event in payload.hangDiagnostics) {
        NSMeasurement *duration = ((MXHangDiagnostic *)event).hangDuration;
        if ([duration measurementBySubtractingMeasurement:self.minimumHangDuration].doubleValue < 0) {
          continue;
        }
        [self processDiagnostic:event atTimestamp:timestamp];
      }
    }
  }
  if (self.completionHandler) {
    self.completionHandler();
  }
}

- (void)processDiagnostic:(MXDiagnostic *)event atTimestamp:(NSTimeInterval)timestamp {
  [self processDiagnostic:event atTimestamp:timestamp capturedCrash:nil];
}

- (void)processDiagnostic:(MXDiagnostic *)event
              atTimestamp:(NSTimeInterval)timestamp
            capturedCrash:(BitdriftPreviousCrash *)capturedCrash API_AVAILABLE(ios(14.0), macos(12.0)) {
  NSDictionary *metadata = event.metaData.dictionaryRepresentation;

  if ([event isKindOfClass:[MXCrashDiagnostic class]]) {
    MXCrashDiagnostic *mxCrash = (MXCrashDiagnostic *)event;
    // Watchdog terminations arrive as MXCrashDiagnostic but should be reported as ANRs.
    BOOL is_hang = [self crashIsHangTermination:mxCrash];
    capturedCrash = is_hang ? nil : capturedCrash;
    ReportType report_type = is_hang ? ReportTypeAppNotResponding : ReportTypeNativeCrash;
    NSString *name = is_hang ? DEFAULT_HANG_NAME : [self nameForCrash:mxCrash];
    NSString *reason = [self reasonForCrash:mxCrash name:name capturedCrash:capturedCrash];
    NSDictionary<NSString *, NSString *> *summary = nil;
    NSDictionary *dictReport = [self.crashReporting enhancedMetricKitReport:event.dictionaryRepresentation
                                                        useStackOverlapMatching:self.useStackOverlapMatching
                                                                     summaryOut:&summary];
    if (summary != nil && self.crashEnrichmentSummaryHandler != nil) {
      self.crashEnrichmentSummaryHandler(summary);
    }
    [self.writer writeCrashReportWithType:report_type
                                       dict:dictReport
                                       name:name
                                     reason:reason
                          machExceptionType:mxCrash.exceptionType
                              exceptionCode:mxCrash.exceptionCode
                                     signal:mxCrash.signal
                          terminationReason:mxCrash.terminationReason
                              capturedCrash:capturedCrash
                                   metadata:metadata
                         applicationVersion:event.applicationVersion
                                  timestamp:timestamp];
  } else if ([event isKindOfClass:[MXHangDiagnostic class]]) {
    MXHangDiagnostic *hang = (MXHangDiagnostic *)event;
    NSMeasurementFormatter *formatter = [NSMeasurementFormatter new];
    NSString *duration = [formatter stringFromMeasurement:hang.hangDuration];
    NSString *reason = [NSString stringWithFormat:@"app was unresponsive for %@", duration];
    NSDictionary *representation = event.dictionaryRepresentation;
    NSString *name = representation[@"hangType"] ?: DEFAULT_HANG_NAME;
    [self.writer writeNonCrashReportWithType:ReportTypeAppNotResponding
                                          dict:representation
                                          name:name
                                        reason:reason
                                         order:FrameOrderOuterToInner
                                      metadata:metadata
                            applicationVersion:event.applicationVersion
                                     timestamp:timestamp];
  }
}

- (BOOL)crashIsHangTermination:(MXCrashDiagnostic *)event API_AVAILABLE(ios(14.0), macos(12.0)) {
  return [self.parsing isWatchdogHangTerminationWithExceptionType:event.exceptionType
                                                              signal:event.signal
                                                   terminationReason:event.terminationReason
                                                       exceptionCode:event.exceptionCode];
}

// MARK: - Crash type helpers

- (NSString *)nameForCrash:(MXCrashDiagnostic *)event API_AVAILABLE(ios(14.0), macos(12.0)) {
  return [self.parsing nameForExceptionType:event.exceptionType.intValue signal:event.signal.intValue];
}

- (NSString *)reasonForCrash:(MXCrashDiagnostic *)event
                        name:(NSString *)name
                capturedCrash:(BitdriftPreviousCrash *)capturedCrash API_AVAILABLE(ios(14.0), macos(12.0)) {
  NSString *exceptionReasonName = nil;
  NSString *exceptionReasonComposedMessage = nil;
  if (@available(iOS 17, macOS 14, *)) {
    if (event.exceptionReason) {
      // exception name included here instead of in nameForCrash: to avoid cases where devices on
      // iOS <17 have a different name and thus a different issue grouping
      exceptionReasonName = event.exceptionReason.exceptionName;
      exceptionReasonComposedMessage = event.exceptionReason.composedMessage;
    }
  }

  NSString *capturedCrashName = nil;
  NSString *capturedCrashReason = nil;
  if (capturedCrash.kind == BitdriftPreviousCrashKindNSException
      && capturedCrash.nsexception.name != nil
      && capturedCrash.nsexception.reason != nil) {
    capturedCrashName = capturedCrash.nsexception.name;
    capturedCrashReason = capturedCrash.nsexception.reason;
  }

  return [self.parsing reasonForCrashWithName:name
                           metricKitReasonName:exceptionReasonName
                metricKitReasonComposedMessage:exceptionReasonComposedMessage
                             capturedCrashName:capturedCrashName
                           capturedCrashReason:capturedCrashReason
                             terminationReason:event.terminationReason
                       virtualMemoryRegionInfo:event.virtualMemoryRegionInfo
                                 exceptionCode:event.exceptionCode
                                        signal:event.signal.intValue];
}

@end
