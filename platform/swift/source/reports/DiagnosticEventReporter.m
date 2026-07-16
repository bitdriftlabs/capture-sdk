// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

#import "DiagnosticEventReporter.h"
#import "BitdriftCrashHandler.h"
#import "bd-report-writer/ffi.h"

#import <mach/exception_types.h>
#include <stdlib.h>
#include <stdint.h>
#import <signal.h>

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

// Unpack version numbers formatted as "iPhone OS 16.7.11 (20H360)"
// - `osName` is everything before the version number ("iPhone OS")
// - `osVersion` is the dot-delimited numbers
// - `buildNumber` is the parenthesized letters and numbers
static NSString *const OS_VERSION_MATCHER = @"^(?<osName>.*)\\s+(?<osVersion>\\d+.*)\\s+\\((?<buildNumber>.*)\\)$";
// Name to use for `MXHangDiagnostic` and 0x8badf00d events if no better name is detected
static NSString *const DEFAULT_HANG_NAME = @"App Hang";
// SDK identifier used in generated files
static const char *const SDK_ID = "io.bitdrift.capture-apple";

typedef enum : NSUInteger {
  /// The root frame is the "top"/outermost frame of the call stack tree
  FrameOrderOuterToInner,
  /// The root frame is the "bottom"/innermost frame of the call stack tree
  FrameOrderInnerToOuter,
} FrameOrder;

typedef struct {
  BDCrashInfoThreadDetails details;
  BDCrashInfoThread *threads;
} BDCrashInfoThreadDetailsStorage;

// MARK: - Static helpers (pure utilities, no instance state)

static id object_for_key(NSDictionary *dict, NSString *key, Class klass) {
  if ([dict isKindOfClass:[NSDictionary class]]) {
    id value = dict[key];
    return [value isKindOfClass:klass] ? value : nil;
  }
  return nil;
}

#define string_for_key(dict, key) object_for_key(dict, key, [NSString class])
#define number_for_key(dict, key) object_for_key(dict, key, [NSNumber class])
#define array_for_key(dict, key) object_for_key(dict, key, [NSArray class])
#define dict_for_key(dict, key) object_for_key(dict, key, [NSDictionary class])

static inline const char * cstring_from(NSString *str) {
  return [str cStringUsingEncoding:NSUTF8StringEncoding];
}

static inline void timestamp_components(NSTimeInterval timestamp, uint64_t *seconds, uint32_t *nanos) {
  long double whole_seconds = truncl(timestamp);
  *seconds = (uint64_t)whole_seconds;
  *nanos = (uint32_t)((timestamp - whole_seconds) * NSEC_PER_SEC);
}

static NSString *trimmed_value_after_prefix(NSString *line, NSString *prefix) {
  if (![line hasPrefix:prefix]) {
    return nil;
  }

  NSString *value = [line substringFromIndex:prefix.length];
  return [value stringByTrimmingCharactersInSet:[NSCharacterSet whitespaceAndNewlineCharacterSet]];
}

static NSDictionary<NSString *, NSString *> *parse_termination_context(NSString *terminationReason) {
  if (terminationReason.length == 0) {
    return @{};
  }

  NSMutableDictionary<NSString *, NSString *> *fields = [NSMutableDictionary dictionary];
  NSError *error = nil;
  NSRegularExpression *headerPattern = [NSRegularExpression regularExpressionWithPattern:@"domain:(\\S+)\\s+code:(\\S+)\\s+explanation:(.*)$"
                                                                                 options:NSRegularExpressionAnchorsMatchLines
                                                                                   error:&error];
  NSTextCheckingResult *headerMatch = [headerPattern firstMatchInString:terminationReason
                                                                options:0
                                                                  range:NSMakeRange(0, terminationReason.length)];
  if (headerMatch.numberOfRanges == 4) {
    fields[@"domain"] = [terminationReason substringWithRange:[headerMatch rangeAtIndex:1]];
    fields[@"code"] = [terminationReason substringWithRange:[headerMatch rangeAtIndex:2]];
    fields[@"explanation"] = [terminationReason substringWithRange:[headerMatch rangeAtIndex:3]];
  }

  for (NSString *line in [terminationReason componentsSeparatedByCharactersInSet:[NSCharacterSet newlineCharacterSet]]) {
    NSString *processVisibility = trimmed_value_after_prefix(line, @"ProcessVisibility:");
    if (processVisibility != nil) {
      fields[@"process_visibility"] = processVisibility;
      continue;
    }

    NSString *processState = trimmed_value_after_prefix(line, @"ProcessState:");
    if (processState != nil) {
      fields[@"process_state"] = processState;
      continue;
    }

    NSString *watchdogEvent = trimmed_value_after_prefix(line, @"WatchdogEvent:");
    if (watchdogEvent != nil) {
      fields[@"watchdog_event"] = watchdogEvent;
      continue;
    }

    NSString *watchdogVisibility = trimmed_value_after_prefix(line, @"WatchdogVisibility:");
    if (watchdogVisibility != nil) {
      fields[@"watchdog_visibility"] = watchdogVisibility;
    }
  }

  return fields;
}

static const char *name_for_diagnostic_type(ReportType type) {
  switch (type) {
    case ReportTypeNativeCrash:
      return "crash";
    case ReportTypeAppNotResponding:
      return "anr";
    case ReportTypeNone:
    default:
      return "unknown";
  }
}

static BDCrashInfoThreadDetailsStorage empty_crash_info_thread_details_storage(void) {
  return (BDCrashInfoThreadDetailsStorage){0};
}

// MARK: - BDOSBuild

@interface BDOSBuild : NSObject
@property (strong, nonatomic) NSString *name;
@property (strong, nonatomic) NSString *version;
@property (strong, nonatomic) NSString *kernversion;
@end

@implementation BDOSBuild
- (instancetype)initWithVersion:(NSString *)version {
  if (!version) {
    return nil;
  }
  NSRegularExpression *matcher = [NSRegularExpression regularExpressionWithPattern:OS_VERSION_MATCHER options:0 error:nil];
  NSTextCheckingResult *match = [[matcher matchesInString:version options:0 range:NSMakeRange(0, version.length)] firstObject];
  NSRange nameRange = [match rangeWithName:@"osName"];
  NSRange versionRange = [match rangeWithName:@"osVersion"];
  NSRange buildRange = [match rangeWithName:@"buildNumber"];
  if (self = [super init]) {
    if (!nameRange.length || !versionRange.length || !buildRange.length) {
      self.version = version; // pathological case where there's a match but the captures don't hit
    } else {
      self.name = [version substringWithRange:nameRange];
      self.version = [version substringWithRange:versionRange];
      self.kernversion = [version substringWithRange:buildRange];
    }
  }
  return self;
}
@end

// MARK: - DiagnosticEventReporter

@interface DiagnosticEventReporter ()
@property (nonnull, strong) NSURL *dir;
@property (nonnull, strong) NSString *sdkVersion;
@property (nonnull, strong, nonatomic) NSMeasurement <NSUnitDuration *> *minimumHangDuration;
@property (nullable, strong, nonatomic) void (^completionHandler)();
@property (nullable, copy, nonatomic) CAPCrashEnrichmentSummaryHandler crashEnrichmentSummaryHandler;
@property CAPDiagnosticType diagnosticTypes;
@property BOOL fileSizeOptimizationEnabled;
@property BOOL useStackOverlapMatching;
@property CAPMemoryPressureLevel memoryPressureLevel;
@property (nonnull, strong, nonatomic) id<CrashReporting> crashReporting;
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
    self.sdkVersion = sdkVersion;
    self.diagnosticTypes = types;
    self.completionHandler = completionHandler;
    self.fileSizeOptimizationEnabled = fileSizeOptimizationEnabled;
    self.useStackOverlapMatching = useStackOverlapMatching;
    self.crashReporting = crashReporting;
    self.crashEnrichmentSummaryHandler = crashEnrichmentSummaryHandler;
    self.memoryPressureLevel = memoryPressureLevel;
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
            capturedCrash:(BitdriftPreviousCrash *)capturedCrash {
  const void *handle = 0;
  ReportType report_type = [self serializeDiagnostic:event atTimestamp:timestamp capturedCrash:capturedCrash intoHandle:&handle];
  if (report_type != ReportTypeNone && handle != 0) {
    uint64_t length = 0;
    const uint8_t *contents = bdrw_get_completed_buffer(&handle, &length);
    NSData *data = [NSData dataWithBytes:contents length:length];
    NSString *identifier = [[NSUUID UUID] UUIDString];
    NSString *filename = [NSString stringWithFormat:@"%Lf_%s_%@.cap", truncl(timestamp), name_for_diagnostic_type(report_type), identifier];
    NSString *path = [[self.dir URLByAppendingPathComponent:filename] path];
    [[NSFileManager defaultManager] createFileAtPath:path contents:data attributes:0];
    bdrw_dispose_buffer_handle(&handle);
  }
}

// MARK: - Serialization


- (ReportType)serializeDiagnostic:(MXDiagnostic *)event
                      atTimestamp:(NSTimeInterval)timestamp
                    capturedCrash:(BitdriftPreviousCrash *)capturedCrash
                       intoHandle:(BDProcessorHandle)handle API_AVAILABLE(ios(14.0), macos(12.0)) {
  ReportType report_type = ReportTypeNone;
  if ([event isKindOfClass:[MXCrashDiagnostic class]]) {
    MXCrashDiagnostic *mxCrash = (MXCrashDiagnostic *)event;
    // Watchdog terminations arrive as MXCrashDiagnostic but should be reported as ANRs.
    BOOL is_hang = [self crashIsHangTermination:mxCrash];
    capturedCrash = is_hang ? nil : capturedCrash;
    report_type = is_hang ? ReportTypeAppNotResponding : ReportTypeNativeCrash;
    bdrw_create_buffer_handle(handle, report_type, SDK_ID, cstring_from(self.sdkVersion), self.fileSizeOptimizationEnabled);
    NSString *name = is_hang ? DEFAULT_HANG_NAME : [self nameForCrash:mxCrash];
    NSString *reason = [self reasonForCrash:mxCrash name:name capturedCrash:capturedCrash];
    NSDictionary<NSString *, NSString *> *summary = nil;
    NSDictionary *dictReport = [self.crashReporting enhancedMetricKitReport:event.dictionaryRepresentation
                                                        useStackOverlapMatching:self.useStackOverlapMatching
                                                                     summaryOut:&summary];
    if (summary != nil && self.crashEnrichmentSummaryHandler != nil) {
      self.crashEnrichmentSummaryHandler(summary);
    }
    [self serializeErrorThreads:handle crash:dictReport name:name reason:reason order:FrameOrderInnerToOuter];
    [self serializeCrashInfo:handle
                   crashDict:dictReport
                     mxCrash:mxCrash
               capturedCrash:capturedCrash
                  metricTime:timestamp];
  } else if ([event isKindOfClass:[MXHangDiagnostic class]]) {
    report_type = ReportTypeAppNotResponding;
    bdrw_create_buffer_handle(handle, report_type, SDK_ID, cstring_from(self.sdkVersion), self.fileSizeOptimizationEnabled);
    MXHangDiagnostic *hang = (MXHangDiagnostic *)event;
    NSMeasurementFormatter *formatter = [NSMeasurementFormatter new];
    NSString *duration = [formatter stringFromMeasurement:hang.hangDuration];
    NSString *reason = [NSString stringWithFormat:@"app was unresponsive for %@", duration];
    NSDictionary *representation = event.dictionaryRepresentation;
    NSString *name = representation[@"hangType"] ?: DEFAULT_HANG_NAME;
    [self serializeErrorThreads:handle crash:representation name:name reason:reason order:FrameOrderOuterToInner];
  } else {
    return report_type;
  }
  NSDictionary *metadata = event.metaData.dictionaryRepresentation;
  [self serializeAppMetrics:handle appVersion:event.applicationVersion metadata:metadata];
  [self serializeDeviceMetrics:handle metadata:metadata timestamp:timestamp];
  return report_type;
}

- (BOOL)crashIsHangTermination:(MXCrashDiagnostic *)event API_AVAILABLE(ios(14.0), macos(12.0)) {
  // if its a watchdog termination
  return [event.exceptionType isEqualToNumber:@EXC_CRASH] && [event.signal isEqualToNumber:@SIGKILL]
  // and the termination context indicates an 0x8BADF00D (ate bad food) watchdog kill
  && ([[event.terminationReason lowercaseString] containsString:@"0x8badf00d"]
      // or no context is provided
      || (event.terminationReason == nil && [event.exceptionCode isEqualToNumber:@0]));
}

// Writes every thread in `crash`'s call stack tree to the in-progress report buffer `handle` via
// `bdrw_add_thread`, registering each frame's owning binary image along the way, and additionally
// writes `bdrw_add_error` for the crashed thread. Threads with no frames are skipped unless
// they're the crashed thread (so a crash with no captured stack still produces an error entry). A
// thread whose walk hits an invalid frame (missing binary/address fields) stops early rather than
// failing the whole call.
- (void)serializeErrorThreads:(BDProcessorHandle)handle
                        crash:(NSDictionary *)crash
                         name:(NSString *)name
                       reason:(NSString *)reason
                        order:(FrameOrder)order API_AVAILABLE(ios(14.0), macos(12.0)) {
  NSMutableSet <NSString *>* images = [NSMutableSet new];
  NSArray *call_stacks = dict_for_key(crash, @"callStackTree")[@"callStacks"];
  uint32_t crashed_index = [self crashedThreadIndex:call_stacks];

  for (uint32_t thread_index = 0; thread_index < call_stacks.count; thread_index++) {
    NSDictionary *thread = call_stacks[thread_index];
    NSString *threadName = string_for_key(thread, @"name");
    NSDictionary *frame = [self threadRootFrame:thread];
    uint64_t frame_count = [self countFrames:frame];
    if (frame_count == 0 && thread_index != crashed_index) {
      continue;
    }
    BDStackFrame *stack = frame_count
      ? (BDStackFrame *)calloc(frame_count, sizeof(BDStackFrame))
      : 0;

    uint32_t frame_index = 0;
    while ([frame isKindOfClass:[NSDictionary class]] && frame_index < frame_count) {
      NSString *binary_name = string_for_key(frame, @"binaryName");
      NSString *binary_uuid = string_for_key(frame, @"binaryUUID");
      NSNumber *address = number_for_key(frame, @"address");
      NSNumber *offset = number_for_key(frame, @"offsetIntoBinaryTextSegment");
      if (binary_name && binary_uuid && address && offset) {
        if (![images containsObject:binary_uuid]) {
          BDBinaryImage image = {
            .id = cstring_from(binary_uuid),
            .path = cstring_from(binary_name),
            .load_address = [address unsignedLongLongValue] - [offset unsignedLongLongValue],
          };
          bdrw_add_binary_image(handle, &image);
          [images addObject:binary_uuid];
        }
      } else {
        break; // if the frame is invalid, it's time to leave
      }

      // Handle differing frame ordering for MXDiagnostic types (FB18377370)
      // insertion order is most recent to oldest
      uint64_t insert_at = order == FrameOrderInnerToOuter
        ? frame_index // the "root" frame (dyld or pthread start) is the furthest from index 0
        : (frame_count - frame_index - 1); // the root frame is index 0
      stack[insert_at] = (BDStackFrame) {
        .image_id = cstring_from(binary_uuid),
        .frame_address = [address unsignedLongLongValue],
        .type_ = 2, // FrameType.DWARF
      };
      frame = [array_for_key(frame, @"subFrames") firstObject];
      frame_index++;
    }
    BDThread bdthread = { .index = thread_index, .quality_of_service = -1, .name = cstring_from(threadName), .active = (thread_index == crashed_index) };
    bdrw_add_thread(handle, [call_stacks count], &bdthread, frame_index, stack);
    if (thread_index == crashed_index) {
      bdrw_add_error(handle, cstring_from(name), cstring_from(reason), 0, frame_index, stack);
    }
    free(stack);
  }
  // handle case where there are no threads
  if (call_stacks.count == 0) {
    bdrw_add_error(handle, cstring_from(name), cstring_from(reason), 0, 0, 0);
  }
}

// Walks the same call stack tree as `serializeErrorThreads:`, but into a `BDCrashInfoThreadDetails`
// (the shape `bdrw_add_apple_crash_info` expects) instead of writing directly to the report buffer.
- (BDCrashInfoThreadDetailsStorage)buildCrashInfoThreadDetails:(NSDictionary *)crash
                                                         order:(FrameOrder)order API_AVAILABLE(ios(14.0), macos(12.0)) {
  NSArray *call_stacks = dict_for_key(crash, @"callStackTree")[@"callStacks"];
  if (call_stacks.count == 0) {
    return empty_crash_info_thread_details_storage();
  }

  uint32_t crashed_index = [self crashedThreadIndex:call_stacks];
  BDCrashInfoThread *threads = (BDCrashInfoThread *)calloc(call_stacks.count, sizeof(BDCrashInfoThread));
  uint16_t thread_count = 0;

  for (uint32_t thread_index = 0; thread_index < call_stacks.count; thread_index++) {
    NSDictionary *thread = call_stacks[thread_index];
    NSString *threadName = string_for_key(thread, @"name");
    NSDictionary *frame = [self threadRootFrame:thread];
    uint64_t frame_count = [self countFrames:frame];
    if (frame_count == 0) {
      // Unlike serializeErrorThreads:, there's no crashed-thread exception here: this data
      // isn't used to report an error, so an empty crashed thread just doesn't contribute frames.
      continue;
    }

    // Binary images aren't registered here: this always runs right after serializeErrorThreads:
    // over the same `crash` dict, which already registered every image in this tree.
    BDStackFrame *stack = (BDStackFrame *)calloc(frame_count, sizeof(BDStackFrame));
    uint32_t frame_index = 0;
    while ([frame isKindOfClass:[NSDictionary class]] && frame_index < frame_count) {
      NSString *binary_uuid = string_for_key(frame, @"binaryUUID");
      NSNumber *address = number_for_key(frame, @"address");
      if (binary_uuid == nil || address == nil) {
        break;
      }

      uint64_t insert_at = order == FrameOrderInnerToOuter
        ? frame_index
        : (frame_count - frame_index - 1);
      stack[insert_at] = (BDStackFrame) {
        .image_id = cstring_from(binary_uuid),
        .frame_address = [address unsignedLongLongValue],
        .type_ = 2,
      };
      frame = [array_for_key(frame, @"subFrames") firstObject];
      frame_index++;
    }

    if (frame_index == 0) {
      free(stack);
      continue;
    }

    threads[thread_count++] = (BDCrashInfoThread) {
      .thread =
        (BDThread){
          .index = thread_index,
          .quality_of_service = -1,
          .name = cstring_from(threadName),
          .active = (thread_index == crashed_index),
        },
      .stack_count = frame_index,
      .stack = stack,
    };
  }

  if (thread_count == 0) {
    free(threads);
    return empty_crash_info_thread_details_storage();
  }

  return (BDCrashInfoThreadDetailsStorage){
    .details =
      (BDCrashInfoThreadDetails){
        .count = (uint16_t)call_stacks.count,
        .threads_count = thread_count,
        .threads = threads,
      },
    .threads = threads,
  };
}

- (BDCrashInfoThreadDetailsStorage)buildCrashInfoThreadDetailsFromCapturedFrames:(NSArray *)frames {
  if (frames.count == 0) {
    return empty_crash_info_thread_details_storage();
  }

  BDCrashInfoThread *threads = (BDCrashInfoThread *)calloc(1, sizeof(BDCrashInfoThread));
  BDStackFrame *stack = (BDStackFrame *)calloc(frames.count, sizeof(BDStackFrame));
  uint32_t frame_count = 0;

  for (BitdriftCrashStackFrame *frame in frames) {
    stack[frame_count++] = (BDStackFrame) {
      .type_ = 2,
      .frame_address = frame.frameAddress,
      .image_id = cstring_from(frame.imageID),
    };
  }

  if (frame_count == 0) {
    free(stack);
    free(threads);
    return empty_crash_info_thread_details_storage();
  }

  threads[0] = (BDCrashInfoThread) {
    .thread =
      (BDThread){
        .index = 0,
        .quality_of_service = -1,
        .active = true,
      },
    .stack_count = frame_count,
    .stack = stack,
  };
  return (BDCrashInfoThreadDetailsStorage){
    .details =
      (BDCrashInfoThreadDetails){
        .count = 1,
        .threads_count = 1,
        .threads = threads,
      },
    .threads = threads,
  };
}

- (void)addCapturedCrashBinaryImages:(BDProcessorHandle)handle frames:(NSArray *)frames {
  NSMutableSet<NSString *> *seenImages = [NSMutableSet set];
  for (BitdriftCrashStackFrame *frame in frames) {
    if (frame.imageID == nil || frame.binaryName == nil || [seenImages containsObject:frame.imageID]) {
      continue;
    }

    BDBinaryImage image = {
      .id = cstring_from(frame.imageID),
      .path = cstring_from(frame.binaryName),
      .load_address = frame.imageLoadAddress,
    };
    bdrw_add_binary_image(handle, &image);
    [seenImages addObject:frame.imageID];
  }
}

- (void)freeCrashInfoThreadDetails:(BDCrashInfoThreadDetailsStorage)threadDetails API_AVAILABLE(ios(14.0), macos(12.0)) {
  for (uintptr_t thread_index = 0; thread_index < threadDetails.details.threads_count; thread_index++) {
    free((void *)threadDetails.threads[thread_index].stack);
  }
  free(threadDetails.threads);
}

// Builds the MetricKit-derived BDAppleCrashInfoPayload: mach exception, posix signal, and (for
// SIGKILL watchdog terminations) the parsed termination context.
- (BDAppleCrashInfoPayload)buildMetricKitPayload:(MXCrashDiagnostic *)mxCrash API_AVAILABLE(ios(14.0), macos(12.0)) {
  BDAppleCrashInfoPayload payload = {0};
  if (mxCrash.exceptionType != nil) {
    payload.has_mach_exception = true;
    payload.mach_exception = (BDMachException) {
      .type_ = mxCrash.exceptionType.unsignedIntValue,
      .code = mxCrash.exceptionCode.unsignedLongLongValue,
      .subcode = 0,
    };
  }

  if (mxCrash.signal != nil) {
    payload.has_posix_signal = true;
    payload.posix_signal = (BDPosixSignal) {
      .number = mxCrash.signal.intValue,
      .code = 0,
      .errno_value = 0,
      .has_fault_address = false,
      .fault_address = 0,
    };
  }

  NSDictionary<NSString *, NSString *> *terminationContext = @{};
  if ([mxCrash.signal isEqualToNumber:@SIGKILL]) {
    terminationContext = parse_termination_context(mxCrash.terminationReason);
  }
  if ([mxCrash.signal isEqualToNumber:@SIGKILL] && (mxCrash.terminationReason.length > 0 || terminationContext.count > 0)) {
    payload.has_termination = true;
    payload.termination = (BDAppleTermination) {
      .domain = cstring_from(terminationContext[@"domain"]),
      .code = cstring_from(terminationContext[@"code"]),
      .explanation = cstring_from(terminationContext[@"explanation"]),
      .process_visibility = cstring_from(terminationContext[@"process_visibility"]),
      .process_state = cstring_from(terminationContext[@"process_state"]),
      .watchdog_event = cstring_from(terminationContext[@"watchdog_event"]),
      .watchdog_visibility = cstring_from(terminationContext[@"watchdog_visibility"]),
    };
  }

  return payload;
}

// Writes two independent BDAppleCrashInfo entries to `handle` when available: one from the
// out-of-process MetricKit report (always, if `crash` has thread data), and one from bitdrift's
// own in-process crash capture (only when `capturedCrash` carries one). They're independent
// sources of the same underlying crash, so both get written rather than one replacing the other.
- (void)serializeCrashInfo:(BDProcessorHandle)handle
                 crashDict:(NSDictionary *)crash
                   mxCrash:(MXCrashDiagnostic *)mxCrash
             capturedCrash:(BitdriftPreviousCrash *)capturedCrash
                metricTime:(NSTimeInterval)metricTime API_AVAILABLE(ios(14.0), macos(12.0)) {
  BDCrashInfoThreadDetailsStorage metricKitThreadDetails =
    [self buildCrashInfoThreadDetails:crash order:FrameOrderInnerToOuter];
  BDAppleCrashInfoPayload payload = [self buildMetricKitPayload:mxCrash];

  uint64_t seconds = 0;
  uint32_t nanos = 0;
  timestamp_components(metricTime, &seconds, &nanos);
  bdrw_add_apple_crash_info(handle,
                            CrashReporterScopeValueOutOfProcess,
                            CrashReporterValueAppleMetricKit,
                            seconds,
                            nanos,
                            &payload,
                            metricKitThreadDetails.details.threads_count > 0
                              ? &metricKitThreadDetails.details
                              : NULL);
  [self freeCrashInfoThreadDetails:metricKitThreadDetails];

  if (capturedCrash.kind == BitdriftPreviousCrashKindNSException && capturedCrash.nsexception != nil) {
    BDCrashInfoThreadDetailsStorage capturedThreadDetails =
      [self buildCrashInfoThreadDetailsFromCapturedFrames:capturedCrash.nsexception.frames];
    [self addCapturedCrashBinaryImages:handle frames:capturedCrash.nsexception.frames];
    BDAppleCrashInfoPayload capturedPayload = {0};
    capturedPayload.has_nsexception = true;
    capturedPayload.nsexception = (BDNSException) {
      .name = cstring_from(capturedCrash.nsexception.name),
      .reason = cstring_from(capturedCrash.nsexception.reason),
    };

    timestamp_components(capturedCrash.crashDate.timeIntervalSince1970, &seconds, &nanos);
    bdrw_add_apple_crash_info(handle,
                              CrashReporterScopeValueInProcess,
                              CrashReporterValueAppleBitdriftCrashReporter,
                              seconds,
                              nanos,
                              &capturedPayload,
                              capturedThreadDetails.details.threads_count > 0
                                ? &capturedThreadDetails.details
                                : NULL);
    [self freeCrashInfoThreadDetails:capturedThreadDetails];
  }
}

- (NSDictionary *)threadRootFrame:(NSDictionary *)thread {
  return [array_for_key(thread, @"callStackRootFrames") firstObject];
}

- (uint64_t)countFrames:(NSDictionary *)rootFrame {
  uint64_t count = 0;
  NSDictionary *frame = rootFrame;
  while ([frame isKindOfClass:[NSDictionary class]]) {
    count++;
    frame = [array_for_key(frame, @"subFrames") firstObject];
  }
  return count;
}

- (uint32_t)crashedThreadIndex:(NSArray *)stacks {
  for (uint32_t index = 0; index < stacks.count; index++) {
    if ([number_for_key(stacks[index], @"threadAttributed") boolValue]) {
      if ([self countFrames:[self threadRootFrame:stacks[index]]] > 0) {
        // match only if thread contains frames (FB18302500)
        return index;
      }
      break;
    }
  }
  for (uint32_t index = 0; index < stacks.count; index++) {
    if ([self countFrames:[self threadRootFrame:stacks[index]]] > 0) {
      // grab first thread with frames if none attributed or attributed to empty
      return index;
    }
  }
  return 0; // first thread is crashed thread if none contain frames
}

- (void)serializeDeviceMetrics:(BDProcessorHandle)handle
                      metadata:(NSDictionary *)metadata
                     timestamp:(NSTimeInterval)timestamp API_AVAILABLE(ios(14.0), macos(12.0)) {
  BDOSBuild *os_build_info = [[BDOSBuild alloc] initWithVersion:string_for_key(metadata, @"osVersion")];
  long double seconds = truncl(timestamp);
  long double nanoseconds = (timestamp - seconds) * NSEC_PER_SEC;

  BDDeviceMetrics device = {
    .model = cstring_from(string_for_key(metadata, @"deviceType")),
    .architecture = [self architectureConstant:string_for_key(metadata, @"platformArchitecture")],
    .os_kernversion = cstring_from(os_build_info.kernversion),
    .os_version = cstring_from(os_build_info.version),
    .os_brand = cstring_from(os_build_info.name),
    .time_nanos = nanoseconds,
    .time_seconds = seconds,
    .low_power_mode_enabled = [metadata[@"lowPowerModeEnabled"] boolValue],
  };
  bdrw_add_device(handle, &device);
}

- (void)serializeAppMetrics:(BDProcessorHandle)handle
                 appVersion:(NSString *)app_version
                   metadata:(NSDictionary *)metadata API_AVAILABLE(ios(14.0), macos(12.0)) {
  NSString *bundle_version = [NSString stringWithFormat:@"%@.%@", app_version, string_for_key(metadata, @"appBuildVersion")];
  BDAppMetrics app = {
    .app_id = cstring_from(string_for_key(metadata, @"bundleIdentifier")),
    .region_format = cstring_from(string_for_key(metadata, @"regionFormat")),
    .version = cstring_from(app_version),
    .cf_bundle_version = cstring_from(bundle_version),
    .memory_pressure_level = self.memoryPressureLevel,
  };
  bdrw_add_app(handle, &app);
}

- (int8_t)architectureConstant:(NSString *)arch {
  if (!arch) {
    return /* Architecture.Unknown */ 0;
  }
  return [arch containsString:@"arm64"] ? /* Architecture.arm64 */ 2 : /* Architecture.x86_64 */ 4;
}

// MARK: - Crash type helpers

#define print_case(name) case name: return @#name
- (NSString *)nameForSignal:(NSNumber *)signal {
  switch (signal.intValue) {
    print_case(SIGHUP);
    print_case(SIGINT);
    print_case(SIGQUIT);
    print_case(SIGILL);
    print_case(SIGTRAP);
    print_case(SIGABRT);
    print_case(SIGFPE);
    print_case(SIGKILL);
    print_case(SIGBUS);
    print_case(SIGSEGV);
    print_case(SIGSYS);
    print_case(SIGPIPE);
    print_case(SIGALRM);
    print_case(SIGTERM);
    print_case(SIGURG);
    print_case(SIGSTOP);
    print_case(SIGTSTP);
    print_case(SIGCONT);
    print_case(SIGCHLD);
    print_case(SIGTTIN);
    print_case(SIGTTOU);
    print_case(SIGXCPU);
    print_case(SIGXFSZ);
    print_case(SIGVTALRM);
    print_case(SIGPROF);
    print_case(SIGWINCH);
    print_case(SIGINFO);
    print_case(SIGUSR1);
    print_case(SIGUSR2);
    default:
      return nil;
  }
}

- (NSString *)nameForCrash:(MXCrashDiagnostic *)event API_AVAILABLE(ios(14.0), macos(12.0)) {
  switch (event.exceptionType.intValue) {
      print_case(EXC_BAD_ACCESS);
      print_case(EXC_BAD_INSTRUCTION);
      print_case(EXC_SYSCALL);
      print_case(EXC_MACH_SYSCALL);
      print_case(EXC_CRASH);
      print_case(EXC_RESOURCE);
      print_case(EXC_GUARD);
      print_case(EXC_CORPSE_NOTIFY);
      print_case(EXC_ARITHMETIC);
      print_case(EXC_EMULATION);
      print_case(EXC_SOFTWARE);
      print_case(EXC_BREAKPOINT);
    default:
      return [self nameForSignal:event.signal];
  }
}
#undef print_case

- (NSString *)reasonForCrash:(MXCrashDiagnostic *)event
                        name:(NSString *)name
                capturedCrash:(BitdriftPreviousCrash *)capturedCrash API_AVAILABLE(ios(14.0), macos(12.0)) {
  NSMutableArray <NSString *> *components = [NSMutableArray new];
  BOOL hasMetricKitExceptionReason = NO;
  if (@available(iOS 17, macOS 14, *)) {
    if (event.exceptionReason) {
      hasMetricKitExceptionReason = YES;
      // exception name included here instead of in the name_for_crash
      // to avoid cases where devices on iOS <17 have a different name
      // and thus a different issue grouping
      [components addObject:[NSString stringWithFormat:@"%@: %@",
                               event.exceptionReason.exceptionName,
                               event.exceptionReason.composedMessage]];
    }
  }
  if (!hasMetricKitExceptionReason
      && capturedCrash.kind == BitdriftPreviousCrashKindNSException
      && capturedCrash.nsexception.name != nil
      && capturedCrash.nsexception.reason != nil) {
    [components addObject:[NSString stringWithFormat:@"%@: %@",
                             capturedCrash.nsexception.name,
                             capturedCrash.nsexception.reason]];
  }
  if (event.terminationReason) {
    [components addObject:event.terminationReason];
  }
  if (event.virtualMemoryRegionInfo) {
    [components addObject:event.virtualMemoryRegionInfo];
  }
  if ([components count]) {
    return [components componentsJoinedByString:@".\n"];
  }
  if (event.exceptionCode) {
    return [NSString stringWithFormat:@"code: %ld, signal: %@",
            event.exceptionCode.longValue,
            [self nameForSignal:event.signal]];
  }

  NSString *reason = [NSString stringWithFormat:@"%@", [self nameForSignal:event.signal]];
  return [reason isEqualToString:name] ? nil : reason;
}

@end
