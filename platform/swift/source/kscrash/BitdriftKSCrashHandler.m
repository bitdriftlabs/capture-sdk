// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

#include "BitdriftKSCrashHandler.h"

#include "KSCrashMonitor.h"
#include "KSCrashMonitor_CPPException.h"
#include "KSCrashMonitor_NSException.h"
#include "KSCrashMonitor_Signal.h"
#include "KSCrashMonitor_MachException.h"
#include "KSBinaryImageCache.h"
#include "KSThreadCache.h"
#include "ReportWriter.h"
#include "ReportContext.h"
#include "ReportReader.h"

#import <UIKit/UIKit.h>
#include <stdatomic.h>
#include <string.h>
#include <sys/sysctl.h>
#include <sys/utsname.h>

// Temporary fix for this dependency in KSCrashMonitor_Signal to KSCrashMonitor_Memory
void ksmemory_notifyUnhandledFatalSignal(void) {}

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

#define KEY_NAME @"name"
#define KEY_STACK_ADDRESSES @"stackAddresses"
#define KEY_COUNT @"count"

static NSString* sysctl_nsstring(const char* name) {
    NSString *str = nil;
    size_t requiredSize = 0;
    char* buff = NULL;

    sysctlbyname(name, NULL, &requiredSize, NULL, 0);
    if(requiredSize == 0) {
        goto done;
    }
    buff = (char*)malloc(requiredSize+1);
    if(sysctlbyname(name, buff, &requiredSize, NULL, 0) != 0) {
        goto done;
    }
    buff[requiredSize] = 0;
    str = [NSString stringWithUTF8String:buff];

done:
    free(buff);
    return str;
}

static NSString *getRegionCode(void) {
    if (@available(iOS 17.0, *)) {
        return NSLocale.currentLocale.regionCode;
    } else {
        return NSLocale.currentLocale.countryCode;
    }
}

static NSString *getOsBuild(void) {
    @try {
        NSString *str = NSProcessInfo.processInfo.operatingSystemVersionString;
        NSRegularExpression *exp = [[NSRegularExpression alloc] initWithPattern:@"\\(Build (.*)\\)" options:0 error:nil];
        NSTextCheckingResult *matched = [exp firstMatchInString:str options:0 range:NSMakeRange(0, str.length)];
        NSString* matchText = [str substringWithRange:[matched rangeAtIndex:1]];
        if(matchText.length > 0) {
            return matchText;
        }
    } @catch(NSException *) {
    }
    // This gives weird results on simulator and MacOS, but better than nothing
    return sysctl_nsstring("kern.osversion");
}

static void initReportContext(ReportContext* context, NSString *reportPath) {
    UIDevice *dev = UIDevice.currentDevice;
    NSProcessInfo *proc = NSProcessInfo.processInfo;
    struct utsname uts;
    uname(&uts);

    memset(context, 0, sizeof(*context));
    context->reportPath = strdup(reportPath.UTF8String);
    context->metadata.pid = proc.processIdentifier;
    context->metadata.deviceType = strdup(uts.machine);
    context->metadata.osVersion = strdup(dev.systemVersion.UTF8String);
    context->metadata.osBuild = strdup(getOsBuild().UTF8String);
    context->metadata.machine = strdup(dev.model.UTF8String);
    context->metadata.appBuildVersion = strdup([NSBundle.mainBundle.infoDictionary[@"CFBundleVersion"] UTF8String]);
    context->metadata.appVersion = strdup([NSBundle.mainBundle.infoDictionary[@"CFBundleShortVersionString"] UTF8String]);
    context->metadata.bundleIdentifier = strdup(NSBundle.mainBundle.bundleIdentifier.UTF8String);
    context->metadata.regionFormat = strdup(getRegionCode().UTF8String);
}

static ReportContext g_baseContext;

static void onCrash(struct KSCrash_MonitorContext *monitorContext)
{
    ReportContext context = g_baseContext;
    context.metadata.time = time(NULL);
    context.monitorContext = monitorContext;
    bitdrift_writeStandardReport(&context);
}

static bool startCrashHandler(NSString *reportPath) {
    static atomic_bool started = false;
    bool expectStarted = false;
    if (!atomic_compare_exchange_strong(&started, &expectStarted, true)) {
        return true; // Already started
    }
    
    ksbic_init();
    kstc_init(10);
    initReportContext(&g_baseContext, reportPath);
    kscm_setEventCallback(onCrash);

#define ERROR_IF_FALSE(A) do if(!(A)) { \
    NSLog(@"Error: Function returned unexpected false: %s", #A); \
    return false; \
} while(0)
    ERROR_IF_FALSE(kscm_addMonitor(kscm_cppexception_getAPI()));
    ERROR_IF_FALSE(kscm_addMonitor(kscm_machexception_getAPI()));
    ERROR_IF_FALSE(kscm_addMonitor(kscm_nsexception_getAPI()));
    ERROR_IF_FALSE(kscm_addMonitor(kscm_signal_getAPI()));
    ERROR_IF_FALSE(kscm_activateMonitors());
    return true;
}

static void printData(NSString *name, id report) {
    NSError *error;
    NSData *data = [NSJSONSerialization dataWithJSONObject:report options:NSJSONWritingPrettyPrinted error:&error];
    if(data == NULL) {
        NSLog(@"Error converting %@ data to JSON: %@", name, error);
    }
    write(1, name.UTF8String, name.length);
    write(1, "\n", 1);
    write(1, data.bytes, data.length);
    write(1, "\n", 1);
}

//@interface NamedThread: NSObject
//@property (nonnull, strong) NSString *name;
//@property (nonnull, strong) NSMutableArray<NSNumber *> *stackAddresses;
//@property (assign) NSInteger count;
//@end
//@implementation NamedThread
//- (instancetype)init {
//    if((self = [super init])) {
//        _stackAddresses = [NSMutableArray new];
//    }
//    return self;
//}
//@end
//

static bool diagnosticsMatch(NSDictionary *metricKitReport, NSDictionary *kscrashReport) {
    if(metricKitReport == nil) {
        NSLog(@"MetricKit report is nil");
        return false;
    }
    if(kscrashReport == nil) {
        NSLog(@"KSCrash report report is nil");
        return false;
    }

    NSArray *matchKeys = @[
        @"exceptionType",
        @"exceptionCode",
        @"signal",
        @"pid",
    ];

    NSString *diagnosticMetadataString = @"diagnosticMetaData";

    NSDictionary *mkMetadata = metricKitReport[diagnosticMetadataString];
    NSDictionary *ksMetadata = kscrashReport[diagnosticMetadataString];

    for(NSString *key in matchKeys) {
        if (![mkMetadata[key] isEqual:ksMetadata[key]]) {
            return false;
        }
    }
    return true;
}

static NSMutableDictionary *namedThreadFromDict(NSDictionary *thread) {
    NSString *name = thread[@"name"];
    if(![name isKindOfClass:NSString.class]) {
        return nil;
    }
    NSArray *contents = thread[@"backtrace"][@"contents"];
    if(![contents isKindOfClass:NSArray.class]) {
        return nil;
    }
    NSMutableArray *stackAddresses = [NSMutableArray new];
    NSMutableDictionary *namedThread = [@{
        KEY_NAME: name,
        KEY_STACK_ADDRESSES: stackAddresses,
        KEY_COUNT: @1,
    } mutableCopy];
    for(NSDictionary *frame in contents) {
        if(![frame isKindOfClass:NSDictionary.class]) {
            return nil;
        }
        NSNumber *address = frame[@"address"];
        if(![address isKindOfClass:NSNumber.class]) {
            return nil;
        }
        [stackAddresses addObject:address];
    }
    return namedThread;
}

static void filterOutThreads(NSMutableArray *namedThreads, size_t frameIndex, size_t totalFrames, NSNumber *address) {
    // MetricKit always puts one extra frame.
    if(frameIndex+1 >= totalFrames) {
        return;
    }

    [namedThreads filterUsingPredicate:[NSPredicate predicateWithBlock:^BOOL(id  _Nullable evaluatedObject, NSDictionary<NSString *,id> * _Nullable bindings) {
        NSDictionary *thread = evaluatedObject;
        int count = [thread[KEY_COUNT] intValue];
        NSArray<NSNumber *> *stackAddresses = thread[KEY_STACK_ADDRESSES];
        if(count < 1) {
            return false;
        }
        if (frameIndex >= stackAddresses.count) {
            return false;
        }
        return [stackAddresses[frameIndex] isEqualToNumber:address];
    }]];
}

static bool stacksAreEqual(NSArray *a, NSArray *b) {
    if (a.count != b.count) {
        return false;
    }

    for(NSUInteger i = 0; i < a.count; i++) {
        if(![a[i] isEqual:b[i]]) {
            return false;
        }
    }
    return true;
}

static NSMutableDictionary *existingNamedThread(NSMutableDictionary *namedThread, NSArray<NSMutableDictionary *> *list)
{
    NSString *thisName = namedThread[KEY_NAME];
    NSArray *thisStack = namedThread[KEY_STACK_ADDRESSES];

    for(NSMutableDictionary *entry in list) {
        if([thisName isEqualToString:entry[KEY_NAME]] &&
           stacksAreEqual(thisStack, entry[KEY_STACK_ADDRESSES])) {
            return entry;
        }
    }
    return nil;
}

static NSArray *namedThreadsFromReport(NSDictionary *metricKitReport, NSDictionary *kscrashReport) {
    if(!diagnosticsMatch(metricKitReport, kscrashReport)) {
        return @[];
    }

    NSArray *threads = kscrashReport[@"threads"];
    if (![threads isKindOfClass:NSArray.class]) {
        return @[];
    }
    NSMutableArray *result = [NSMutableArray new];
    for (NSDictionary *thread in threads) {
        NSMutableDictionary *namedThread = namedThreadFromDict(thread);
        if(namedThread != nil) {
            NSMutableDictionary *existing = existingNamedThread(namedThread, result);
            if(existing != nil) {
                existing[KEY_COUNT] = @([existing[KEY_COUNT] intValue] + 1);
            } else {
                [result addObject:namedThread];
            }
        }
    }
    return result;
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

static id replaceMutableKey(NSMutableDictionary *dict, NSString *key, Class expectedClass) {
    id obj = dict[key];
    if (![obj isKindOfClass:expectedClass]) {
        return nil;
    }
    id mutable = [obj mutableCopy];
    dict[key] = mutable;
    return mutable;
}

static id replaceMutableIndex(NSMutableArray *array, NSUInteger index, Class expectedClass) {
    id obj = array[index];
    if (![obj isKindOfClass:expectedClass]) {
        return nil;
    }
    id mutable = [obj mutableCopy];
    array[index] = mutable;
    return mutable;
}

static NSDictionary *enhancedMetricKitReport(NSDictionary *metrickitReport, NSDictionary *kscrashReport) {
    NSArray *namedThreads = namedThreadsFromReport(metrickitReport, kscrashReport);
    NSMutableDictionary *report = [metrickitReport mutableCopy];
    NSMutableDictionary *callStackTree = replaceMutableKey(report, @"callStackTree", NSDictionary.class);
    NSMutableArray *callStacks = replaceMutableKey(callStackTree, @"callStacks", NSArray.class);

    for (uint32_t thread_index = 0; thread_index < callStacks.count; thread_index++) {
        NSMutableDictionary *thread = replaceMutableIndex(callStacks, thread_index, NSDictionary.class);
        NSDictionary *frame = thread_root_frame(thread);
        uint64_t frame_count = count_frames(frame);
        if (frame_count == 0) {
            continue;
        }

        NSMutableArray *workingNamedThreads = [namedThreads mutableCopy];

        uint32_t frame_index = 0;
        while ([frame isKindOfClass:[NSDictionary class]] && frame_index < frame_count) {
            NSNumber *address = number_for_key(frame, @"address");
            filterOutThreads(workingNamedThreads, frame_index, frame_count, address);
            frame = [array_for_key(frame, @"subFrames") firstObject];
            frame_index++;
        }
        if(workingNamedThreads.count > 0 && frame_count > 1) {
            NSMutableDictionary *namedThread = workingNamedThreads[0];
            int count = [namedThread[KEY_COUNT] intValue];
            namedThread[KEY_COUNT] = @(count - 1);
            thread[@"name"] = namedThread[KEY_NAME];
        }
    }
    return report;
}

@implementation BitdriftKSCrashHandler

static NSString *g_reportPath;
static NSDictionary *g_lastReport;

+ (bool)configureWithBasePath:(NSURL *)basePath {
    NSString *path = [NSString stringWithUTF8String:basePath.fileSystemRepresentation];
    path = [path stringByAppendingPathComponent:@"kscrash"];

    NSError *error = nil;
    if (![NSFileManager.defaultManager createDirectoryAtPath:path
                                  withIntermediateDirectories:YES
                                                   attributes:nil
                                                        error:&error]) {
        NSLog(@"Error: Could not create directory \"%@\": %@", path, error);
        return false;
    }

    g_reportPath = [path stringByAppendingPathComponent:@"lastCrash.bjn"];

    NSMutableDictionary *report = bitdrift_readReport(g_reportPath);
    [NSFileManager.defaultManager removeItemAtPath:g_reportPath error:nil];
    [self fixupReport:report];
    g_lastReport = report;

    return true;
}

+ (void) fixupReport:(NSMutableDictionary*) report {
    NSMutableDictionary* diagnosticMetadata = report[@"diagnosticMetaData"];
    NSNumber* secsSince1970 = diagnosticMetadata[@"crashedAt"];
    if(secsSince1970 != nil) {
        NSDate *asDate = [NSDate dateWithTimeIntervalSince1970:secsSince1970.longLongValue];
        NSISO8601DateFormatOptions opts = NSISO8601DateFormatWithInternetDateTime |
                                            NSISO8601DateFormatWithDashSeparatorInDate |
                                            NSISO8601DateFormatWithColonSeparatorInTime |
                                            NSISO8601DateFormatWithTimeZone;
        NSString *asString = [NSISO8601DateFormatter stringFromDate:asDate
                                                           timeZone:[NSTimeZone timeZoneWithAbbreviation:@"UTC"]
                                                      formatOptions:opts];
        diagnosticMetadata[@"crashedAt"] = asString;
    }
}

+ (NSDictionary *)enhancedMetricKitReport:(NSDictionary *)metricKitReport {
    if(metricKitReport == nil || g_lastReport == nil) {
        return metricKitReport;
    }
    return enhancedMetricKitReport(metricKitReport, g_lastReport);
}

+ (bool)start {
    return startCrashHandler(g_reportPath);
}

+ (void)stop {
    kscm_disableAllMonitors();
}

@end
