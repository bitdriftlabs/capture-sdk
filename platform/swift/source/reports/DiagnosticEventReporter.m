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
static NSString *const OS_VERSION_MATCHER = @"^(?<osName>.*)\\s+(?<osVersion>\\d+\\.\\d+\\.\\d+)\\s+\\((?<buildNumber>.*)\\)$";
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

@interface DiagnosticEventReporter ()
@property (nonnull, strong) NSURL *dir;
@property (nonnull, strong) NSString *sdkVersion;
@end

@implementation DiagnosticEventReporter
- (instancetype _Nonnull)initWithOutputDir:(NSURL *_Nonnull)dir
                                sdkVersion:(NSString *_Nonnull)sdkVersion {
  if (self = [super init]) {
    self.dir = dir;
    self.sdkVersion = sdkVersion;
  }
  return self;
}

- (void)didReceiveDiagnosticPayloads:(NSArray<MXDiagnosticPayload *> * _Nonnull)payloads API_AVAILABLE(ios(14.0), macos(12.0)) {
  NSFileManager *fileManager = [NSFileManager defaultManager];
  if (![fileManager createDirectoryAtPath:[self.dir path] withIntermediateDirectories:YES attributes:0 error:nil]) {
    return;
  }

  const void *handle = 0;
  for (MXDiagnosticPayload *payload in payloads) {
    NSTimeInterval timestamp = [payload.timeStampEnd timeIntervalSince1970];
    for (MXDiagnostic *event in payload.crashDiagnostics) {
      if (serialize_diagnostic(&handle, self.sdkVersion, event, timestamp) && handle != 0) {
        uint64_t length = 0;
        const uint8_t *contents = bdrw_get_completed_buffer(&handle, &length);
        NSData *data = [NSData dataWithBytes:contents length:length];
        NSString *identifier = [[NSUUID UUID] UUIDString];
        NSString *filename = [NSString stringWithFormat:@"%Lf_%@.cap", truncl(timestamp), identifier];
        NSString *path = [[self.dir URLByAppendingPathComponent:filename] path];
        [fileManager createFileAtPath:path contents:data attributes:0];
        bdrw_dispose_buffer_handle(&handle);
      }
    }
  }
}
@end

static NSString *name_for_signal(NSNumber *signal);
static NSString *name_for_crash(MXCrashDiagnostic *event) API_AVAILABLE(ios(14.0), macos(12.0));
static NSString *reason_for_crash(MXCrashDiagnostic *event, NSString *name) API_AVAILABLE(ios(14.0), macos(12.0));

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

static uint64_t count_frames(NSDictionary *rootFrame) {
  uint64_t count = 0;
  NSDictionary *frame = rootFrame;
  while ([frame isKindOfClass:[NSDictionary class]]) {
    count++;
    frame = [array_for_key(frame, @"subFrames") firstObject];
  }
  return count;
}

static void serialize_error_threads(BDProcessorHandle handle, NSDictionary *crash, NSString *name, NSString *reason) {
  NSMutableSet <NSString *>* images = [NSMutableSet new];
  NSArray *callStacks = dict_for_key(crash, @"callStackTree")[@"callStacks"];
  for (NSDictionary *thread in callStacks) {
    bool didCrash = [number_for_key(thread, @"threadAttributed") boolValue];
    NSDictionary *frame = [array_for_key(thread, @"callStackRootFrames") firstObject];
    uint64_t frameCount = count_frames(frame);
    BDStackFrame *stack = (BDStackFrame *)calloc(frameCount, sizeof(BDStackFrame));
    uint32_t index = 0;

    while ([frame isKindOfClass:[NSDictionary class]]) {
      NSString *binaryName = string_for_key(frame, @"binaryName");
      NSString *binaryUUID = string_for_key(frame, @"binaryUUID");
      NSNumber *address = number_for_key(frame, @"address");
      NSNumber *offset = number_for_key(frame, @"offsetIntoBinaryTextSegment");
      if (binaryName && binaryUUID && address && offset) {
        if (![images containsObject:binaryUUID]) {
          BDBinaryImage image = {
            .id = cstring_from(binaryUUID),
            .path = cstring_from(binaryName),
            .load_address = [address unsignedLongLongValue] - [offset unsignedLongLongValue],
          };
          bdrw_add_binary_image(handle, &image);
          [images addObject:binaryUUID];
        }
      } else {
        break; // if the frame is invalid, it's time to leave
      }
      stack[index++] = (BDStackFrame) {
        .image_id = cstring_from(binaryUUID),
        .frame_address = [address unsignedLongLongValue],
        .type_ = 2, // FrameType.DWARF
      };
      frame = [array_for_key(frame, @"subFrames") firstObject];
    }
    if (didCrash) {
      bdrw_add_error(handle, cstring_from(name), cstring_from(reason), 0, index, stack);
    } else {
      BDThread thread = { .quality_of_service = -1 };
      bdrw_add_thread(handle, [callStacks count], &thread, index, stack);
    }
    free(stack);
  }
}

static int8_t architecture_constant(NSString *arch) {
  if (!arch) {
    return /* Architecture.Unknown */ 0;
  }
  return [arch containsString:@"arm64"] ? /* Architecture.arm64 */ 2 : /* Architecture.x86_64 */ 4;
}

static void serialize_device_metrics(BDProcessorHandle handle, NSDictionary *metadata, NSTimeInterval timestamp) {
  NSRegularExpression *version_matcher = [NSRegularExpression regularExpressionWithPattern:OS_VERSION_MATCHER options:0 error:nil];
  NSString *version_info = string_for_key(metadata, @"osVersion");
  NSTextCheckingResult *match = [[version_matcher matchesInString:version_info options:0 range:NSMakeRange(0, version_info.length)] firstObject];
  NSString *os_name = match ? [version_info substringWithRange:[match rangeWithName:@"osName"]] : nil;
  NSString *os_version = match ? [version_info substringWithRange:[match rangeWithName:@"osVersion"]] : nil;
  NSString *os_kernversion = match ? [version_info substringWithRange:[match rangeWithName:@"buildNumber"]] : nil;
  long double seconds = truncl(timestamp);
  long double nanoseconds = (timestamp - seconds) * NSEC_PER_SEC;

  BDDeviceMetrics device = {
    .model = cstring_from(string_for_key(metadata, @"deviceType")),
    .architecture = architecture_constant(string_for_key(metadata, @"platformArchitecture")),
    .os_kernversion = cstring_from(os_kernversion),
    .os_version = cstring_from(os_version),
    .os_brand = cstring_from(os_name),
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
    serialize_error_threads(handle, event.dictionaryRepresentation, name, reason);
  } else if ([event isKindOfClass:[MXHangDiagnostic class]]) {
    bdrw_create_buffer_handle(handle, /* ReportType.AppNotResponding */ 1, SDK_ID, cstring_from(sdk_version));
    MXHangDiagnostic *hang = (MXHangDiagnostic *)event;
    NSString *reason = [NSString stringWithFormat:@"app was unresponsive for %@", hang.hangDuration];
    serialize_error_threads(handle, event.dictionaryRepresentation, DEFAULT_HANG_NAME, reason);
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
  if (@available(iOS 14, macOS 12, *)) {
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
  return nil;
}
#undef print_case

static NSString *reason_for_crash(MXCrashDiagnostic *event, NSString *name) {
  if (@available(iOS 17, macOS 14, *)) {
    if (event.exceptionReason) {
      return event.exceptionReason.composedMessage;
    }
  }
  if (event.terminationReason) {
    return event.terminationReason;
  }
  if (event.exceptionCode.longValue) {
    return [NSString stringWithFormat:@"code: %ld, signal: %@",
            event.exceptionCode.longValue,
            name_for_signal(event.signal)];
  }

  NSString *reason = [NSString stringWithFormat:@"%@", name_for_signal(event.signal)];
  return [reason isEqualToString:name] ? nil : reason;
}
