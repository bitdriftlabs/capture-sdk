// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

#import <Foundation/Foundation.h>

#import "bd-report-writer/ffi.h"

NS_ASSUME_NONNULL_BEGIN

static inline const char *cstring_from(NSString *_Nullable str) {
  return [str cStringUsingEncoding:NSUTF8StringEncoding];
}

typedef struct {
  BDCrashInfoThreadDetails details;
  BDCrashInfoThread *threads;
} BDCrashInfoThreadDetailsStorage;

static inline BDCrashInfoThreadDetailsStorage empty_crash_info_thread_details_storage(void) {
  return (BDCrashInfoThreadDetailsStorage){0};
}

/// SDK identifier used in generated report files.
static const char *const SDK_ID = "io.bitdrift.capture-apple";

typedef NS_ENUM(int8_t, ReportType) {
  ReportTypeNone = 0,
  ReportTypeAppNotResponding = 1,
  ReportTypeNativeCrash = 5,
};

typedef NS_ENUM(int8_t, CrashReporterScopeValue) {
  CrashReporterScopeValueUnknown = 0,
  CrashReporterScopeValueInProcess = 1,
  CrashReporterScopeValueOutOfProcess = 2,
};

typedef NS_ENUM(int8_t, CrashReporterValue) {
  CrashReporterValueUnknown = 0,
  CrashReporterValueAppleMetricKit = 1,
  CrashReporterValueAppleKSCrash = 2,
  CrashReporterValueAppleBitdriftCrashReporter = 3,
};

typedef NS_ENUM(NSUInteger, FrameOrder) {
  /// The root frame is the "top"/outermost frame of the call stack tree.
  FrameOrderOuterToInner,
  /// The root frame is the "bottom"/innermost frame of the call stack tree.
  FrameOrderInnerToOuter,
};

/// Pure, payload-shape-agnostic parsing helpers shared between `DiagnosticEventReporter` (the
/// `MXMetricManager`/`NSDictionary`-based path, iOS <27) and `MetricKitDiagnosticManager` (the
/// `MetricManager`/native-struct-based path, iOS 27+). None of these take or return
/// `NSDictionary`-shaped MetricKit payloads, only primitives and plain strings, so unlike the
/// enrichment/matching logic, there's no payload-shape reason to duplicate them.
@interface MetricKitDiagnosticParsing : NSObject

/// Returns the file-name component used for a given `reportType` (e.g. `"crash"`, `"anr"`).
- (NSString *)nameForReportType:(ReportType)reportType;

/// Returns the C constant name for a Mach exception type (e.g. `EXC_BAD_ACCESS`), or nil if
/// `exceptionType` doesn't match a known constant.
- (nullable NSString *)nameForExceptionType:(int32_t)exceptionType;

/// Returns the C constant name for a POSIX signal (e.g. `SIGSEGV`), or nil if `signal` doesn't
/// match a known constant.
- (nullable NSString *)nameForSignal:(int32_t)signal;

/// Parses a `terminationReason` string (as reported by MetricKit for `SIGKILL` terminations) into
/// its component fields: `domain`, `code`, `explanation`, `process_visibility`, `process_state`,
/// `watchdog_event`, `watchdog_visibility`. Any field not present in `terminationReason` is
/// omitted from the result. Returns an empty dictionary for a nil or empty `terminationReason`.
- (NSDictionary<NSString *, NSString *> *)parseTerminationContext:(nullable NSString *)terminationReason;

/// Splits a Unix timestamp into whole seconds and the remaining nanoseconds.
- (void)timestampComponentsFor:(NSTimeInterval)timestamp
                        seconds:(uint64_t *)seconds
                          nanos:(uint32_t *)nanos;

/// Maps a platform architecture string (e.g. `"arm64e"`) to the `Architecture` enum raw value the
/// report writer expects. Returns `Architecture.Unknown` (0) for a nil or unrecognized value.
- (int8_t)architectureConstantFor:(nullable NSString *)platformArchitecture;

@end

NS_ASSUME_NONNULL_END
