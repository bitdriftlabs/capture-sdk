// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

#import <Foundation/Foundation.h>

#import "DiagnosticEventReporter.h"
#import "MetricKitDiagnosticParsing.h"

@class BitdriftPreviousCrash;

NS_ASSUME_NONNULL_BEGIN

@interface MetricKitReportFrame : NSObject

- (instancetype)initWithAddress:(uint64_t)address
                      binaryUUID:(NSString *)binaryUUID
                      binaryName:(NSString *)binaryName
    offsetIntoBinaryTextSegment:(uint64_t)offsetIntoBinaryTextSegment;

@property (nonatomic, readonly) uint64_t address;
@property (nonatomic, readonly, copy) NSString *binaryUUID;
@property (nonatomic, readonly, copy) NSString *binaryName;
@property (nonatomic, readonly) uint64_t offsetIntoBinaryTextSegment;

@end

@interface MetricKitReportThread : NSObject

- (instancetype)initWithName:(nullable NSString *)name
                   attributed:(BOOL)attributed
                       frames:(NSArray<MetricKitReportFrame *> *)frames;

@property (nonatomic, readonly, nullable, copy) NSString *name;
@property (nonatomic, readonly) BOOL attributed;
@property (nonatomic, readonly, copy) NSArray<MetricKitReportFrame *> *frames;

@end

@interface MetricKitReportEnvironment : NSObject

- (instancetype)initWithBundleIdentifier:(NSString *)bundleIdentifier
                       applicationVersion:(NSString *)applicationVersion
                  applicationBuildVersion:(NSString *)applicationBuildVersion
                             regionFormat:(NSString *)regionFormat
                               deviceType:(NSString *)deviceType
                            osVersionName:(NSString *)osVersionName
                          osVersionNumber:(NSString *)osVersionNumber
                     osVersionBuildNumber:(NSString *)osVersionBuildNumber
                     platformArchitecture:(NSString *)platformArchitecture
                      lowPowerModeEnabled:(BOOL)lowPowerModeEnabled;

@property (nonatomic, readonly, copy) NSString *bundleIdentifier;
@property (nonatomic, readonly, copy) NSString *applicationVersion;
@property (nonatomic, readonly, copy) NSString *applicationBuildVersion;
@property (nonatomic, readonly, copy) NSString *regionFormat;
@property (nonatomic, readonly, copy) NSString *deviceType;
@property (nonatomic, readonly, copy) NSString *osVersionName;
@property (nonatomic, readonly, copy) NSString *osVersionNumber;
@property (nonatomic, readonly, copy) NSString *osVersionBuildNumber;
@property (nonatomic, readonly, copy) NSString *platformArchitecture;
@property (nonatomic, readonly) BOOL lowPowerModeEnabled;

@end

/// Owns everything downstream of "here are the normalized threads/name/reason for this event":
/// the `bdrw_*` report-writer FFI calls, the C string/buffer lifetime management they require, and
/// writing the finished report to disk. Consumed by `MetricKitDiagnosticManager` (iOS 27+, native
/// `CallStackTree`-derived data); not yet wired into `DiagnosticEventReporter` (`NSDictionary`-derived
/// data, iOS <27), though nothing here is tied to the newer payload shape.
@interface MetricKitReportWriter : NSObject

- (instancetype)initWithOutputDir:(NSURL *)outputDir
                        sdkVersion:(NSString *)sdkVersion
      fileSizeOptimizationEnabled:(BOOL)fileSizeOptimizationEnabled
               memoryPressureLevel:(CAPMemoryPressureLevel)memoryPressureLevel
                       fileManager:(NSFileManager *)fileManager;

/// Writes a crash report: the threads/error, `BDAppleCrashInfo` (from `diagnostic` and, if present,
/// `capturedCrash`), and app/device metrics, then flushes it to `outputDir`.
- (void)writeCrashReportWithType:(ReportType)reportType
                          threads:(NSArray<MetricKitReportThread *> *)threads
                             name:(nullable NSString *)name
                           reason:(nullable NSString *)reason
                machExceptionType:(nullable NSNumber *)machExceptionType
                    exceptionCode:(nullable NSNumber *)exceptionCode
                           signal:(nullable NSNumber *)signal
                terminationReason:(nullable NSString *)terminationReason
                    capturedCrash:(nullable BitdriftPreviousCrash *)capturedCrash
                      environment:(MetricKitReportEnvironment *)environment
                        timestamp:(NSTimeInterval)timestamp;

/// Writes a memory-exception report: the threads/error and app/device metrics, then flushes it to
/// `outputDir`. There's no `BDAppleCrashInfo`/`capturedCrash` correlation here since memory
/// exceptions have no in-process crash reporter counterpart to cross-reference against.
- (void)writeMemoryExceptionReportWithThreads:(NSArray<MetricKitReportThread *> *)threads
                                          name:(NSString *)name
                                        reason:(NSString *)reason
                                   environment:(MetricKitReportEnvironment *)environment
                                     timestamp:(NSTimeInterval)timestamp;

@end

NS_ASSUME_NONNULL_END
