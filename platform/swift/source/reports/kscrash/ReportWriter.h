//
//  ReportWriter.h
//  CrashTester
//
//  Created by Karl Stenerud on 02.07.25.
//

#pragma once

#ifdef __cplusplus
extern "C" {
#endif

#include "ReportContext.h"

bool bitdrift_writeStandardReport(ReportContext *context);

#ifdef __cplusplus
}
#endif
