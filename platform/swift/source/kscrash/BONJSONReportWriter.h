// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

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
