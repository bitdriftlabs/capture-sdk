// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

#import "BitdriftKSCrashHandler.h"

#import "KSCrashMonitor.h"
#import "KSCrashMonitor_CPPException.h"
#import "KSCrashMonitor_NSException.h"
#import "KSCrashMonitor_Signal.h"
#import "KSCrashMonitor_MachException.h"
#import "KSBinaryImageCache.h"
#import "KSThreadCache.h"
#import "ReportWriter.h"
#import "ReportContext.h"

#import <UIKit/UIKit.h>
#import <stdatomic.h>
#import <string.h>
#import <sys/sysctl.h>
#import <sys/utsname.h>

// Temporary fix for an internal dependency in KSCrashMonitor_Signal
// to KSCrashMonitor_Memory (whose code we don't want to include).
void ksmemory_notifyUnhandledFatalSignal(void) {}

#pragma mark Rust Bridge

typedef enum {
    /// The report was not cached due to an error
    CacheResultFailure = 0,
    /// The crash report file does not exist
    CacheResultReportDoesNotExist = 1,
    /// Successfully cached a partial document
    CacheResultPartialSuccess = 2,
    /// Successfully cached the complete document
    CacheResultSuccess = 3
} CacheResult;

/** Cache a KSCrash report, which will be used later for report enhancement. */
CacheResult capture_cache_kscrash_report(NSString *reportPath);

/** Enhance a MetricKit report using the cached KSCrash report. */
NSDictionary *_Nullable capture_enhance_metrickit_diagnostic_report(const NSDictionary *_Nullable report);

#pragma mark Crash Handling

static ReportContext g_crashHandlerReportContext;

static void onCrash(struct KSCrash_MonitorContext *monitorContext) {
    bool expectReceived = false;
    if (!atomic_compare_exchange_strong(&g_crashHandlerReportContext.hasReceivedCrashNotification,
                                        &expectReceived, true)) {
        // We only want to handle one crash. Don't write any reports if more come in.
        return;
    }

    g_crashHandlerReportContext.metadata.time = time(NULL);
    g_crashHandlerReportContext.monitorContext = monitorContext;
    bitdrift_writeKSCrashReport(&g_crashHandlerReportContext);
}

#pragma mark API

@interface BitdriftKSCrashHandler ()
@property(nonatomic, strong) NSString *kscrashReportFilePath;
@property(class, nonatomic, readonly, strong) BitdriftKSCrashHandler *sharedInstance;
@end

@implementation BitdriftKSCrashHandler

+ (instancetype)sharedInstance {
    static BitdriftKSCrashHandler *instance = nil;
    static dispatch_once_t onceToken;
    dispatch_once(&onceToken, ^{
        instance = [[self alloc] init];
    });
    return instance;
}

+ (BOOL)configureWithCrashReportDirectory:(NSURL *)crashReportPath {
    return [BitdriftKSCrashHandler.sharedInstance configureWithCrashReportDirectory:crashReportPath];
}

- (BOOL)configureWithCrashReportDirectory:(NSURL *)crashReportDir {
    if (crashReportDir == nil) {
        return NO;
    }

    NSString *crashReportFile = [crashReportDir.absoluteURL.path stringByAppendingPathComponent:@"lastCrash.bjn"];

    @try {
        NSFileManager *fm = NSFileManager.defaultManager;
        NSString *dir = crashReportDir.absoluteURL.path;
        if (![fm fileExistsAtPath:dir]) {
            NSError *error = nil;
            if (![fm createDirectoryAtPath:dir
               withIntermediateDirectories:YES
                                attributes:nil
                                     error:&error]) {
                @throw [NSString stringWithFormat:@"Error: Could not create directory \"%@\": %@", dir, error];
            }
        }

        self.kscrashReportFilePath = crashReportFile;
        if (![fm fileExistsAtPath:crashReportFile]) {
            return YES;
        }

        switch (capture_cache_kscrash_report(crashReportFile)) {
            case CacheResultReportDoesNotExist:
                // KSCrash didn't detect a crash last launch.
                break;
            case CacheResultSuccess:
                break;
            case CacheResultPartialSuccess:
                NSLog(@"Warning: The KSCrash report was only partially recovered.");
                break;
            case CacheResultFailure:
                @throw [NSString stringWithFormat:@"Error caching kscrash report at \"%@\"",self.kscrashReportFilePath];
        }

        if ([fm fileExistsAtPath:crashReportFile]) {
            NSError *error = NULL;
            if (![fm removeItemAtPath:crashReportFile error:&error]) {
                NSLog(@"Error removing old KSCrash report at %@: %@", self.kscrashReportFilePath, error);
            }
        }

        return YES;
    } @catch(id exception) {
        [NSFileManager.defaultManager removeItemAtPath:crashReportFile error:nil];
        NSLog(@"Error configuring BitdriftKSCrashHandler: %@", exception);
        return NO;
    }
}

+ (BOOL)startCrashReporter {
    return [self.sharedInstance startCrashReporter];
}

- (BOOL)startCrashReporter {
    if (self.kscrashReportFilePath == nil) {
        NSLog(@"Error: Cannot start crash reporter: must call configureWithCrashReportPath first");
        return NO;
    }

    static atomic_bool isStarted = false;
    bool expectStarted = false;
    if (!atomic_compare_exchange_strong(&isStarted, &expectStarted, true)) {
        return YES; // Already started
    }

    memset(&g_crashHandlerReportContext, 0, sizeof(g_crashHandlerReportContext));
    g_crashHandlerReportContext.reportPath = strdup(self.kscrashReportFilePath.UTF8String);
    g_crashHandlerReportContext.metadata.pid = NSProcessInfo.processInfo.processIdentifier;

#define ERROR_IF_FALSE(A) do if(!(A)) { \
    NSLog(@"Error: Function returned unexpected false: %s", #A); \
    isStarted = false; \
    return NO; \
} while(0)

    ERROR_IF_FALSE(bdcrw_open_writer(&g_crashHandlerReportContext.writer, g_crashHandlerReportContext.reportPath));

    const int threadCachePollIntervalSecs = 10;
    ksbic_init();
    kstc_init(threadCachePollIntervalSecs);
    kscm_setEventCallback(onCrash);

    ERROR_IF_FALSE(kscm_addMonitor(kscm_cppexception_getAPI()));
    ERROR_IF_FALSE(kscm_addMonitor(kscm_machexception_getAPI()));
    ERROR_IF_FALSE(kscm_addMonitor(kscm_nsexception_getAPI()));
    ERROR_IF_FALSE(kscm_addMonitor(kscm_signal_getAPI()));
    ERROR_IF_FALSE(kscm_activateMonitors());

#undef ERROR_IF_FALSE
    return YES;
}

+ (void)stopCrashReporter {
    [self.sharedInstance stopCrashReporter];
}

- (void)stopCrashReporter {
    kscm_disableAllMonitors();
}

+ (NSDictionary<NSString *, id> *)enhancedMetricKitReport:(NSDictionary<NSString *, id> *)metricKitReport {
    return [self.sharedInstance enhancedMetricKitReport:metricKitReport];
}

- (NSDictionary<NSString *, id> *)enhancedMetricKitReport:(NSDictionary<NSString *, id> *)metricKitReport {
    if (self.kscrashReportFilePath == nil) {
        NSLog(@"Error: Cannot enhance MetricKit report: must call configureWithCrashReportPath first");
        return metricKitReport;
    }

    return capture_enhance_metrickit_diagnostic_report(metricKitReport);
}

@end
