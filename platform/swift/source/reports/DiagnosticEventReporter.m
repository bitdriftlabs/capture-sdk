// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

#import "DiagnosticEventReporter.h"
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
// Name to use for `MXHangDiagnostic` events if no better name is detected
static NSString *const DEFAULT_HANG_NAME = @"App Hang";
// SDK identifier used in generated files
static const char *const SDK_ID = "io.bitdrift.capture-apple";

/**
 * Create a buffer containing a serialized diagnostic
 *
 * @param handle a ptr to create and populate the buffer
 * @param sdk_version containing library version which will serialize the payload
 * @param event the diagnostic payload to write
 * @param timestamp time at which the event occurred
 *
 * @return true if the buffer was populated
 */
static bool serialize_diagnostic(BDProcessorHandle handle,
                                 NSString *_Nonnull sdk_version,
                                 MXDiagnostic *_Nonnull event,
                                 NSTimeInterval timestamp)
                                 API_AVAILABLE(ios(14.0), macos(12.0));

static const char *name_for_diagnostic_type(MXDiagnostic *event);

@interface DiagnosticEventReporter ()
@property (nonnull, strong) NSURL *dir;
@property (nonnull, strong) NSString *sdkVersion;
@property (nonnull, strong, nonatomic) NSMeasurement <NSUnitDuration *> *minimumHangDuration;
@end

@implementation DiagnosticEventReporter
- (instancetype _Nonnull)initWithOutputDir:(NSURL *_Nonnull)dir
                                sdkVersion:(NSString *_Nonnull)sdkVersion
                        minimumHangSeconds:(NSTimeInterval)seconds {
  if (self = [super init]) {
    self.dir = dir;
    self.sdkVersion = sdkVersion;
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
    for (MXCrashDiagnostic *event in payload.crashDiagnostics) {
      [self processDiagnostic:event atTimestamp:timestamp];
    }

    for (MXHangDiagnostic *event in payload.hangDiagnostics) {
      NSMeasurement *duration = ((MXHangDiagnostic *)event).hangDuration;
      if ([duration measurementBySubtractingMeasurement:self.minimumHangDuration].doubleValue < 0) {
        continue;
      }
      [self processDiagnostic:event atTimestamp:timestamp];
    }
  }
}

- (void)processDiagnostic:(MXDiagnostic *)event atTimestamp:(NSTimeInterval)timestamp {
  const void *handle = 0;
  if (serialize_diagnostic(&handle, self.sdkVersion, event, timestamp) && handle != 0) {
    uint64_t length = 0;
    const uint8_t *contents = bdrw_get_completed_buffer(&handle, &length);
    NSData *data = [NSData dataWithBytes:contents length:length];
    NSString *identifier = [[NSUUID UUID] UUIDString];
    NSString *filename = [NSString stringWithFormat:@"%Lf_%s_%@.cap", truncl(timestamp), name_for_diagnostic_type(event), identifier];
    NSString *path = [[self.dir URLByAppendingPathComponent:filename] path];
    [[NSFileManager defaultManager] createFileAtPath:path contents:data attributes:0];
    bdrw_dispose_buffer_handle(&handle);
  }
}
@end

static NSString *name_for_signal(NSNumber *signal);
static NSString *name_for_crash(MXCrashDiagnostic *event) API_AVAILABLE(ios(14.0), macos(12.0));
static NSString *reason_for_crash(MXCrashDiagnostic *event, NSString *name) API_AVAILABLE(ios(14.0), macos(12.0));

static const char *name_for_diagnostic_type(MXDiagnostic *event) {
  if ([event isKindOfClass:[MXCrashDiagnostic class]]) {
    return "crash";
  } else if ([event isKindOfClass:[MXHangDiagnostic class]]) {
    return "anr";
  }
  return "unknown";
}

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

static NSDictionary *thread_root_frame(NSDictionary *thread) {
  return [array_for_key(thread, @"callStackRootFrames") firstObject];
}

static uint64_t count_frames(NSDictionary *rootFrame) {
  uint64_t count = 0;
  NSDictionary *frame = rootFrame;
  while ([frame isKindOfClass:[NSDictionary class]]) {
    count++;
    frame = [array_for_key(frame, @"subFrames") firstObject];
  }
  return count;
}

static uint32_t crashed_thread_index(NSArray *stacks) {
  for (uint32_t index = 0; index < stacks.count; index++) {
    if ([number_for_key(stacks[index], @"threadAttributed") boolValue]) {
      if (count_frames(thread_root_frame(stacks[index])) > 0) {
        // match only if thread contains frames (FB18302500)
        return index;
      }
      break;
    }
  }
  for (uint32_t index = 0; index < stacks.count; index++) {
    if (count_frames(thread_root_frame(stacks[index])) > 0) {
      // grab first thread with frames if none attributed or attributed to empty
      return index;
    }
  }

  return 0; // first thread is crashed thread if none contain frames
}

typedef enum : NSUInteger {
  /// The root frame is the "top"/outermost frame of the call stack tree
  FrameOrderOuterToInner,
  /// The root frame is the "bottom"/innermost frame of the call stack tree
  FrameOrderInnerToOuter,
} FrameOrder;

static void serialize_error_threads(BDProcessorHandle handle, NSDictionary *crash, NSString *name, NSString *reason, FrameOrder order) {
  NSMutableSet <NSString *>* images = [NSMutableSet new];
  NSArray *call_stacks = dict_for_key(crash, @"callStackTree")[@"callStacks"];
  uint32_t crashed_index = crashed_thread_index(call_stacks);

  for (uint32_t thread_index = 0; thread_index < call_stacks.count; thread_index++) {
    NSDictionary *thread = call_stacks[thread_index];
    NSDictionary *frame = thread_root_frame(thread);
    uint64_t frame_count = count_frames(frame);
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
    if (thread_index == crashed_index) {
      bdrw_add_error(handle, cstring_from(name), cstring_from(reason), 0, frame_index, stack);
    } else {
      BDThread thread = { .quality_of_service = -1 };
      bdrw_add_thread(handle, [call_stacks count], &thread, frame_index, stack);
    }
    free(stack);
  }
  // handle case where there are no threads
  if (call_stacks.count == 0) {
    bdrw_add_error(handle, cstring_from(name), cstring_from(reason), 0, 0, 0);
  }
}

static int8_t architecture_constant(NSString *arch) {
  if (!arch) {
    return /* Architecture.Unknown */ 0;
  }
  return [arch containsString:@"arm64"] ? /* Architecture.arm64 */ 2 : /* Architecture.x86_64 */ 4;
}

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

static void serialize_device_metrics(BDProcessorHandle handle, NSDictionary *metadata, NSTimeInterval timestamp) {
  BDOSBuild *os_build_info = [[BDOSBuild alloc] initWithVersion:string_for_key(metadata, @"osVersion")];
  long double seconds = truncl(timestamp);
  long double nanoseconds = (timestamp - seconds) * NSEC_PER_SEC;

  BDDeviceMetrics device = {
    .model = cstring_from(string_for_key(metadata, @"deviceType")),
    .architecture = architecture_constant(string_for_key(metadata, @"platformArchitecture")),
    .os_kernversion = cstring_from(os_build_info.kernversion),
    .os_version = cstring_from(os_build_info.version),
    .os_brand = cstring_from(os_build_info.name),
    .time_nanos = nanoseconds,
    .time_seconds = seconds,
  };
  bdrw_add_device(handle, &device);
}

static void serialize_app_metrics(BDProcessorHandle handle, NSString *app_version, NSDictionary *metadata) {
  NSString *bundle_version = [NSString stringWithFormat:@"%@.%@", app_version, string_for_key(metadata, @"appBuildVersion")];
  BDAppMetrics app = {
    .app_id = cstring_from(string_for_key(metadata, @"bundleIdentifier")),
    .version = cstring_from(app_version),
    .cf_bundle_version = cstring_from(bundle_version),
  };
  bdrw_add_app(handle, &app);
}

static bool serialize_diagnostic(BDProcessorHandle handle, NSString *sdk_version, MXDiagnostic *event, NSTimeInterval timestamp) {
  if ([event isKindOfClass:[MXCrashDiagnostic class]]) {
    bdrw_create_buffer_handle(handle, /* ReportType.NativeCrash */ 5, SDK_ID, cstring_from(sdk_version));
    MXCrashDiagnostic *crash = (MXCrashDiagnostic *)event;
    NSString *name = name_for_crash(crash);
    NSString *reason = reason_for_crash(crash, name);
    serialize_error_threads(handle, event.dictionaryRepresentation, name, reason, FrameOrderInnerToOuter);
  } else if ([event isKindOfClass:[MXHangDiagnostic class]]) {
    bdrw_create_buffer_handle(handle, /* ReportType.AppNotResponding */ 1, SDK_ID, cstring_from(sdk_version));
    MXHangDiagnostic *hang = (MXHangDiagnostic *)event;
    NSMeasurementFormatter *formatter = [NSMeasurementFormatter new];
    NSString *duration = [formatter stringFromMeasurement:hang.hangDuration];
    NSString *reason = [NSString stringWithFormat:@"app was unresponsive for %@", duration];
    NSDictionary *representation = event.dictionaryRepresentation;
    NSString *name = representation[@"hangType"] ?: DEFAULT_HANG_NAME;
    serialize_error_threads(handle, representation, name, reason, FrameOrderOuterToInner);
  } else {
    return false;
  }
  NSDictionary *metadata = event.metaData.dictionaryRepresentation;
  serialize_app_metrics(handle, event.applicationVersion, metadata);
  serialize_device_metrics(handle, metadata, timestamp);
  return true;
}

#define print_case(name) case name: return @#name
static NSString *name_for_signal(NSNumber *signal) {
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

static NSString *name_for_crash(MXCrashDiagnostic *event) {
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
      return name_for_signal(event.signal);
  }
}
#undef print_case

static NSString *reason_for_crash(MXCrashDiagnostic *event, NSString *name) {
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
  if (event.exceptionCode.longValue) {
    return [NSString stringWithFormat:@"code: %ld, signal: %@",
            event.exceptionCode.longValue,
            name_for_signal(event.signal)];
  }

  NSString *reason = [NSString stringWithFormat:@"%@", name_for_signal(event.signal)];
  return [reason isEqualToString:name] ? nil : reason;
}
