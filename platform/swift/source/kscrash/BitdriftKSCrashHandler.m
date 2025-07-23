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


#pragma mark Crash Handling

static ReportContext g_crashHandlerReportContext;
static atomic_bool g_crashHandlerIsRunning = false;

static void onCrash(struct KSCrash_MonitorContext *monitorContext) {
    g_crashHandlerReportContext.metadata.time = time(NULL);
    g_crashHandlerReportContext.monitorContext = monitorContext;
    bitdrift_writeKSCrashReport(&g_crashHandlerReportContext);
}

static bool startCrashHandler(NSString *reportPath) {
    bool expectStarted = false;
    if (!atomic_compare_exchange_strong(&g_crashHandlerIsRunning, &expectStarted, true)) {
        return true; // Already started
    }

    memset(&g_crashHandlerReportContext, 0, sizeof(g_crashHandlerReportContext));
    g_crashHandlerReportContext.reportPath = strdup(reportPath.UTF8String);
    g_crashHandlerReportContext.metadata.pid = NSProcessInfo.processInfo.processIdentifier;

    const int threadCachePollIntervalSecs = 10;
    ksbic_init();
    kstc_init(threadCachePollIntervalSecs);
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
#undef ERROR_IF_FALSE
    return true;
}

static void stopCrashHandler(void) {
    kscm_disableAllMonitors();
    g_crashHandlerIsRunning = false;
}

#pragma mark Report Enhancement

// Used for debugging
//static void printData(NSString *name, id report) {
//    NSError *error;
//    NSData *data = [NSJSONSerialization dataWithJSONObject:report options:NSJSONWritingPrettyPrinted error:&error];
//    if(data == NULL) {
//        NSLog(@"Error converting %@ data to JSON: %@", name, error);
//    }
//    write(1, name.UTF8String, name.length);
//    write(1, "\n", 1);
//    write(1, data.bytes, data.length);
//    write(1, "\n", 1);
//}

#define KEY_NAME @"name"
#define KEY_STACK_ADDRESSES @"stackAddresses"
#define KEY_COUNT @"count"

static id object_for_key(NSDictionary *dict, NSString *key, Class klass) {
    if ([dict isKindOfClass:[NSDictionary class]]) {
        id value = dict[key];
        return [value isKindOfClass:klass] ? value : nil;
    }
    return nil;
}

#define stringForKey(dict, key) object_for_key(dict, key, [NSString class])
#define numberForKey(dict, key) object_for_key(dict, key, [NSNumber class])
#define arrayForKey(dict, key) object_for_key(dict, key, [NSArray class])
#define dictForKey(dict, key) object_for_key(dict, key, [NSDictionary class])
#define mutDictForKey(dict, key) object_for_key(dict, key, [NSMutableDictionary class])

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

static bool diagnosticMetadataMatches(NSDictionary *metricKitReport, NSDictionary *kscrashReport) {
    NSArray *matchKeys = @[
        @"exceptionType",
        @"exceptionCode",
        @"signal",
        @"pid",
    ];

    NSString *diagnosticMetadataString = @"diagnosticMetaData";

    NSDictionary *mkMetadata = dictForKey(metricKitReport, diagnosticMetadataString);
    NSDictionary *ksMetadata = dictForKey(kscrashReport, diagnosticMetadataString);

    for(NSString *key in matchKeys) {
        if (![mkMetadata[key] isEqual:ksMetadata[key]]) {
            return false;
        }
    }
    return true;
}

static NSMutableDictionary *namedThreadFromDict(NSDictionary *thread) {
    NSString *name = stringForKey(thread, @"name");
    if(name == nil) {
        return nil;
    }
    NSDictionary *backtrace = dictForKey(thread, @"backtrace");
    NSArray *contents = arrayForKey(backtrace, @"contents");
    if(contents == nil) {
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
        NSNumber *address = numberForKey(frame, @"address");
        if(address == nil) {
            return nil;
        }
        [stackAddresses addObject:address];
    }
    return namedThread;
}

static void filterOutThreadsNotMatchingAddress(NSMutableArray *namedThreads,
                                               size_t frameIndex,
                                               size_t totalFrames,
                                               NSNumber *address) {
    // MetricKit always puts one extra frame on each thread, so we skip it.
    if(frameIndex+1 >= totalFrames) {
        return;
    }

    [namedThreads filterUsingPredicate:[NSPredicate predicateWithBlock:^BOOL(id  _Nullable evaluatedObject, NSDictionary<NSString *,id> * _Nullable bindings) {
        NSDictionary *thread = evaluatedObject;
        int count = [numberForKey(thread, KEY_COUNT) intValue];
        NSArray<NSNumber *> *stackAddresses = arrayForKey(thread, KEY_STACK_ADDRESSES);
        if(count < 1) {
            return false;
        }
        if (frameIndex >= stackAddresses.count) {
            return false;
        }
        return [stackAddresses[frameIndex] isEqual:address];
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

static NSMutableDictionary *existingNamedThread(NSMutableDictionary *namedThread,
                                                NSArray<NSMutableDictionary *> *existingList) {
    NSString *thisName = stringForKey(namedThread, KEY_NAME);
    NSArray *thisStack = arrayForKey(namedThread, KEY_STACK_ADDRESSES);

    for(NSMutableDictionary *entry in existingList) {
        if([thisName isEqualToString:stringForKey(entry, KEY_NAME)] &&
           stacksAreEqual(thisStack, arrayForKey(entry, KEY_STACK_ADDRESSES))) {
            return entry;
        }
    }
    return nil;
}

static NSArray *namedThreadsFromReport(NSDictionary *metricKitReport, NSDictionary *kscrashReport) {
    if(!diagnosticMetadataMatches(metricKitReport, kscrashReport)) {
        return @[];
    }

    NSArray *threads = arrayForKey(kscrashReport, @"threads");
    if (threads == nil) {
        return @[];
    }
    NSMutableArray *result = [NSMutableArray new];
    for (NSDictionary *thread in threads) {
        NSMutableDictionary *namedThread = namedThreadFromDict(thread);
        if(namedThread != nil) {
            NSMutableDictionary *existing = existingNamedThread(namedThread, result);
            if(existing != nil) {
                existing[KEY_COUNT] = @([numberForKey(existing, KEY_COUNT) intValue] + 1);
            } else {
                [result addObject:namedThread];
            }
        }
    }
    return result;
}

static NSDictionary *threadRootFrame(NSDictionary *thread) {
  return [arrayForKey(thread, @"callStackRootFrames") firstObject];
}

static uint64_t frameCount(NSDictionary *rootFrame) {
  uint64_t count = 0;
  NSDictionary *frame = rootFrame;
  while ([frame isKindOfClass:[NSDictionary class]]) {
    count++;
    frame = [arrayForKey(frame, @"subFrames") firstObject];
  }
  return count;
}

static NSDictionary *enhancedMetricKitReport(NSDictionary *metrickitReport, NSDictionary *kscrashReport) {
    NSArray *namedThreads = namedThreadsFromReport(metrickitReport, kscrashReport);
    NSMutableDictionary *report = [metrickitReport mutableCopy];
    NSMutableDictionary *callStackTree = replaceMutableKey(report, @"callStackTree", NSDictionary.class);
    NSMutableArray *callStacks = replaceMutableKey(callStackTree, @"callStacks", NSArray.class);

    for (uint32_t thread_index = 0; thread_index < callStacks.count; thread_index++) {
        NSMutableDictionary *thread = replaceMutableIndex(callStacks, thread_index, NSDictionary.class);
        NSDictionary *frame = threadRootFrame(thread);
        uint64_t frame_count = frameCount(frame);
        if (frame_count == 0) {
            continue;
        }

        NSMutableArray *workingNamedThreads = [namedThreads mutableCopy];

        uint32_t frame_index = 0;
        while ([frame isKindOfClass:[NSDictionary class]] && frame_index < frame_count) {
            NSNumber *address = numberForKey(frame, @"address");
            filterOutThreadsNotMatchingAddress(workingNamedThreads, frame_index, frame_count, address);
            frame = [arrayForKey(frame, @"subFrames") firstObject];
            frame_index++;
        }
        if(workingNamedThreads.count > 0 && frame_count > 1) {
            NSMutableDictionary *namedThread = workingNamedThreads[0];
            int count = [numberForKey(namedThread, KEY_COUNT) intValue];
            namedThread[KEY_COUNT] = @(count - 1);
            thread[@"name"] = stringForKey(namedThread, KEY_NAME);
        }
    }
    return report;
}


#pragma mark API

@implementation BitdriftKSCrashHandler

static NSString *g_kscrashReportPath;
static NSDictionary *g_lastKSCrashReport;

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

    g_kscrashReportPath = [path stringByAppendingPathComponent:@"lastCrash.bjn"];

    NSMutableDictionary *report = bitdrift_readKSCrashReport(g_kscrashReportPath);
    [NSFileManager.defaultManager removeItemAtPath:g_kscrashReportPath error:nil];
    [self fixupKSCrashReport:report];
    g_lastKSCrashReport = report;

    return true;
}

+ (bool)startCrashReporter {
    return startCrashHandler(g_kscrashReportPath);
}

+ (void)stopCrashReporter {
    stopCrashHandler();
}

+ (void) fixupKSCrashReport:(NSMutableDictionary*) report {
    NSMutableDictionary* diagnosticMetadata = mutDictForKey(report, @"diagnosticMetaData");
    NSNumber* secsSince1970 = numberForKey(diagnosticMetadata, @"crashedAt");
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

+ (NSDictionary<NSString *, id> *)enhancedMetricKitReport:(NSDictionary<NSString *, id> *)metricKitReport {
    @try {
        NSDictionary *kscrashReport = g_lastKSCrashReport;
        if(metricKitReport == nil || kscrashReport == nil) {
            return metricKitReport;
        }
        return enhancedMetricKitReport(metricKitReport, kscrashReport);
    } @catch (NSException *exception) {
        NSLog(@"Error: enhancedMetricKitReport() threw exception %@. Returning original report.", exception);
        return metricKitReport;
    }
}

@end
