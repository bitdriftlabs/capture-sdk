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
                   useStackOverlapMatching:(BOOL)useStackOverlapMatching
                            crashReporting:(id<CrashReporting> _Nonnull)crashReporting
             crashEnrichmentSummaryHandler:(CAPCrashEnrichmentSummaryHandler _Nullable)crashEnrichmentSummaryHandler
                         completionHandler:(void (^_Nullable)())completionHandler {
  if (self = [super init]) {
    self.dir = dir;
    self.sdkVersion = sdkVersion;
    self.diagnosticTypes = types;
    self.completionHandler = completionHandler;
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
      NSDate *crashDate = payload.crashDiagnostics.count == 1 ? [self.crashReporting cachedCrashDate] : nil;
      for (MXCrashDiagnostic *event in payload.crashDiagnostics) {
        NSTimeInterval eventTimestamp = crashDate ? crashDate.timeIntervalSince1970 : timestamp;
        [self processDiagnostic:event atTimestamp:eventTimestamp];
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
  const void *handle = 0;
  ReportType report_type = [self serializeDiagnostic:event atTimestamp:timestamp intoHandle:&handle];
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
                       intoHandle:(BDProcessorHandle)handle API_AVAILABLE(ios(14.0), macos(12.0)) {
  ReportType report_type = ReportTypeNone;
  if ([event isKindOfClass:[MXCrashDiagnostic class]]) {
    MXCrashDiagnostic *crash = (MXCrashDiagnostic *)event;
    // Watchdog terminations arrive as MXCrashDiagnostic but should be reported as ANRs.
    BOOL is_hang = [self crashIsHangTermination:crash];
    report_type = is_hang ? ReportTypeAppNotResponding : ReportTypeNativeCrash;
    bdrw_create_buffer_handle(handle, report_type, SDK_ID, cstring_from(self.sdkVersion));
    NSString *name = is_hang ? DEFAULT_HANG_NAME : [self nameForCrash:crash];
    NSString *reason = [self reasonForCrash:crash name:name];
    NSDictionary<NSString *, NSString *> *summary = nil;
    NSDictionary *dictReport = [self.crashReporting enhancedMetricKitReport:event.dictionaryRepresentation
                                                        useStackOverlapMatching:self.useStackOverlapMatching
                                                                     summaryOut:&summary];
    if (summary != nil && self.crashEnrichmentSummaryHandler != nil) {
      self.crashEnrichmentSummaryHandler(summary);
    }
    [self serializeErrorThreads:handle crash:dictReport name:name reason:reason order:FrameOrderInnerToOuter];
  } else if ([event isKindOfClass:[MXHangDiagnostic class]]) {
    report_type = ReportTypeAppNotResponding;
    bdrw_create_buffer_handle(handle, report_type, SDK_ID, cstring_from(self.sdkVersion));
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
  // and the termination context indicates ate bad food or
  && ([[event.terminationReason lowercaseString] containsString:@"0x8badf00d"]
      // no context is provided
      || (event.terminationReason == nil && [event.exceptionCode isEqualToNumber:@0]));
}

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
  // NSException data is captured in-process and carries the original name; MetricKit only provides
  // a Mach exception type, which loses that detail
  BitdriftPreviousCrash *bitdriftCrash = [self.crashReporting cachedPreviousCrash];
  if (bitdriftCrash.kind == BitdriftPreviousCrashKindNSException && bitdriftCrash.nsexception.name != nil) {
    return bitdriftCrash.nsexception.name;
  }
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
                        name:(NSString *)name API_AVAILABLE(ios(14.0), macos(12.0)) {
  BitdriftPreviousCrash *bitdriftCrash = [self.crashReporting cachedPreviousCrash];
  if (bitdriftCrash.kind == BitdriftPreviousCrashKindNSException && bitdriftCrash.nsexception.reason != nil) {
    return bitdriftCrash.nsexception.reason;
  }
  NSMutableArray <NSString *> *components = [NSMutableArray new];
  if (@available(iOS 17, macOS 14, *)) {
    if (event.exceptionReason) {
      // exception name included here instead of in the name_for_crash
      // to avoid cases where devices on iOS <17 have a different name
      // and thus a different issue grouping
      [components addObject:[NSString stringWithFormat:@"%@: %@",
                               event.exceptionReason.exceptionName,
                               event.exceptionReason.composedMessage]];
    }
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
