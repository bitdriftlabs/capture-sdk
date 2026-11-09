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

typedef struct {
  BDCPURegister *regs;
  uintptr_t count;
} BDCPURegisterStorage;

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

/// Shared parsing helpers for MetricKit crash/diagnostic data that will help to both v1 and v2
/// payloads of MetricKit
@interface MetricKitDiagnosticParsing : NSObject

/// Returns the filename component used for a given report type.
///
/// - parameter reportType: The report type.
///
/// - returns: The filename component
- (NSString *)nameForReportType:(ReportType)reportType;

/// Returns the actual name for a Mach exception type.
///
/// - parameter exceptionType: The Mach exception type code
///
/// - returns: The exception type's name, or nil if `exceptionType` doesn't match a known
///   constant.
- (nullable NSString *)nameForExceptionType:(int32_t)exceptionType;

/// Returns the name for a POSIX signal.
///
/// - parameter signal: The POSIX signal code.
///
/// - returns: The signal's constant name, or nil if `signal` doesn't match a known constant.
- (nullable NSString *)nameForSignal:(int32_t)signal;

/// Generates a name based on the exception type, and uses the signal in case the exception type
/// is not enough.
///
/// - parameter exceptionType: The Mach exception type
/// - parameter signal: The POSIX signal, used as a fallback.
///
/// - returns: The exception type's name, or the signal's name, or nil if neither is recognized.
- (nullable NSString *)nameForExceptionType:(int32_t)exceptionType signal:(int32_t)signal;

/// Builds the crash reason from the sources are available.
///
/// - parameter name: The crash's display name.
/// - parameter exceptionReasonName: MetricKit's exception reason name, if available.
/// - parameter exceptionReasonComposedMessage: MetricKit's composed exception message, if
///   available.
/// - parameter capturedCrashName: The name of a captured in-process `NSException`, if one was
///   recorded.
/// - parameter capturedCrashReason: The reason of a captured in-process `NSException`, if one was
///   recorded.
/// - parameter terminationReason: The OS-provided termination reason string, if any.
/// - parameter vmRegionInfo: The OS-provided virtual memory region info string, if any.
/// - parameter exceptionCode: The exception code (fallback).
/// - parameter signal: The POSIX signal (fallback).
///
/// - returns: The composed reason string, or nil if there's nothing to report beyond `name` itself.
- (nullable NSString *)reasonForCrashWithName:(nullable NSString *)name
                          metricKitReasonName:(nullable NSString *)exceptionReasonName
               metricKitReasonComposedMessage:(nullable NSString *)exceptionReasonComposedMessage
                            capturedCrashName:(nullable NSString *)capturedCrashName
                          capturedCrashReason:(nullable NSString *)capturedCrashReason
                            terminationReason:(nullable NSString *)terminationReason
                      virtualMemoryRegionInfo:(nullable NSString *)vmRegionInfo
                                exceptionCode:(nullable NSNumber *)exceptionCode
                                       signal:(int32_t)signal;

/// Returns whether a crash termination is a watchdog kill due to a hang or not.
///
/// - parameter exceptionType: The Mach exception type.
/// - parameter signal: The POSIX signal.
/// - parameter terminationReason: MetricKit's termination reason string, if any.
/// - parameter exceptionCode: The exception code.
///
/// - returns: Whether this termination was due to a hang or not.
- (BOOL)isWatchdogHangTerminationWithExceptionType:(nullable NSNumber *)exceptionType
                                            signal:(nullable NSNumber *)signal
                                 terminationReason:(nullable NSString *)terminationReason
                                     exceptionCode:(nullable NSNumber *)exceptionCode;

/// Parses a termination reason string (as reported by MetricKit for `SIGKILL` terminations) into
/// its component fields.
///
/// - parameter terminationReason: The termination reason string to parse.
///
/// - returns: A dictionary containing whichever of `domain`, `code`, `explanation`,
///   `process_visibility`, `process_state`, `watchdog_event`, `watchdog_visibility` are present in
///   `terminationReason`. Empty if `terminationReason` is nil or empty.
- (NSDictionary<NSString *, NSString *> *)parseTerminationContext:
    (nullable NSString *)terminationReason;

/// Splits a Unix timestamp into whole seconds and the remaining nanoseconds.
///
/// - parameter timestamp: The Unix timestamp to split.
/// - parameter seconds: Out-parameter set to the whole-second component of `timestamp`.
/// - parameter nanos: Out-parameter set to the remaining component of `timestamp`, in nanoseconds.
- (void)timestampComponentsFor:(NSTimeInterval)timestamp
                       seconds:(uint64_t *)seconds
                         nanos:(uint32_t *)nanos;

/// Maps a platform architecture string to the raw value the report writer expects.
///
/// - parameter platformArchitecture: The platform architecture string (e.g. `"arm64e"`).
///
/// - returns: The corresponding `Architecture` raw value, or `Architecture.Unknown` (0) for a nil
///   or unrecognized value.
- (int8_t)architectureConstantFor:(nullable NSString *)platformArchitecture;

@end

NS_ASSUME_NONNULL_END
