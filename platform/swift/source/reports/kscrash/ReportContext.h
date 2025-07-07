//
//  ReportContext.h
//  CrashTester
//
//  Created by Karl Stenerud on 03.07.25.
//

#pragma once

#ifdef __cplusplus
extern "C" {
#endif

#include <stdint.h>
#include "BONJSONReportWriter.h"
#include "KSCrashMonitorContext.h"
#include "KSFileUtils.h"


typedef struct {
    pid_t pid;
    time_t time;
    int signal;
    char* deviceType;
    char* osVersion;
    char* osBuild;
    char* machine;
    char* appBuildVersion;
    char* appVersion;
    char* bundleIdentifier;
    char* regionFormat;
} ReportMetadata;

typedef struct {
    char* reportPath;
    ReportMetadata metadata;
    KSCrash_MonitorContext* monitorContext;
    BonjsonWriterContext* writerContext;
    KSBufferedWriter bufferedWriter;
} ReportContext;

#ifdef __cplusplus
}
#endif
