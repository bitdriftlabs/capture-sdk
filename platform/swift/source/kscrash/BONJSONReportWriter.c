// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

#include "BONJSONReportWriter.h"
#include "ReportContext.h"
#include "KSLogger.h"

#include <string.h>

//#define DEBUG_WRITER

#ifdef DEBUG_WRITER
// Prints everything to stdout in a pseudo-json format
#include <stdio.h>
#define DEBUG_PRINT(KEY, FMT, ...) do { \
    for(int i = 0; i < ctx->indentLevel; i++) printf("    "); \
    if((KEY) != NULL) printf("%s = ", (KEY)); \
    printf((FMT), __VA_ARGS__); \
    printf("\n"); \
    fflush(stdout); \
} while(0)
#else
#define DEBUG_PRINT(KEY, FMT, ...) do {} while(0)
#endif

static BonjsonWriterContext* getCtx(const BitdriftReportWriter *const writer) {
    ReportContext* ctx = (ReportContext*)writer->context;
    return ctx->writerContext;
}

static KSBONJSONEncodeContext* getEncodeCtx(const BitdriftReportWriter *const writer) {
    ReportContext* ctx = (ReportContext*)writer->context;
    return &ctx->writerContext->bonjsonContext;
}

static void increaseDepth(const BitdriftReportWriter *const writer, bool isArray) {
    BonjsonWriterContext* ctx = getCtx(writer);
    ctx->indentLevel++;
    ctx->isArray[ctx->indentLevel] = isArray;
}

static bool decreaseDepth(const BitdriftReportWriter *const writer) {
    BonjsonWriterContext* ctx = getCtx(writer);
    bool wasArray = ctx->isArray[ctx->indentLevel];
    if(ctx->indentLevel > 0) {
        ctx->indentLevel--;
    }
    return wasArray;
}

static ksbonjson_encodeStatus addKey(const BitdriftReportWriter *const writer, const char* key) {
    BonjsonWriterContext* ctx = getCtx(writer);
    if(ctx->indentLevel > 0 && !ctx->isArray[ctx->indentLevel]) {
        if(key == NULL) {
            key = "<null>";
        }
        return ksbonjson_addString(&ctx->bonjsonContext, key, strlen(key));
    }
    return KSBONJSON_ENCODE_OK;
}

#define RETURN_ON_FAIL(A) do { \
    ksbonjson_encodeStatus RETURN_ON_FAIL_status = (A); \
    if(RETURN_ON_FAIL_status != KSBONJSON_ENCODE_OK) { \
        KSLOG_ERROR("Failed to " # A ": %s", ksbonjson_describeEncodeStatus(RETURN_ON_FAIL_status)); \
        return false; \
    } \
} while(0)

#define RETURN_RESULT(A) do { \
    RETURN_ON_FAIL(A); \
    return true; \
} while(0)

static bool addBooleanElement(const BitdriftReportWriter *const writer, const char *const key, const bool value) {
    BonjsonWriterContext* ctx = getCtx(writer);
    DEBUG_PRINT(key, "%s", value ? "true" : "false");
    RETURN_ON_FAIL(addKey(writer, key));
    RETURN_RESULT(ksbonjson_addBoolean(&ctx->bonjsonContext, value));
}

static bool addFloatingPointElement(const BitdriftReportWriter *const writer, const char *const key, const double value) {
    BonjsonWriterContext* ctx = getCtx(writer);
    DEBUG_PRINT(key, "%f", value);
    RETURN_ON_FAIL(addKey(writer, key));
    RETURN_RESULT(ksbonjson_addFloat(&ctx->bonjsonContext, value));
}

static bool addIntegerElement(const BitdriftReportWriter *const writer, const char *const key, const int64_t value) {
    BonjsonWriterContext* ctx = getCtx(writer);
    DEBUG_PRINT(key, "%lld", value);
    RETURN_ON_FAIL(addKey(writer, key));
    RETURN_RESULT(ksbonjson_addSignedInteger(&ctx->bonjsonContext, value));
}

static bool addUIntegerElement(const BitdriftReportWriter *const writer, const char *const key, const uint64_t value) {
    BonjsonWriterContext* ctx = getCtx(writer);
    DEBUG_PRINT(key, "%llu", value);
    RETURN_ON_FAIL(addKey(writer, key));
    RETURN_RESULT(ksbonjson_addUnsignedInteger(&ctx->bonjsonContext, value));
}

static bool addStringElement(const BitdriftReportWriter *const writer, const char *const key, const char *const value) {
    BonjsonWriterContext* ctx = getCtx(writer);
    DEBUG_PRINT(key, "\"%s\"", value);
    RETURN_ON_FAIL(addKey(writer, key));
    if(value == NULL) {
        return ksbonjson_addNull(&ctx->bonjsonContext);
    }
    RETURN_RESULT(ksbonjson_addString(&ctx->bonjsonContext, value, strlen(value)));
}

static const char g_hexNybbles[] = { '0', '1', '2', '3', '4', '5', '6', '7', '8', '9', 'A', 'B', 'C', 'D', 'E', 'F' };

static bool addUUIDElement(const BitdriftReportWriter *const writer,
                           const char *const key,
                           const unsigned char *const value) {
    BonjsonWriterContext* ctx = getCtx(writer);
    RETURN_ON_FAIL(addKey(writer, key));

    if (value == NULL) {
        return ksbonjson_addNull(&ctx->bonjsonContext);
    }

    char uuidBuffer[38];
    const unsigned char *src = value;
    char *dst = uuidBuffer;
    for (int i = 0; i < 4; i++) {
        *dst++ = g_hexNybbles[(*src >> 4) & 15];
        *dst++ = g_hexNybbles[(*src++) & 15];
    }
    *dst++ = '-';
    for (int i = 0; i < 2; i++) {
        *dst++ = g_hexNybbles[(*src >> 4) & 15];
        *dst++ = g_hexNybbles[(*src++) & 15];
    }
    *dst++ = '-';
    for (int i = 0; i < 2; i++) {
        *dst++ = g_hexNybbles[(*src >> 4) & 15];
        *dst++ = g_hexNybbles[(*src++) & 15];
    }
    *dst++ = '-';
    for (int i = 0; i < 2; i++) {
        *dst++ = g_hexNybbles[(*src >> 4) & 15];
        *dst++ = g_hexNybbles[(*src++) & 15];
    }
    *dst++ = '-';
    for (int i = 0; i < 6; i++) {
        *dst++ = g_hexNybbles[(*src >> 4) & 15];
        *dst++ = g_hexNybbles[(*src++) & 15];
    }
    *dst = 0;
    DEBUG_PRINT(key, "\"%s\"", uuidBuffer);
    RETURN_RESULT(ksbonjson_addString(&ctx->bonjsonContext, uuidBuffer, strlen(uuidBuffer)));
}

static bool beginObject(const BitdriftReportWriter *const writer, const char *const key) {
    BonjsonWriterContext* ctx = getCtx(writer);
    DEBUG_PRINT(key, "{", NULL);
    RETURN_ON_FAIL(addKey(writer, key));
    ksbonjson_encodeStatus status = ksbonjson_beginObject(&ctx->bonjsonContext);
    if(status == KSBONJSON_ENCODE_OK) {
        increaseDepth(writer, false);
    } else {
        KSLOG_ERROR("Failed to ksbonjson_beginObject(): %s", ksbonjson_describeEncodeStatus(status));
    }
    return status == KSBONJSON_ENCODE_OK;
}

static bool beginArray(const BitdriftReportWriter *const writer, const char *const key) {
    BonjsonWriterContext* ctx = getCtx(writer);
    DEBUG_PRINT(key, "[", NULL);
    RETURN_ON_FAIL(addKey(writer, key));
    ksbonjson_encodeStatus status = ksbonjson_beginArray(&ctx->bonjsonContext);
    if(status == KSBONJSON_ENCODE_OK) {
        increaseDepth(writer, true);
    } else {
        KSLOG_ERROR("Failed to ksbonjson_beginArray(): %s", ksbonjson_describeEncodeStatus(status));
    }
    return status == KSBONJSON_ENCODE_OK;
}

static bool endContainer(const BitdriftReportWriter *const writer) {
    BonjsonWriterContext* ctx = getCtx(writer);
    ksbonjson_encodeStatus status = ksbonjson_endContainer(&ctx->bonjsonContext);
    if(status == KSBONJSON_ENCODE_OK) {
        if(decreaseDepth(writer)) {
            DEBUG_PRINT((char*)NULL, "]", NULL);
        } else {
            DEBUG_PRINT((char*)NULL, "}", NULL);
        }
    } else {
        KSLOG_ERROR("Failed to ksbonjson_endContainer(): %s", ksbonjson_describeEncodeStatus(status));
    }
    return status == KSBONJSON_ENCODE_OK;
}

static ksbonjson_encodeStatus addEncodedData(const uint8_t* KSBONJSON_RESTRICT data,
                           size_t dataLength,
                           void* KSBONJSON_RESTRICT userData) {
    ReportContext* ctx = (ReportContext*)userData;
    if (!ksfu_writeBufferedWriter(&ctx->bufferedWriter, (const char*)data, (int)dataLength)) {
        return KSBONJSON_ENCODE_COULD_NOT_ADD_DATA;
    }
    return KSBONJSON_ENCODE_OK;
}

void bitdrift_beginBONJSONReport(BitdriftReportWriter *const writer, void* ctx) {
    writer->addBooleanElement = addBooleanElement;
    writer->addFloatingPointElement = addFloatingPointElement;
    writer->addIntegerElement = addIntegerElement;
    writer->addUIntegerElement = addUIntegerElement;
    writer->addStringElement = addStringElement;
    writer->addUUIDElement = addUUIDElement;
    writer->beginObject = beginObject;
    writer->beginArray = beginArray;
    writer->endContainer = endContainer;
    writer->context = ctx;
    ksbonjson_beginEncode(getEncodeCtx(writer), addEncodedData, ctx);
}

bool bitdrift_endBONJSONReport(BitdriftReportWriter *const writer) {
    RETURN_RESULT(ksbonjson_endEncode(getEncodeCtx(writer)));
}
