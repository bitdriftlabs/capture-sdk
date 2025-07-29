// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

#pragma once

#include <stdint.h>
#include "BONJSONReportWriter.h"
#include "KSCrashMonitorContext.h"
#include "KSFileUtils.h"

#ifdef __cplusplus
extern "C" {
#endif

typedef struct {
    pid_t pid;
    time_t time;
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
