// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

#include "CrashHandling.h"

#include "KSCrashMonitor.h"
#include "KSCrashMonitor_CPPException.h"
#include "KSCrashMonitor_NSException.h"
#include "KSCrashMonitor_Signal.h"
#include "KSCrashMonitor_MachException.h"
#include "KSBinaryImageCache.h"
#include "KSThreadCache.h"
#include "../reports/kscrash/ReportWriter.h"
#include "../reports/kscrash/ReportContext.h"
#include "../reports/kscrash/ReportReader.h"

#import <UIKit/UIKit.h>
#include <stdatomic.h>
#include <string.h>
#include <sys/sysctl.h>
#include <sys/utsname.h>

// Temporary fix for this dependency in KSCrashMonitor_Signal to KSCrashMonitor_Memory
void ksmemory_notifyUnhandledFatalSignal(void) {}

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

static NSString *getNextCrashReportPath(NSURL *basePath)
{
    NSString *path = [NSString stringWithUTF8String:basePath.fileSystemRepresentation];
    path = [path stringByAppendingPathComponent:@"kscrash"];
    return [path stringByAppendingPathComponent:@"lastCrash.bjn"];
}

static void initReportContext(ReportContext* context) {
    UIDevice *dev = UIDevice.currentDevice;
    NSProcessInfo *proc = NSProcessInfo.processInfo;
    struct utsname uts;
    uname(&uts);

    memset(context, 0, sizeof(*context));
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

static bool mkdirs(const char* crashReportPath) {
    NSString *dir = [[NSString stringWithUTF8String:crashReportPath] stringByDeletingLastPathComponent];
    NSError *error = nil;
    bool result = [NSFileManager.defaultManager createDirectoryAtPath:dir
                                          withIntermediateDirectories:YES
                                                           attributes:nil
                                                                error:&error];
    if(!result)
    {
        NSLog(@"Could not create directory \"%@\": %@", dir, error);
    }
    return result;
}

static ReportContext g_baseContext;

static void onCrash(struct KSCrash_MonitorContext *monitorContext)
{
    ReportContext context = g_baseContext;
    context.metadata.time = time(NULL);
    context.monitorContext = monitorContext;
    bitdrift_writeStandardReport(&context);
}

bool bitdrift_install_crash_handler(NSURL *basePath) {
    ksbic_init();
    kstc_init(10);
    initReportContext(&g_baseContext);

#define RETURN_IF_FALSE(A) if(!(A)) return false

    g_baseContext.reportPath = strdup(getNextCrashReportPath(basePath).UTF8String);
    RETURN_IF_FALSE(mkdirs(g_baseContext.reportPath));

    kscm_setEventCallback(onCrash);
    RETURN_IF_FALSE(kscm_addMonitor(kscm_cppexception_getAPI()));
    RETURN_IF_FALSE(kscm_addMonitor(kscm_machexception_getAPI()));
    RETURN_IF_FALSE(kscm_addMonitor(kscm_nsexception_getAPI()));
    RETURN_IF_FALSE(kscm_addMonitor(kscm_signal_getAPI()));
    if (kscm_activateMonitors() == false) {
        NSLog(@"Error: No crash monitors were installed\n");
        return false;
    }
    return true;
}

void bitdrift_uninstall_crash_handler(void) {
    kscm_disableAllMonitors();
}

static void fixupReport(NSMutableDictionary* report) {
    NSMutableDictionary* diagnosticMetadata = report[@"diagnosticMetaData"];
    NSNumber* secsSince1970 = diagnosticMetadata[@"crashedAt"];
    if(secsSince1970 != nil) {
        diagnosticMetadata[@"crashedAt"] = [NSDate dateWithTimeIntervalSince1970:secsSince1970.longLongValue];
    }
}

NSDictionary *bitdrift_getLastReport(NSURL *basePath) {
    NSMutableDictionary *report = bitdrift_readReport(getNextCrashReportPath(basePath));
    [NSFileManager.defaultManager removeItemAtPath:getNextCrashReportPath(basePath) error:nil];
    fixupReport(report);
    return report;
}
