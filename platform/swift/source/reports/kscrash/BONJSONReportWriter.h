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

#include "ReportWriterPrivate.h"
#include "KSBONJSONEncoder.h"

typedef struct {
    bool isArray[KSBONJSON_MAX_CONTAINER_DEPTH];
    int indentLevel;
    KSBONJSONEncodeContext bonjsonContext;
} BonjsonWriterContext;

void bitdrift_beginBONJSONReport(BitdriftReportWriter *const writer, void* ctx);
bool bitdrift_endBONJSONReport(BitdriftReportWriter *const writer);

#ifdef __cplusplus
}
#endif
