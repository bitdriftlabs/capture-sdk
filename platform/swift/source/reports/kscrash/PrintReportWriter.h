//
//  PrintReportWriter.h
//  CrashTester
//
//  Created by Karl Stenerud on 02.07.25.
//

#pragma once

#ifdef __cplusplus
extern "C" {
#endif

#include "../kscrash/KSCrashReportWriter.h"

void initPrintReportWriter(KSCrashReportWriter *const writer);

#ifdef __cplusplus
}
#endif
