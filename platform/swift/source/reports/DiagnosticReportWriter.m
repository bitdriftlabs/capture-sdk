// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

#import "DiagnosticReportWriter.h"
#import "BitdriftCrashHandler.h"
#import "bd-report-writer/ffi.h"

#import <mach/exception_types.h>
#include <stdlib.h>
#include <stdint.h>
#import <signal.h>

// Unpack version numbers formatted as "iPhone OS 16.7.11 (20H360)"
// - `osName` is everything before the version number ("iPhone OS")
// - `osVersion` is the dot-delimited numbers
// - `buildNumber` is the parenthesized letters and numbers
static NSString *const OS_VERSION_MATCHER = @"^(?<osName>.*)\\s+(?<osVersion>\\d+.*)\\s+\\((?<buildNumber>.*)\\)$";

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

@interface DiagnosticReportWriter ()
@property (nonnull, strong) NSURL *outputDir;
@property (nonnull, strong) NSString *sdkVersion;
@property BOOL fileSizeOptimizationEnabled;
@property CAPMemoryPressureLevel memoryPressureLevel;
@property (nonnull, strong) NSFileManager *fileManager;
@property (nonnull, strong) MetricKitDiagnosticParsing *parsing;
@end

@implementation DiagnosticReportWriter

- (instancetype)initWithOutputDir:(NSURL *)outputDir
                        sdkVersion:(NSString *)sdkVersion
      fileSizeOptimizationEnabled:(BOOL)fileSizeOptimizationEnabled
               memoryPressureLevel:(CAPMemoryPressureLevel)memoryPressureLevel
                       fileManager:(NSFileManager *)fileManager {
  if (self = [super init]) {
    self.outputDir = outputDir;
    self.sdkVersion = sdkVersion;
    self.fileSizeOptimizationEnabled = fileSizeOptimizationEnabled;
    self.memoryPressureLevel = memoryPressureLevel;
    self.fileManager = fileManager;
    self.parsing = [MetricKitDiagnosticParsing new];
  }
  return self;
}

// MARK: - Public

- (void)writeCrashReportWithType:(ReportType)reportType
                             dict:(NSDictionary *)crashDict
                             name:(NSString *)name
                           reason:(NSString *)reason
                machExceptionType:(NSNumber *)machExceptionType
                    exceptionCode:(NSNumber *)exceptionCode
                           signal:(NSNumber *)signal
                terminationReason:(NSString *)terminationReason
                    capturedCrash:(BitdriftPreviousCrash *)capturedCrash
                         metadata:(NSDictionary *)metadata
               applicationVersion:(NSString *)applicationVersion
                        timestamp:(NSTimeInterval)timestamp {
  const void *handle = 0;
  bdrw_create_buffer_handle(&handle, reportType, SDK_ID, cstring_from(self.sdkVersion), self.fileSizeOptimizationEnabled);

  [self serializeErrorThreads:&handle crash:crashDict name:name reason:reason order:FrameOrderInnerToOuter];
  [self serializeCrashInfo:&handle
                  crashDict:crashDict
          machExceptionType:machExceptionType
              exceptionCode:exceptionCode
                     signal:signal
          terminationReason:terminationReason
              capturedCrash:capturedCrash
                 metricTime:timestamp];
  [self serializeAppMetrics:&handle appVersion:applicationVersion metadata:metadata];
  [self serializeDeviceMetrics:&handle metadata:metadata timestamp:timestamp];
  [self finishReport:&handle reportType:reportType timestamp:timestamp];
}

- (void)writeNonCrashReportWithType:(ReportType)reportType
                                dict:(NSDictionary *)dict
                                name:(NSString *)name
                              reason:(NSString *)reason
                               order:(FrameOrder)order
                            metadata:(NSDictionary *)metadata
                  applicationVersion:(NSString *)applicationVersion
                           timestamp:(NSTimeInterval)timestamp {
  const void *handle = 0;
  bdrw_create_buffer_handle(&handle, reportType, SDK_ID, cstring_from(self.sdkVersion), self.fileSizeOptimizationEnabled);

  [self serializeErrorThreads:&handle crash:dict name:name reason:reason order:order];
  [self serializeAppMetrics:&handle appVersion:applicationVersion metadata:metadata];
  [self serializeDeviceMetrics:&handle metadata:metadata timestamp:timestamp];
  [self finishReport:&handle reportType:reportType timestamp:timestamp];
}

// MARK: - Threads + error

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
                        order:(FrameOrder)order {
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

// MARK: - BDAppleCrashInfo (MetricKit side + bitdrift in-process side)

// Writes two independent BDAppleCrashInfo entries to `handle` when available: one from the
// out-of-process MetricKit report (always, if `crash` has thread data), and one from bitdrift's
// own in-process crash capture (only when `capturedCrash` carries one). They're independent
// sources of the same underlying crash, so both get written rather than one replacing the other.
- (void)serializeCrashInfo:(BDProcessorHandle)handle
                  crashDict:(NSDictionary *)crash
          machExceptionType:(NSNumber *)machExceptionType
              exceptionCode:(NSNumber *)exceptionCode
                     signal:(NSNumber *)signal
          terminationReason:(NSString *)terminationReason
              capturedCrash:(BitdriftPreviousCrash *)capturedCrash
                 metricTime:(NSTimeInterval)metricTime {
  BDCrashInfoThreadDetailsStorage metricKitThreadDetails =
    [self buildCrashInfoThreadDetails:crash order:FrameOrderInnerToOuter];
  BDAppleCrashInfoPayload payload = [self buildPayloadWithMachExceptionType:machExceptionType
                                                                exceptionCode:exceptionCode
                                                                       signal:signal
                                                            terminationReason:terminationReason];

  uint64_t seconds = 0;
  uint32_t nanos = 0;
  [self.parsing timestampComponentsFor:metricTime seconds:&seconds nanos:&nanos];
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

  if (capturedCrash.kind != BitdriftPreviousCrashKindNSException || capturedCrash.nsexception == nil) {
    return;
  }

  NSArray<BitdriftCrashStackFrame *> *capturedFrames = capturedCrash.nsexception.frames ?: @[];
  [self addCapturedCrashBinaryImages:handle frames:capturedFrames];
  BDCrashInfoThreadDetailsStorage capturedThreadDetails =
    [self buildCrashInfoThreadDetailsFromCapturedFrames:capturedFrames];

  BDAppleCrashInfoPayload capturedPayload = {0};
  capturedPayload.has_nsexception = true;
  capturedPayload.nsexception = (BDNSException) {
    .name = cstring_from(capturedCrash.nsexception.name),
    .reason = cstring_from(capturedCrash.nsexception.reason),
  };

  uint64_t capturedSeconds = 0;
  uint32_t capturedNanos = 0;
  [self.parsing timestampComponentsFor:capturedCrash.crashDate.timeIntervalSince1970
                                seconds:&capturedSeconds
                                  nanos:&capturedNanos];
  bdrw_add_apple_crash_info(handle,
                            CrashReporterScopeValueInProcess,
                            CrashReporterValueAppleBitdriftCrashReporter,
                            capturedSeconds,
                            capturedNanos,
                            &capturedPayload,
                            capturedThreadDetails.details.threads_count > 0
                              ? &capturedThreadDetails.details
                              : NULL);
  [self freeCrashInfoThreadDetails:capturedThreadDetails];
}

// Walks the same call stack tree as `serializeErrorThreads:`, but into a `BDCrashInfoThreadDetails`
// (the shape `bdrw_add_apple_crash_info` expects) instead of writing directly to the report buffer.
- (BDCrashInfoThreadDetailsStorage)buildCrashInfoThreadDetails:(NSDictionary *)crash
                                                         order:(FrameOrder)order {
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
    if (frame.imageID == nil) {
      continue;
    }
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

- (void)freeCrashInfoThreadDetails:(BDCrashInfoThreadDetailsStorage)threadDetails {
  for (uintptr_t thread_index = 0; thread_index < threadDetails.details.threads_count; thread_index++) {
    free((void *)threadDetails.threads[thread_index].stack);
  }
  free(threadDetails.threads);
}

// Builds the MetricKit-derived BDAppleCrashInfoPayload: mach exception, posix signal, and (for
// SIGKILL watchdog terminations) the parsed termination context.
- (BDAppleCrashInfoPayload)buildPayloadWithMachExceptionType:(NSNumber *)machExceptionType
                                                exceptionCode:(NSNumber *)exceptionCode
                                                       signal:(NSNumber *)signal
                                            terminationReason:(NSString *)terminationReason {
  BDAppleCrashInfoPayload payload = {0};

  if (machExceptionType != nil) {
    payload.has_mach_exception = true;
    payload.mach_exception = (BDMachException) {
      .type_ = machExceptionType.unsignedIntValue,
      .code = exceptionCode.unsignedLongLongValue,
      .subcode = 0,
    };
  }

  if (signal != nil) {
    payload.has_posix_signal = true;
    payload.posix_signal = (BDPosixSignal) {
      .number = signal.intValue,
      .code = 0,
      .errno_value = 0,
      .has_fault_address = false,
      .fault_address = 0,
    };
  }

  NSDictionary<NSString *, NSString *> *terminationContext = @{};
  BOOL isSigkill = signal != nil && signal.intValue == SIGKILL;
  if (isSigkill) {
    terminationContext = [self.parsing parseTerminationContext:terminationReason];
  }
  if (isSigkill && (terminationReason.length > 0 || terminationContext.count > 0)) {
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

// MARK: - App / device metrics

- (void)serializeAppMetrics:(BDProcessorHandle)handle
                 appVersion:(NSString *)app_version
                   metadata:(NSDictionary *)metadata {
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

- (void)serializeDeviceMetrics:(BDProcessorHandle)handle
                      metadata:(NSDictionary *)metadata
                     timestamp:(NSTimeInterval)timestamp {
  BDOSBuild *os_build_info = [[BDOSBuild alloc] initWithVersion:string_for_key(metadata, @"osVersion")];
  long double seconds = truncl(timestamp);
  long double nanoseconds = (timestamp - seconds) * NSEC_PER_SEC;

  BDDeviceMetrics device = {
    .model = cstring_from(string_for_key(metadata, @"deviceType")),
    .architecture = [self.parsing architectureConstantFor:string_for_key(metadata, @"platformArchitecture")],
    .os_kernversion = cstring_from(os_build_info.kernversion),
    .os_version = cstring_from(os_build_info.version),
    .os_brand = cstring_from(os_build_info.name),
    .time_nanos = nanoseconds,
    .time_seconds = seconds,
    .low_power_mode_enabled = [metadata[@"lowPowerModeEnabled"] boolValue],
  };
  bdrw_add_device(handle, &device);
}

// MARK: - Report finalization

- (void)finishReport:(BDProcessorHandle)handle reportType:(ReportType)reportType timestamp:(NSTimeInterval)timestamp {
  uint64_t length = 0;
  const uint8_t *contents = bdrw_get_completed_buffer(handle, &length);
  if (contents == NULL) {
    bdrw_dispose_buffer_handle(handle);
    return;
  }

  NSData *data = [NSData dataWithBytes:contents length:length];
  NSString *identifier = [[NSUUID UUID] UUIDString];
  NSString *filename = [NSString stringWithFormat:@"%Lf_%@_%@.cap",
                          truncl(timestamp), [self.parsing nameForReportType:reportType], identifier];
  NSString *path = [[self.outputDir URLByAppendingPathComponent:filename] path];
  [self.fileManager createFileAtPath:path contents:data attributes:0];
  bdrw_dispose_buffer_handle(handle);
}

@end
