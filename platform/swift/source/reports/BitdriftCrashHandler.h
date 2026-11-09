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
    /// No previous-launch crash is cached by the bitdrift crash reporter.
    BitdriftPreviousCrashKindNone = 0,
    /// The previous launch ended in an uncaught `NSException`. See `BitdriftPreviousCrash.nsexception`.
    BitdriftPreviousCrashKindNSException = 1,
};

/// An uncaught `NSException` captured by the bitdrift crash reporter during the previous launch.
@interface BitdriftNSExceptionCrash: NSObject

@property(nonatomic, readonly, copy) NSString *name;
@property(nonatomic, readonly, copy, nullable) NSString *reason;
/// The exception's call stack, ordered innermost frame (where the exception was raised) first,
/// matching `NSException.callStackReturnAddresses` ordering. Each element is a
/// `BitdriftCrashStackFrame`.
@property(nonatomic, readonly, copy) NSArray *frames;

@end

/// A single call stack frame captured for an `NSException` crash. Frames are identified only by
/// their owning image (`binaryName`/`imageID`) plus `imageLoadAddress` for server-side
/// symbolication.
@interface BitdriftCrashStackFrame: NSObject

@property(nonatomic, readonly) uint64_t frameAddress;
/// The load address of the image containing `frameAddress`, as reported by `dladdr`.
@property(nonatomic, readonly) uint64_t imageLoadAddress;
/// The file name (last path component) of the image containing `frameAddress`, e.g. `Foundation`.
@property(nonatomic, readonly, copy, nullable) NSString *binaryName;
/// The build UUID of the image containing `frameAddress`, used to match it against a dSYM.
@property(nonatomic, readonly, copy, nullable) NSString *imageID;

@end

/// The most recent crash captured by an active crash reporter, cached across the previous launch.
@interface BitdriftPreviousCrash: NSObject

@property(nonatomic, readonly) BitdriftPreviousCrashKind kind;
@property(nonatomic, readonly, strong) NSDate *crashDate;
/// Populated when `kind` is `BitdriftPreviousCrashKindNSException`; nil otherwise.
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

/// Instance API conforms to `BitdriftCrashHandling` for use via `CrashReporterService`; the
/// static API is kept for existing callers that don't go through that protocol. Both delegate to
/// the same underlying `bd-crash-reporter` Rust state, so they observe the same crash data.
@interface BitdriftCrashHandler: NSObject <BitdriftCrashHandling>

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
