// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

#pragma once

#import <Foundation/Foundation.h>

NS_ASSUME_NONNULL_BEGIN

typedef NS_ENUM(NSUInteger, BitdriftPreviousCrashKind) {
    BitdriftPreviousCrashKindNone = 0,
    BitdriftPreviousCrashKindNSException = 1,
};

@interface BitdriftNSExceptionCrash: NSObject

@property(nonatomic, readonly, copy) NSString *name;
@property(nonatomic, readonly, copy, nullable) NSString *reason;

@end

@interface BitdriftPreviousCrash: NSObject

@property(nonatomic, readonly) BitdriftPreviousCrashKind kind;
@property(nonatomic, readonly, strong) NSDate *crashDate;
@property(nonatomic, readonly, strong, nullable) BitdriftNSExceptionCrash *nsexception;

@end

@protocol BitdriftCrashHandling <NSObject>
- (BOOL)configureWithCrashReportDirectory:(NSURL *)crashReportDir error:(NSError **)error;
- (BOOL)startCrashReporterWithError:(NSError **)error;
- (void)stopCrashReporter;
- (NSNumber *_Nullable)didCrashLastLaunch;
- (NSDate *_Nullable)cachedCrashDate;
- (BitdriftPreviousCrash *_Nullable)cachedPreviousCrash;
@end

@interface BitdriftCrashHandler: NSObject <BitdriftCrashHandling>

// MARK: - Instance methods (conforms to BitdriftCrashHandling)

- (BOOL)configureWithCrashReportDirectory:(NSURL *)crashReportDir error:(NSError **)error;
- (BOOL)startCrashReporterWithError:(NSError **)error;
- (void)stopCrashReporter;
- (NSNumber *_Nullable)didCrashLastLaunch;
- (NSDate *_Nullable)cachedCrashDate;
- (BitdriftPreviousCrash *_Nullable)cachedPreviousCrash;

// MARK: - Static methods

+ (BOOL)configureWithCrashReportDirectory:(NSURL *)crashReportDir error:(NSError **)error;
+ (BOOL)startCrashReporterWithError:(NSError **)error;
+ (NSNumber *_Nullable)didCrashLastLaunch;
+ (NSDate * _Nullable)cachedCrashDate;
+ (BitdriftPreviousCrash * _Nullable)cachedPreviousCrash;
+ (NSString * _Nullable)cachedExceptionName;
+ (NSString * _Nullable)cachedExceptionReason;
+ (void)stopCrashReporter;

@end

NS_ASSUME_NONNULL_END
