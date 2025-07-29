// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

#include "ReportWriter.h"
#include "ReportWriterPrivate.h"

#include "KSCrashMonitorContext.h"
#include "KSMachineContext.h"
#include "KSLogger.h"
#include "KSStackCursor.h"
#include "KSStackCursor_MachineContext.h"
#include "KSCrashReportFields.h"
#include "KSThreadCache.h"
#include "KSFileUtils.h"
#include "KSDynamicLinker.h"
#include "BONJSONReportWriter.h"
#include "ReportContext.h"

static bool getStackCursor(const ReportContext *const ctx,
                           const struct KSMachineContext *const machineContext,
                           KSStackCursor *cursor) {
    if (ksmc_getThreadFromContext(machineContext) == ksmc_getThreadFromContext(ctx->monitorContext->offendingMachineContext)) {
        *cursor = *((KSStackCursor *)ctx->monitorContext->stackCursor);
        return true;
    }
    
    kssc_initWithMachineContext(cursor, KSSC_STACK_OVERFLOW_THRESHOLD, machineContext);
    return true;
}

#define RETURN_ON_FAIL(A) if(!(A)) return false

static bool writeBacktrace(const BitdriftReportWriter *const writer, const char *const key, KSStackCursor *stackCursor) {
    RETURN_ON_FAIL(writer->beginObject(writer, key));
    {
        RETURN_ON_FAIL(writer->beginArray(writer, KSCrashField_Contents));
        {
            while (stackCursor->advanceCursor(stackCursor)) {
                RETURN_ON_FAIL(writer->beginObject(writer, NULL));
                {
                    RETURN_ON_FAIL(writer->addUIntegerElement(writer, "address", stackCursor->stackEntry.address));
                    Dl_info info = {0};
                    if(ksdl_dladdr(stackCursor->stackEntry.address, &info))
                    {
                        RETURN_ON_FAIL(writer->addStringElement(writer, "binaryName", ksfu_lastPathEntry(info.dli_fname)));
                        RETURN_ON_FAIL(writer->addUIntegerElement(writer, "offsetIntoBinaryTextSegment", info.dli_saddr - info.dli_fbase));
                        KSBinaryImage img = {0};
                        if(ksdl_getBinaryImageForHeader(info.dli_fbase, info.dli_fname, &img)) {
                            RETURN_ON_FAIL(writer->addUUIDElement(writer, "binaryUUID", img.uuid));
                        }
                    }
                }
                RETURN_ON_FAIL(writer->endContainer(writer));
            }
        }
        RETURN_ON_FAIL(writer->endContainer(writer));
        RETURN_ON_FAIL(writer->addIntegerElement(writer, KSCrashField_Skipped, 0));
    }
    return writer->endContainer(writer);
}

static bool writeThread(const BitdriftReportWriter *const writer,
                        const char *const key,
                        const int threadIndex,
                        const ReportContext* ctx,
                        const struct KSMachineContext *const machineContext) {
    bool isCrashedThread = ksmc_isCrashedContext(machineContext);
    KSThread thread = ksmc_getThreadFromContext(machineContext);
    KSLOG_DEBUG("Writing thread %x (index %d). is crashed: %d", thread, threadIndex, isCrashedThread);
    
    KSStackCursor stackCursor;
    bool hasBacktrace = getStackCursor(ctx, machineContext, &stackCursor);
    
    RETURN_ON_FAIL(writer->beginObject(writer, key));
    {
        if (hasBacktrace) {
            writeBacktrace(writer, KSCrashField_Backtrace, &stackCursor);
        }
        RETURN_ON_FAIL(writer->addIntegerElement(writer, KSCrashField_Index, threadIndex));
        const char *name = kstc_getThreadName(thread);
        if (name != NULL) {
            RETURN_ON_FAIL(writer->addStringElement(writer, KSCrashField_Name, name));
        }
        name = kstc_getQueueName(thread);
        if (name != NULL) {
            RETURN_ON_FAIL(writer->addStringElement(writer, KSCrashField_DispatchQueue, name));
        }
        RETURN_ON_FAIL(writer->addBooleanElement(writer, KSCrashField_Crashed, isCrashedThread));
        RETURN_ON_FAIL(writer->addBooleanElement(writer, KSCrashField_CurrentThread, thread == ksthread_self()));
    }
    RETURN_ON_FAIL(writer->endContainer(writer));
    return true;
}

static bool writeAllThreads(const BitdriftReportWriter *const writer, const ReportContext* ctx) {
    const struct KSMachineContext *const offendingMachineContext = ctx->monitorContext->offendingMachineContext;
    KSThread offendingThread = ksmc_getThreadFromContext(offendingMachineContext);
    int threadCount = ksmc_getThreadCount(offendingMachineContext);
    
    KSLOG_DEBUG("Writing %d threads.", threadCount);
    for (int i = 0; i < threadCount; i++) {
        KSThread thread = ksmc_getThreadAtIndex(offendingMachineContext, i);
        if (thread == offendingThread) {
            RETURN_ON_FAIL(writeThread(writer, NULL, i, ctx, offendingMachineContext));
        } else {
            KSMachineContext machineContext = { 0 };
            ksmc_getContextForThread(thread, &machineContext, false);
            RETURN_ON_FAIL(writeThread(writer, NULL, i, ctx, &machineContext));
        }
    }
    return true;
}

static bool writeMetadata(BitdriftReportWriter *writer, const ReportContext* ctx) {
    RETURN_ON_FAIL(writer->addUIntegerElement(writer, "crashedAt", ctx->metadata.time));
    RETURN_ON_FAIL(writer->addUIntegerElement(writer, "pid", ctx->metadata.pid));
    RETURN_ON_FAIL(writer->addUIntegerElement(writer, "exceptionType", ctx->monitorContext->mach.type));
    RETURN_ON_FAIL(writer->addUIntegerElement(writer, "exceptionCode", ctx->monitorContext->mach.code));
    RETURN_ON_FAIL(writer->addUIntegerElement(writer, "signal", ctx->monitorContext->signal.signum));
    return true;
}

static bool writeReport(BitdriftReportWriter *writer, ReportContext* ctx) {
    RETURN_ON_FAIL(writer->beginObject(writer, NULL));
    {
        RETURN_ON_FAIL(writer->beginObject(writer, "diagnosticMetaData"));
        {
            RETURN_ON_FAIL(writeMetadata(writer, ctx));
        }
        RETURN_ON_FAIL(writer->endContainer(writer));

        RETURN_ON_FAIL(writer->beginArray(writer, "threads"));
        {
            RETURN_ON_FAIL(writeAllThreads(writer, ctx));
        }
        RETURN_ON_FAIL(writer->endContainer(writer));
    }
    RETURN_ON_FAIL(writer->endContainer(writer));
    return true;
}

bool bitdrift_writeKSCrashReport(ReportContext *ctx) {
    KSLOG_DEBUG("Writing report at path: %s\n", ctx->reportPath);
    
    BitdriftReportWriter writer;
    bool result = true;

    char writeBuffer[1024];
    ksfu_removeFile(ctx->reportPath, false);
    if (!ksfu_openBufferedWriter(&ctx->bufferedWriter, ctx->reportPath, writeBuffer, sizeof(writeBuffer))) {
        KSLOG_ERROR("Could not open report file %s", ctx->reportPath);
        return false;
    }
    BonjsonWriterContext writerContext = {0};
    ctx->writerContext = &writerContext;
    bitdrift_beginBONJSONReport(&writer, ctx);
    if(!writeReport(&writer, ctx)) {
        KSLOG_ERROR("Error encountered while writing report to file %s", ctx->reportPath);
        result = false;
    }
    bitdrift_endBONJSONReport(&writer);
    ksfu_closeBufferedWriter(&ctx->bufferedWriter);
    return result;
}
