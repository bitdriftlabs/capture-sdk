//
//  ReportWriter.c
//  CrashTester
//
//  Created by Karl Stenerud on 02.07.25.
//

#include "ReportWriter.h"

#include "KSCrashMonitorContext.h"
#include "KSMachineContext.h"
#include "KSLogger.h"
#include "KSStackCursor.h"
#include "KSStackCursor_MachineContext.h"
#include "KSCrashReportWriter.h"
#include "KSCrashReportFields.h"
#include "KSThreadCache.h"
#include "KSFileUtils.h"
#include "KSDynamicLinker.h"

//#include "PrintReportWriter.h"
#include "BONJSONReportWriter.h"
#include "ReportContext.h"

static bool getStackCursor(const ReportContext *const ctx,
                           const struct KSMachineContext *const machineContext,
                           KSStackCursor *cursor)
{
    if (ksmc_getThreadFromContext(machineContext) == ksmc_getThreadFromContext(ctx->monitorContext->offendingMachineContext)) {
        *cursor = *((KSStackCursor *)ctx->monitorContext->stackCursor);
        return true;
    }
    
    kssc_initWithMachineContext(cursor, KSSC_STACK_OVERFLOW_THRESHOLD, machineContext);
    return true;
}


static void writeBacktrace(const KSCrashReportWriter *const writer, const char *const key, KSStackCursor *stackCursor)
{
    writer->beginObject(writer, key);
    {
        writer->beginArray(writer, KSCrashField_Contents);
        {
            while (stackCursor->advanceCursor(stackCursor)) {
                writer->beginObject(writer, NULL);
                {
                    writer->addUIntegerElement(writer, "address", stackCursor->stackEntry.address);
                    Dl_info info = {0};
                    if(ksdl_dladdr(stackCursor->stackEntry.address, &info))
                    {
                        writer->addStringElement(writer, "binaryName", ksfu_lastPathEntry(info.dli_fname));
                        writer->addUIntegerElement(writer, "offsetIntoBinaryTextSegment", info.dli_saddr - info.dli_fbase);
                        KSBinaryImage img = {0};
                        if(ksdl_getBinaryImageForHeader(info.dli_fbase, info.dli_fname, &img)) {
                            writer->addUUIDElement(writer, "binaryUUID", img.uuid);
                        }
                    }
                }
                writer->endContainer(writer);
            }
        }
        writer->endContainer(writer);
        writer->addIntegerElement(writer, KSCrashField_Skipped, 0);
    }
    writer->endContainer(writer);
}

static void writeThread(const KSCrashReportWriter *const writer,
                        const char *const key,
                        const int threadIndex,
                        const ReportContext* ctx,
                        const struct KSMachineContext *const machineContext)
{
    bool isCrashedThread = ksmc_isCrashedContext(machineContext);
    KSThread thread = ksmc_getThreadFromContext(machineContext);
    KSLOG_DEBUG("Writing thread %x (index %d). is crashed: %d", thread, threadIndex, isCrashedThread);
    
    KSStackCursor stackCursor;
    bool hasBacktrace = getStackCursor(ctx, machineContext, &stackCursor);
    
    writer->beginObject(writer, key);
    {
        if (hasBacktrace) {
            writeBacktrace(writer, KSCrashField_Backtrace, &stackCursor);
        }
        writer->addIntegerElement(writer, KSCrashField_Index, threadIndex);
        const char *name = kstc_getThreadName(thread);
        if (name != NULL) {
            writer->addStringElement(writer, KSCrashField_Name, name);
        }
        name = kstc_getQueueName(thread);
        if (name != NULL) {
            writer->addStringElement(writer, KSCrashField_DispatchQueue, name);
        }
        writer->addBooleanElement(writer, KSCrashField_Crashed, isCrashedThread);
        writer->addBooleanElement(writer, KSCrashField_CurrentThread, thread == ksthread_self());
    }
    writer->endContainer(writer);
}

static void writeAllThreads(const KSCrashReportWriter *const writer, const ReportContext* ctx)
{
    const struct KSMachineContext *const offendingMachineContext = ctx->monitorContext->offendingMachineContext;
    KSThread offendingThread = ksmc_getThreadFromContext(offendingMachineContext);
    int threadCount = ksmc_getThreadCount(offendingMachineContext);
    
    KSLOG_DEBUG("Writing %d threads.", threadCount);
    for (int i = 0; i < threadCount; i++) {
        KSThread thread = ksmc_getThreadAtIndex(offendingMachineContext, i);
        if (thread == offendingThread) {
            writeThread(writer, NULL, i, ctx, offendingMachineContext);
        } else {
            KSMachineContext machineContext = { 0 };
            ksmc_getContextForThread(thread, &machineContext, false);
            writeThread(writer, NULL, i, ctx, &machineContext);
        }
    }
}

static void writeMetadata(KSCrashReportWriter *writer, const ReportContext* ctx) {
    writer->addUIntegerElement(writer, "crashedAt", ctx->metadata.time);
    writer->addStringElement(writer, "appBuildVersion", ctx->metadata.appBuildVersion);
    writer->addStringElement(writer, "appVersion", ctx->metadata.appVersion);
    writer->addStringElement(writer, "bundleIdentifier", ctx->metadata.bundleIdentifier);
    writer->addStringElement(writer, "deviceType", ctx->metadata.deviceType);
    writer->addStringElement(writer, "machine", ctx->metadata.machine);
    writer->addStringElement(writer, "osVersion", ctx->metadata.osVersion);
    writer->addStringElement(writer, "osBuild", ctx->metadata.osBuild);
    writer->addUIntegerElement(writer, "pid", ctx->metadata.pid);
    writer->addStringElement(writer, "regionFormat", ctx->metadata.regionFormat);
}

static void writeReport(KSCrashReportWriter *writer, ReportContext* ctx) {
    writer->beginObject(writer, NULL);
    {
        writer->beginObject(writer, "diagnosticMetaData");
        writeMetadata(writer, ctx);
        writer->endContainer(writer);

        writer->beginArray(writer, "threads");
        writeAllThreads(writer, ctx);
        writer->endContainer(writer);
    }
    writer->endContainer(writer);
}

#include <stdio.h>

void bitdrift_writeStandardReport(ReportContext *ctx) {
    printf("Writing report at path: %s\n", ctx->reportPath);
    
    KSCrashReportWriter writer;

    char writeBuffer[1024];
    ksfu_removeFile(ctx->reportPath, false);
    if (!ksfu_openBufferedWriter(&ctx->bufferedWriter, ctx->reportPath, writeBuffer, sizeof(writeBuffer))) {
        return;
    }
    BonjsonWriterContext writerContext = {0};
    ctx->writerContext = &writerContext;
    bitdrift_initBONJSONReportWriter(&writer, ctx);
    writeReport(&writer, ctx);
    bitdrift_endBONJSONReport(&writer);
    ksfu_closeBufferedWriter(&ctx->bufferedWriter);
    
//    initPrintReportWriter(&writer);
//    writeReport(&writer, ctx);
}
