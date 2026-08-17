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

@interface DiagnosticReportWriter : NSObject

- (instancetype)initWithOutputDir:(NSURL *)outputDir
                        sdkVersion:(NSString *)sdkVersion
      fileSizeOptimizationEnabled:(BOOL)fileSizeOptimizationEnabled
               memoryPressureLevel:(CAPMemoryPressureLevel)memoryPressureLevel
                    appEnvironment:(CAPAppEnvironment)appEnvironment
                    teamIdentifier:(nullable NSString *)teamIdentifier
                       fileManager:(NSFileManager *)fileManager;

- (void)writeCrashReportWithType:(ReportType)reportType
                             dict:(NSDictionary *)crashDict
                             name:(nullable NSString *)name
                           reason:(nullable NSString *)reason
                machExceptionType:(nullable NSNumber *)machExceptionType
                    exceptionCode:(nullable NSNumber *)exceptionCode
                           signal:(nullable NSNumber *)signal
                terminationReason:(nullable NSString *)terminationReason
                    capturedCrash:(nullable BitdriftPreviousCrash *)capturedCrash
                         metadata:(NSDictionary *)metadata
               applicationVersion:(NSString *)applicationVersion
                        timestamp:(NSTimeInterval)timestamp;

- (void)writeNonCrashReportWithType:(ReportType)reportType
                                dict:(NSDictionary *)dict
                                name:(nullable NSString *)name
                              reason:(nullable NSString *)reason
                               order:(FrameOrder)order
                            metadata:(NSDictionary *)metadata
                  applicationVersion:(NSString *)applicationVersion
                           timestamp:(NSTimeInterval)timestamp;

@end

NS_ASSUME_NONNULL_END
