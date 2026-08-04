// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

#import "MetricKitReportWriter.h"
#import "BitdriftCrashHandler.h"

#import <signal.h>

@implementation MetricKitReportFrame

- (instancetype)initWithAddress:(uint64_t)address
                      binaryUUID:(NSString *)binaryUUID
                      binaryName:(NSString *)binaryName
    offsetIntoBinaryTextSegment:(uint64_t)offsetIntoBinaryTextSegment {
  if (self = [super init]) {
    _address = address;
    _binaryUUID = [binaryUUID copy];
    _binaryName = [binaryName copy];
    _offsetIntoBinaryTextSegment = offsetIntoBinaryTextSegment;
  }
  return self;
}

@end

@implementation MetricKitReportThread

- (instancetype)initWithName:(NSString *)name attributed:(BOOL)attributed frames:(NSArray<MetricKitReportFrame *> *)frames {
  if (self = [super init]) {
    _name = [name copy];
    _attributed = attributed;
    _frames = [frames copy];
  }
  return self;
}

@end

@implementation MetricKitReportEnvironment

- (instancetype)initWithBundleIdentifier:(NSString *)bundleIdentifier
                       applicationVersion:(NSString *)applicationVersion
                  applicationBuildVersion:(NSString *)applicationBuildVersion
                             regionFormat:(NSString *)regionFormat
                               deviceType:(NSString *)deviceType
                            osVersionName:(NSString *)osVersionName
                          osVersionNumber:(NSString *)osVersionNumber
                     osVersionBuildNumber:(NSString *)osVersionBuildNumber
                     platformArchitecture:(NSString *)platformArchitecture
                      lowPowerModeEnabled:(BOOL)lowPowerModeEnabled {
  if (self = [super init]) {
    _bundleIdentifier = [bundleIdentifier copy];
    _applicationVersion = [applicationVersion copy];
    _applicationBuildVersion = [applicationBuildVersion copy];
    _regionFormat = [regionFormat copy];
    _deviceType = [deviceType copy];
    _osVersionName = [osVersionName copy];
    _osVersionNumber = [osVersionNumber copy];
    _osVersionBuildNumber = [osVersionBuildNumber copy];
    _platformArchitecture = [platformArchitecture copy];
    _lowPowerModeEnabled = lowPowerModeEnabled;
  }
  return self;
}

@end

@interface MetricKitReportWriter ()
@property (nonnull, strong) NSURL *outputDir;
@property (nonnull, strong) NSString *sdkVersion;
@property BOOL fileSizeOptimizationEnabled;
@property CAPMemoryPressureLevel memoryPressureLevel;
@property (nonnull, strong) NSFileManager *fileManager;
@property (nonnull, strong) MetricKitDiagnosticParsing *parsing;
@end

@implementation MetricKitReportWriter

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
                          threads:(NSArray<MetricKitReportThread *> *)threads
                             name:(NSString *)name
                           reason:(NSString *)reason
                machExceptionType:(NSNumber *)machExceptionType
                    exceptionCode:(NSNumber *)exceptionCode
                           signal:(NSNumber *)signal
                terminationReason:(NSString *)terminationReason
                    capturedCrash:(BitdriftPreviousCrash *)capturedCrash
                      environment:(MetricKitReportEnvironment *)environment
                        timestamp:(NSTimeInterval)timestamp {
  const void *handle = 0;
  bdrw_create_buffer_handle(&handle, reportType, SDK_ID, cstring_from(self.sdkVersion), self.fileSizeOptimizationEnabled);

  [self serializeErrorThreads:&handle threads:threads name:name reason:reason order:FrameOrderInnerToOuter];
  [self serializeCrashInfoWithHandle:&handle
                              threads:threads
                    machExceptionType:machExceptionType
                        exceptionCode:exceptionCode
                               signal:signal
                    terminationReason:terminationReason
                        capturedCrash:capturedCrash
                           metricTime:timestamp];
  [self serializeAppMetrics:&handle environment:environment];
  [self serializeDeviceMetrics:&handle environment:environment timestamp:timestamp];
  [self finishReport:&handle reportType:reportType timestamp:timestamp];
}

- (void)writeMemoryExceptionReportWithThreads:(NSArray<MetricKitReportThread *> *)threads
                                          name:(NSString *)name
                                        reason:(NSString *)reason
                                   environment:(MetricKitReportEnvironment *)environment
                                     timestamp:(NSTimeInterval)timestamp {
  const void *handle = 0;
  bdrw_create_buffer_handle(&handle, ReportTypeNativeCrash, SDK_ID, cstring_from(self.sdkVersion), self.fileSizeOptimizationEnabled);

  [self serializeErrorThreads:&handle threads:threads name:name reason:reason order:FrameOrderOuterToInner];
  [self serializeAppMetrics:&handle environment:environment];
  [self serializeDeviceMetrics:&handle environment:environment timestamp:timestamp];
  [self finishReport:&handle reportType:ReportTypeNativeCrash timestamp:timestamp];
}

// MARK: - Threads + error

- (uint32_t)crashedThreadIndexInThreads:(NSArray<MetricKitReportThread *> *)threads {
  NSInteger attributedIndex = NSNotFound;
  for (NSUInteger index = 0; index < threads.count; index++) {
    if (threads[index].attributed) {
      attributedIndex = (NSInteger)index;
      break;
    }
  }
  if (attributedIndex != NSNotFound && threads[(NSUInteger)attributedIndex].frames.count > 0) {
    return (uint32_t)attributedIndex;
  }

  for (NSUInteger index = 0; index < threads.count; index++) {
    if (threads[index].frames.count > 0) {
      return (uint32_t)index;
    }
  }

  return 0;
}

- (void)serializeErrorThreads:(BDProcessorHandle)handle
                       threads:(NSArray<MetricKitReportThread *> *)threads
                          name:(NSString *)name
                        reason:(NSString *)reason
                         order:(FrameOrder)order {
  NSMutableSet<NSString *> *registeredImages = [NSMutableSet new];
  uint32_t crashedIndex = [self crashedThreadIndexInThreads:threads];

  for (uint32_t threadIndex = 0; threadIndex < threads.count; threadIndex++) {
    MetricKitReportThread *thread = threads[threadIndex];
    if (thread.frames.count == 0 && threadIndex != crashedIndex) {
      continue;
    }

    uint32_t frameCount = (uint32_t)thread.frames.count;
    BDStackFrame *stack = frameCount ? (BDStackFrame *)calloc(frameCount, sizeof(BDStackFrame)) : 0;

    for (uint32_t frameIndex = 0; frameIndex < frameCount; frameIndex++) {
      MetricKitReportFrame *frame = thread.frames[frameIndex];
      if (![registeredImages containsObject:frame.binaryUUID]) {
        BDBinaryImage image = {
          .id = cstring_from(frame.binaryUUID),
          .path = cstring_from(frame.binaryName),
          .load_address = frame.address - frame.offsetIntoBinaryTextSegment,
        };
        bdrw_add_binary_image(handle, &image);
        [registeredImages addObject:frame.binaryUUID];
      }

      // Handle differing frame ordering for MXDiagnostic types (FB18377370): insertion order is
      // most recent to oldest.
      uint64_t insertAt = order == FrameOrderInnerToOuter ? frameIndex : (frameCount - frameIndex - 1);
      stack[insertAt] = (BDStackFrame) {
        .image_id = cstring_from(frame.binaryUUID),
        .frame_address = frame.address,
        .type_ = 2, // FrameType.DWARF
      };
    }

    BDThread bdthread = {
      .index = threadIndex,
      .quality_of_service = -1,
      .name = cstring_from(thread.name),
      .active = (threadIndex == crashedIndex),
    };
    bdrw_add_thread(handle, (uint16_t)threads.count, &bdthread, frameCount, stack);
    if (threadIndex == crashedIndex) {
      bdrw_add_error(handle, cstring_from(name), cstring_from(reason), 0, frameCount, stack);
    }
    free(stack);
  }

  if (threads.count == 0) {
    bdrw_add_error(handle, cstring_from(name), cstring_from(reason), 0, 0, 0);
  }
}

// MARK: - BDAppleCrashInfo (MetricKit side + bitdrift in-process side)

- (void)serializeCrashInfoWithHandle:(BDProcessorHandle)handle
                              threads:(NSArray<MetricKitReportThread *> *)threads
                    machExceptionType:(NSNumber *)machExceptionType
                        exceptionCode:(NSNumber *)exceptionCode
                               signal:(NSNumber *)signal
                    terminationReason:(NSString *)terminationReason
                        capturedCrash:(BitdriftPreviousCrash *)capturedCrash
                           metricTime:(NSTimeInterval)metricTime {
  BDCrashInfoThreadDetailsStorage metricKitThreadDetails = [self buildCrashInfoThreadDetails:threads];
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

- (BDCrashInfoThreadDetailsStorage)buildCrashInfoThreadDetails:(NSArray<MetricKitReportThread *> *)threads {
  if (threads.count == 0) {
    return empty_crash_info_thread_details_storage();
  }

  uint32_t crashedIndex = [self crashedThreadIndexInThreads:threads];
  BDCrashInfoThread *entries = (BDCrashInfoThread *)calloc(threads.count, sizeof(BDCrashInfoThread));
  uint16_t threadCount = 0;

  for (uint32_t threadIndex = 0; threadIndex < threads.count; threadIndex++) {
    MetricKitReportThread *thread = threads[threadIndex];
    if (thread.frames.count == 0) {
      // Unlike serializeErrorThreads:, there's no crashed-thread exception here: this data isn't
      // used to report an error, so an empty crashed thread just doesn't contribute frames.
      continue;
    }

    // Binary images aren't registered here: this always runs right after serializeErrorThreads:
    // over the same threads, which already registered every image in this tree.
    uint32_t frameCount = (uint32_t)thread.frames.count;
    BDStackFrame *stack = (BDStackFrame *)calloc(frameCount, sizeof(BDStackFrame));
    for (uint32_t frameIndex = 0; frameIndex < frameCount; frameIndex++) {
      MetricKitReportFrame *frame = thread.frames[frameIndex];
      stack[frameIndex] = (BDStackFrame) {
        .image_id = cstring_from(frame.binaryUUID),
        .frame_address = frame.address,
        .type_ = 2,
      };
    }

    entries[threadCount++] = (BDCrashInfoThread) {
      .thread = (BDThread){
        .index = threadIndex,
        .quality_of_service = -1,
        .name = cstring_from(thread.name),
        .active = (threadIndex == crashedIndex),
      },
      .stack_count = frameCount,
      .stack = stack,
    };
  }

  if (threadCount == 0) {
    free(entries);
    return empty_crash_info_thread_details_storage();
  }

  return (BDCrashInfoThreadDetailsStorage){
    .details = (BDCrashInfoThreadDetails){
      .count = (uint16_t)threads.count,
      .threads_count = threadCount,
      .threads = entries,
    },
    .threads = entries,
  };
}

- (BDCrashInfoThreadDetailsStorage)buildCrashInfoThreadDetailsFromCapturedFrames:(NSArray<BitdriftCrashStackFrame *> *)frames {
  if (frames.count == 0) {
    return empty_crash_info_thread_details_storage();
  }

  BDCrashInfoThread *entries = (BDCrashInfoThread *)calloc(1, sizeof(BDCrashInfoThread));
  BDStackFrame *stack = (BDStackFrame *)calloc(frames.count, sizeof(BDStackFrame));
  uint32_t frameCount = 0;

  for (BitdriftCrashStackFrame *frame in frames) {
    if (frame.imageID == nil) {
      continue;
    }
    stack[frameCount++] = (BDStackFrame) {
      .type_ = 2,
      .frame_address = frame.frameAddress,
      .image_id = cstring_from(frame.imageID),
    };
  }

  if (frameCount == 0) {
    free(stack);
    free(entries);
    return empty_crash_info_thread_details_storage();
  }

  entries[0] = (BDCrashInfoThread) {
    .thread = (BDThread){ .index = 0, .quality_of_service = -1, .active = true },
    .stack_count = frameCount,
    .stack = stack,
  };
  return (BDCrashInfoThreadDetailsStorage){
    .details = (BDCrashInfoThreadDetails){ .count = 1, .threads_count = 1, .threads = entries },
    .threads = entries,
  };
}

- (void)addCapturedCrashBinaryImages:(BDProcessorHandle)handle frames:(NSArray<BitdriftCrashStackFrame *> *)frames {
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
  for (uintptr_t threadIndex = 0; threadIndex < threadDetails.details.threads_count; threadIndex++) {
    free((void *)threadDetails.threads[threadIndex].stack);
  }
  free(threadDetails.threads);
}

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

- (void)serializeAppMetrics:(BDProcessorHandle)handle environment:(MetricKitReportEnvironment *)environment {
  NSString *bundleVersion = [NSString stringWithFormat:@"%@.%@", environment.applicationVersion, environment.applicationBuildVersion];
  BDAppMetrics app = {
    .app_id = cstring_from(environment.bundleIdentifier),
    .version = cstring_from(environment.applicationVersion),
    .version_code = 0,
    .cf_bundle_version = cstring_from(bundleVersion),
    .memory_pressure_level = self.memoryPressureLevel,
    .region_format = cstring_from(environment.regionFormat),
  };
  bdrw_add_app(handle, &app);
}

- (void)serializeDeviceMetrics:(BDProcessorHandle)handle
                    environment:(MetricKitReportEnvironment *)environment
                      timestamp:(NSTimeInterval)timestamp {
  uint64_t seconds = 0;
  uint32_t nanos = 0;
  [self.parsing timestampComponentsFor:timestamp seconds:&seconds nanos:&nanos];

  BDDeviceMetrics device = {
    .model = cstring_from(environment.deviceType),
    .architecture = [self.parsing architectureConstantFor:environment.platformArchitecture],
    .os_kernversion = cstring_from(environment.osVersionBuildNumber),
    .os_version = cstring_from(environment.osVersionNumber),
    .os_brand = cstring_from(environment.osVersionName),
    .time_nanos = nanos,
    .time_seconds = seconds,
    .low_power_mode_enabled = environment.lowPowerModeEnabled,
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
