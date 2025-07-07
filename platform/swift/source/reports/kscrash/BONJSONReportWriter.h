//
//  BONJSONReportWriter.h
//  CrashTester
//
//  Created by Karl Stenerud on 03.07.25.
//

#pragma once

#ifdef __cplusplus
extern "C" {
#endif

#include "KSCrashReportWriter.h"
#include "KSBONJSONEncoder.h"

typedef struct {
    bool isArray[KSBONJSON_MAX_CONTAINER_DEPTH];
    int indentLevel;
    KSBONJSONEncodeContext bonjsonContext;
} BonjsonWriterContext;

void bitdrift_initBONJSONReportWriter(KSCrashReportWriter *const writer, void* ctx);
void bitdrift_endBONJSONReport(KSCrashReportWriter *const writer);

#ifdef __cplusplus
}
#endif
