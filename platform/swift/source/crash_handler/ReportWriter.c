// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

#include "ReportWriter.h"

#include "KSCrashMonitorContext.h"
#include "KSMachineContext.h"
#include "KSLogger.h"
#include "KSStackCursor.h"
#include "KSStackCursor_MachineContext.h"
#include "KSCrashReportFields.h"
#include "KSThreadCache.h"
#include "KSFileUtils.h"
#include "KSDynamicLinker.h"
#include "ReportContext.h"
#include "bd-bonjson/ffi.h"

#include <stdio.h>

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

#define BUILD_KV_WRITE_FUNC_0ARG(LOCAL_NAME, FFI_NAME) \
static bool writeKV##LOCAL_NAME(BDCrashWriterHandle writer, const char *key) { \
    RETURN_ON_FAIL(bdcrw_write_str(writer, key)); \
    return bdcrw_write_##FFI_NAME(writer); \
}

#define BUILD_KV_WRITE_FUNC_1ARG(LOCAL_NAME, FFI_NAME, TYPE) \
static bool writeKV##LOCAL_NAME(BDCrashWriterHandle writer, const char *key, TYPE value) { \
    RETURN_ON_FAIL(bdcrw_write_str(writer, key)); \
    return bdcrw_write_##FFI_NAME(writer, value); \
}

BUILD_KV_WRITE_FUNC_0ARG(ArrayBegin, array_begin)
BUILD_KV_WRITE_FUNC_0ARG(ObjectBegin, map_begin)
BUILD_KV_WRITE_FUNC_1ARG(Unsigned, unsigned, uint64_t)
BUILD_KV_WRITE_FUNC_1ARG(Signed, signed, int64_t)
BUILD_KV_WRITE_FUNC_1ARG(String, str, const char *)
BUILD_KV_WRITE_FUNC_1ARG(Boolean, boolean, bool)

static bool writeKVUUID(BDCrashWriterHandle writer, const char *key, const uint8_t *value) {
    // Example: 9c5b94b1-35ad-49bb-b118-8e8fc24abf80
    char uuid[40] = {0};
    // sprintf and friends are signal safe so long as you don't try to print floats.
    snprintf(uuid, sizeof(uuid), "%02x%02x%02x%02x-%02x%02x-%02x%02x-%02x%02x-%02x%02x%02x%02x%02x%02x",
             value[0], value[1], value[2], value[3], value[4], value[5], value[6], value[7],
             value[8], value[9], value[10], value[11], value[12], value[13], value[14], value[15]);
    return writeKVString(writer, key, uuid);
}

static bool writeBacktrace(BDCrashWriterHandle writer, const char *const key, KSStackCursor *stackCursor) {
    RETURN_ON_FAIL(writeKVObjectBegin(writer, key));
    {
        RETURN_ON_FAIL(writeKVArrayBegin(writer, KSCrashField_Contents));
        {
            while (stackCursor->advanceCursor(stackCursor)) {
                RETURN_ON_FAIL(bdcrw_write_map_begin(writer));
                {
                    RETURN_ON_FAIL(writeKVUnsigned(writer, "address", stackCursor->stackEntry.address));
                    Dl_info info = {0};
                    if(ksdl_dladdr(stackCursor->stackEntry.address, &info))
                    {
                        RETURN_ON_FAIL(writeKVString(writer, "binaryName", ksfu_lastPathEntry(info.dli_fname)));
                        RETURN_ON_FAIL(writeKVUnsigned(writer, "offsetIntoBinaryTextSegment", info.dli_saddr - info.dli_fbase));
                        KSBinaryImage img = {0};
                        if(ksdl_binaryImageForHeader(info.dli_fbase, info.dli_fname, &img)) {
                            RETURN_ON_FAIL(writeKVUUID(writer, "binaryUUID", img.uuid));
                        }
                    }
                }
                RETURN_ON_FAIL(bdcrw_write_container_end(writer));
            }
        }
        RETURN_ON_FAIL(bdcrw_write_container_end(writer));
        RETURN_ON_FAIL(writeKVUnsigned(writer, KSCrashField_Skipped, 0));
    }
    return bdcrw_write_container_end(writer);
}

static bool writeThread(BDCrashWriterHandle writer,
                        const int threadIndex,
                        const ReportContext* ctx,
                        const struct KSMachineContext *const machineContext) {
    bool isCrashedThread = ksmc_isCrashedContext(machineContext);
    KSThread thread = ksmc_getThreadFromContext(machineContext);
    KSLOG_DEBUG("Writing thread %x (index %d). is crashed: %d", thread, threadIndex, isCrashedThread);
    
    KSStackCursor stackCursor;
    bool hasBacktrace = getStackCursor(ctx, machineContext, &stackCursor);
    
    RETURN_ON_FAIL(bdcrw_write_map_begin(writer));
    {
        if (hasBacktrace) {
            writeBacktrace(writer, KSCrashField_Backtrace, &stackCursor);
        }
        RETURN_ON_FAIL(writeKVSigned(writer, KSCrashField_Index, threadIndex));
        const char *name = kstc_getThreadName(thread);
        if (name != NULL) {
            RETURN_ON_FAIL(writeKVString(writer, KSCrashField_Name, name));
        }
        name = kstc_getQueueName(thread);
        if (name != NULL) {
            RETURN_ON_FAIL(writeKVString(writer, KSCrashField_DispatchQueue, name));
        }
        RETURN_ON_FAIL(writeKVBoolean(writer, KSCrashField_Crashed, isCrashedThread));
        RETURN_ON_FAIL(writeKVBoolean(writer, KSCrashField_CurrentThread, thread == ksthread_self()));
    }
    RETURN_ON_FAIL(bdcrw_write_container_end(writer));
    return true;
}

static bool writeAllThreads(BDCrashWriterHandle writer, const ReportContext* ctx) {
    const struct KSMachineContext *const offendingMachineContext = ctx->monitorContext->offendingMachineContext;
    KSThread offendingThread = ksmc_getThreadFromContext(offendingMachineContext);
    int threadCount = ksmc_getThreadCount(offendingMachineContext);
    
    KSLOG_DEBUG("Writing %d threads.", threadCount);
    for (int i = 0; i < threadCount; i++) {
        KSThread thread = ksmc_getThreadAtIndex(offendingMachineContext, i);
        if (thread == offendingThread) {
            RETURN_ON_FAIL(writeThread(writer, i, ctx, offendingMachineContext));
        } else {
            KSMachineContext machineContext = { 0 };
            ksmc_getContextForThread(thread, &machineContext, false);
            RETURN_ON_FAIL(writeThread(writer, i, ctx, &machineContext));
        }
    }
    return true;
}

static bool writeMetadata(BDCrashWriterHandle writer, const ReportContext* ctx) {
    RETURN_ON_FAIL(writeKVUnsigned(writer, "crashedAt", ctx->metadata.time));
    RETURN_ON_FAIL(writeKVUnsigned(writer, "pid", ctx->metadata.pid));
    RETURN_ON_FAIL(writeKVUnsigned(writer, "exceptionType", ctx->monitorContext->mach.type));
    RETURN_ON_FAIL(writeKVUnsigned(writer, "exceptionCode", ctx->monitorContext->mach.code));
    RETURN_ON_FAIL(writeKVUnsigned(writer, "signal", ctx->monitorContext->signal.signum));
    return true;
}

static bool writeReport(BDCrashWriterHandle writer, ReportContext* ctx) {
    RETURN_ON_FAIL(bdcrw_write_map_begin(writer));
    {
        RETURN_ON_FAIL(writeKVObjectBegin(writer, "diagnosticMetaData"));
        {
            RETURN_ON_FAIL(writeMetadata(writer, ctx));
        }
        RETURN_ON_FAIL(bdcrw_write_container_end(writer));

        RETURN_ON_FAIL(writeKVArrayBegin(writer, "threads"));
        {
            RETURN_ON_FAIL(writeAllThreads(writer, ctx));
        }
        RETURN_ON_FAIL(bdcrw_write_container_end(writer));
    }
    RETURN_ON_FAIL(bdcrw_write_container_end(writer));
    return true;
}

bool bitdrift_writeKSCrashReport(ReportContext *ctx) {
    KSLOG_DEBUG("Writing report at path: %s\n", ctx->reportPath);
    
    if(!writeReport(&ctx->writer, ctx)) {
        KSLOG_ERROR("Error encountered while writing report to file %s", ctx->reportPath);
        return false;
    }
    return true;
}
