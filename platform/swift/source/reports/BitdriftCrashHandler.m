// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

#import "BitdriftCrashHandler.h"

bool capture_bitdrift_crash_configure(const char *state_path);
bool capture_bitdrift_crash_start(void);
void capture_bitdrift_crash_stop(void);
int8_t capture_bitdrift_crash_did_crash_last_launch(void);
uint64_t capture_bitdrift_crash_cached_timestamp(void);
uint8_t capture_bitdrift_crash_cached_kind(void);
const char *_Nullable capture_bitdrift_crash_last_exception_name(void);
const char *_Nullable capture_bitdrift_crash_last_exception_reason(void);
uint16_t capture_bitdrift_crash_last_exception_call_stack_frame_count(void);
const uint64_t *_Nullable capture_bitdrift_crash_last_exception_call_stack_return_addresses(void);
uint64_t capture_bitdrift_crash_last_exception_call_stack_image_load_address_at(uint16_t frame_index);
const char *_Nullable capture_bitdrift_crash_last_exception_call_stack_binary_name_at(uint16_t frame_index);
const char *_Nullable capture_bitdrift_crash_last_exception_call_stack_image_id_at(uint16_t frame_index);

@interface BitdriftNSExceptionCrash ()

- (instancetype)initWithName:(NSString *)name
                      reason:(NSString *_Nullable)reason
                      frames:(NSArray *)frames;

@end

@interface BitdriftCrashStackFrame ()

- (instancetype)initWithFrameAddress:(uint64_t)frameAddress
                    imageLoadAddress:(uint64_t)imageLoadAddress
                          binaryName:(NSString *_Nullable)binaryName
                             imageID:(NSString *_Nullable)imageID;

@end

@implementation BitdriftNSExceptionCrash

- (instancetype)initWithName:(NSString *)name
                      reason:(NSString *_Nullable)reason
                      frames:(NSArray *)frames {
    self = [super init];
    if (self != nil) {
        _name = [name copy];
        _reason = [reason copy];
        _frames = [frames copy];
    }
    return self;
}

@end

@implementation BitdriftCrashStackFrame

- (instancetype)initWithFrameAddress:(uint64_t)frameAddress
                    imageLoadAddress:(uint64_t)imageLoadAddress
                          binaryName:(NSString *_Nullable)binaryName
                             imageID:(NSString *_Nullable)imageID {
    self = [super init];
    if (self != nil) {
        _frameAddress = frameAddress;
        _imageLoadAddress = imageLoadAddress;
        _binaryName = [binaryName copy];
        _imageID = [imageID copy];
    }
    return self;
}

@end

@interface BitdriftPreviousCrash ()

- (instancetype)initWithKind:(BitdriftPreviousCrashKind)kind
                   crashDate:(NSDate *)crashDate
                 nsexception:(BitdriftNSExceptionCrash *_Nullable)nsexception;

@end

@implementation BitdriftPreviousCrash

- (instancetype)initWithKind:(BitdriftPreviousCrashKind)kind
                   crashDate:(NSDate *)crashDate
                 nsexception:(BitdriftNSExceptionCrash *_Nullable)nsexception {
    self = [super init];
    if (self != nil) {
        _kind = kind;
        _crashDate = crashDate;
        _nsexception = nsexception;
    }
    return self;
}

@end

@implementation BitdriftCrashHandler

// MARK: - Instance methods

- (BOOL)configureWithCrashReportDirectory:(NSURL *)crashReportDir error:(NSError **)error {
    return [BitdriftCrashHandler configureWithCrashReportDirectory:crashReportDir error:error];
}

- (BOOL)startCrashReporterWithError:(NSError **)error {
    return [BitdriftCrashHandler startCrashReporterWithError:error];
}

- (void)stopCrashReporter {
    capture_bitdrift_crash_stop();
}

- (NSNumber *_Nullable)didCrashLastLaunch {
    return [BitdriftCrashHandler didCrashLastLaunch];
}

- (NSDate *_Nullable)cachedCrashDate {
    return [BitdriftCrashHandler cachedCrashDate];
}

- (BitdriftPreviousCrash *_Nullable)cachedPreviousCrash {
    return [BitdriftCrashHandler cachedPreviousCrash];
}

// MARK: - Static methods

+ (BOOL)configureWithCrashReportDirectory:(NSURL *)crashReportDir error:(NSError **)error {
    if (crashReportDir == nil) {
        if (error != nil) {
            *error = [NSError errorWithDomain:@"BitdriftCrashHandler" code:0 userInfo:@{
                NSLocalizedDescriptionKey: @"Configuration failed",
                NSLocalizedFailureReasonErrorKey: @"crashReportDir is nil",
            }];
        }
        return NO;
    }

    NSString *statePath = [crashReportDir.absoluteURL.path stringByAppendingPathComponent:@"state.bin"];
    NSFileManager *fileManager = NSFileManager.defaultManager;
    if (![fileManager fileExistsAtPath:crashReportDir.absoluteURL.path]) {
        if (![fileManager createDirectoryAtPath:crashReportDir.absoluteURL.path
                    withIntermediateDirectories:YES
                                     attributes:nil
                                          error:error]) {
            return NO;
        }
    }

    BOOL configured = capture_bitdrift_crash_configure(statePath.UTF8String);
    if (!configured && error != nil) {
        *error = [NSError errorWithDomain:@"BitdriftCrashHandler" code:0 userInfo:@{
            NSLocalizedDescriptionKey: @"Configuration failed",
            NSLocalizedFailureReasonErrorKey: [NSString stringWithFormat:@"failed to configure bitdrift crash reporter at %@", statePath],
        }];
    }
    return configured;
}

+ (BOOL)startCrashReporterWithError:(NSError **)error {
    BOOL started = capture_bitdrift_crash_start();
    if (!started && error != nil) {
        *error = [NSError errorWithDomain:@"BitdriftCrashHandler" code:0 userInfo:@{
            NSLocalizedDescriptionKey: @"Start failed",
            NSLocalizedFailureReasonErrorKey: @"failed to install bitdrift crash reporter",
        }];
    }
    return started;
}

+ (NSNumber *_Nullable)didCrashLastLaunch {
    int8_t value = capture_bitdrift_crash_did_crash_last_launch();
    return value < 0 ? nil : @(value != 0);
}

+ (NSDate * _Nullable)cachedCrashDate {
    uint64_t timestamp = capture_bitdrift_crash_cached_timestamp();
    return timestamp == 0 ? nil : [NSDate dateWithTimeIntervalSince1970:(NSTimeInterval)timestamp];
}

// Builds a `BitdriftPreviousCrash` from the cached FFI state written by the Rust crash reporter
// during the previous launch. Returns nil if no crash date is cached, meaning nothing crashed
// (or the crash reporter wasn't configured in time).
+ (BitdriftPreviousCrash * _Nullable)cachedPreviousCrash {
    NSDate *crashDate = [self cachedCrashDate];
    if (crashDate == nil) {
        return nil;
    }

    BitdriftPreviousCrashKind kind = (BitdriftPreviousCrashKind)capture_bitdrift_crash_cached_kind();
    BitdriftNSExceptionCrash *nsexception = nil;
    if (kind == BitdriftPreviousCrashKindNSException) {
        NSString *name = [self cachedExceptionName];
        if (name != nil) {
            NSMutableArray *frames = [NSMutableArray array];
            uint16_t frameCount = capture_bitdrift_crash_last_exception_call_stack_frame_count();
            const uint64_t *returnAddresses = capture_bitdrift_crash_last_exception_call_stack_return_addresses();
            if (returnAddresses != nil) {
                for (uint16_t index = 0; index < frameCount; index++) {
                    const char *binaryName = capture_bitdrift_crash_last_exception_call_stack_binary_name_at(index);
                    const char *imageID = capture_bitdrift_crash_last_exception_call_stack_image_id_at(index);
                    [frames addObject:[[BitdriftCrashStackFrame alloc] initWithFrameAddress:returnAddresses[index]
                                                                         imageLoadAddress:capture_bitdrift_crash_last_exception_call_stack_image_load_address_at(index)
                                                                               binaryName:binaryName == NULL ? nil : [NSString stringWithUTF8String:binaryName]
                                                                                  imageID:imageID == NULL ? nil : [NSString stringWithUTF8String:imageID]]];
                }
            }
            nsexception = [[BitdriftNSExceptionCrash alloc] initWithName:name
                                                                  reason:[self cachedExceptionReason]
                                                                  frames:frames];
        }
    }

    return [[BitdriftPreviousCrash alloc] initWithKind:kind crashDate:crashDate nsexception:nsexception];
}

+ (NSString * _Nullable)cachedExceptionName {
    const char *value = capture_bitdrift_crash_last_exception_name();
    return value == nil ? nil : [NSString stringWithUTF8String:value];
}

+ (NSString * _Nullable)cachedExceptionReason {
    const char *value = capture_bitdrift_crash_last_exception_reason();
    return value == nil ? nil : [NSString stringWithUTF8String:value];
}

+ (void)stopCrashReporter {
    capture_bitdrift_crash_stop();
}

@end
