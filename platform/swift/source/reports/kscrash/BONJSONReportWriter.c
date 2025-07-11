//
//  BONJSONReportWriter.c
//  CrashTester
//
//  Created by Karl Stenerud on 03.07.25.
//

#include "BONJSONReportWriter.h"
#include "ReportContext.h"

#include <string.h>

static BonjsonWriterContext* getCtx(const KSCrashReportWriter *const writer) {
    ReportContext* ctx = (ReportContext*)writer->context;
    return ctx->writerContext;
}

static KSBONJSONEncodeContext* getEncodeCtx(const KSCrashReportWriter *const writer) {
    ReportContext* ctx = (ReportContext*)writer->context;
    return &ctx->writerContext->bonjsonContext;
}

static void increaseDepth(const KSCrashReportWriter *const writer, bool isArray) {
    BonjsonWriterContext* ctx = getCtx(writer);
    ctx->indentLevel++;
    ctx->isArray[ctx->indentLevel] = isArray;
}

static bool decreaseDepth(const KSCrashReportWriter *const writer) {
    BonjsonWriterContext* ctx = getCtx(writer);
    bool wasArray = ctx->isArray[ctx->indentLevel];
    if(ctx->indentLevel > 0) {
        ctx->indentLevel--;
    }
    return wasArray;
}

static void addKey(const KSCrashReportWriter *const writer, const char* key) {
    BonjsonWriterContext* ctx = getCtx(writer);
    if(ctx->indentLevel > 0 && !ctx->isArray[ctx->indentLevel]) {
        if(key == NULL) {
            key = "<null>";
        }
        ksbonjson_addString(&ctx->bonjsonContext, key, strlen(key));
    }
}


static void addBooleanElement(const KSCrashReportWriter *const writer, const char *const key, const bool value)
{
    BonjsonWriterContext* ctx = getCtx(writer);
    addKey(writer, key);
    ksbonjson_addBoolean(&ctx->bonjsonContext, value);
}

static void addFloatingPointElement(const KSCrashReportWriter *const writer, const char *const key, const double value)
{
    BonjsonWriterContext* ctx = getCtx(writer);
    addKey(writer, key);
    ksbonjson_addFloat(&ctx->bonjsonContext, value);
}

static void addIntegerElement(const KSCrashReportWriter *const writer, const char *const key, const int64_t value)
{
    BonjsonWriterContext* ctx = getCtx(writer);
    addKey(writer, key);
    ksbonjson_addSignedInteger(&ctx->bonjsonContext, value);
}

static void addUIntegerElement(const KSCrashReportWriter *const writer, const char *const key, const uint64_t value)
{
    BonjsonWriterContext* ctx = getCtx(writer);
    addKey(writer, key);
    ksbonjson_addUnsignedInteger(&ctx->bonjsonContext, value);
}

static void addStringElement(const KSCrashReportWriter *const writer, const char *const key, const char *const value)
{
    BonjsonWriterContext* ctx = getCtx(writer);
    addKey(writer, key);
    if(value == NULL)
    {
        ksbonjson_addNull(&ctx->bonjsonContext);
    }
    else
    {
        ksbonjson_addString(&ctx->bonjsonContext, value, strlen(value));
    }
}

static void addTextFileElement(const KSCrashReportWriter *const writer, const char *const key,
                               const char *const filePath)
{
}

static void addDataElement(const KSCrashReportWriter *const writer, const char *const key, const char *const value,
                           const int length)
{
}

static void beginDataElement(const KSCrashReportWriter *const writer, const char *const key)
{
}

static void appendDataElement(const KSCrashReportWriter *const writer, const char *const value, const int length)
{
}

static void endDataElement(const KSCrashReportWriter *const writer)
{
}

static const char g_hexNybbles[] = { '0', '1', '2', '3', '4', '5', '6', '7', '8', '9', 'A', 'B', 'C', 'D', 'E', 'F' };

static void addUUIDElement(const KSCrashReportWriter *const writer, const char *const key,
                           const unsigned char *const value)
{
    BonjsonWriterContext* ctx = getCtx(writer);
    addKey(writer, key);

    if (value == NULL) {
        ksbonjson_addNull(&ctx->bonjsonContext);
        return;
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
    ksbonjson_addString(&ctx->bonjsonContext, uuidBuffer, strlen(uuidBuffer));
}

static void addJSONElement(const KSCrashReportWriter *const writer, const char *const key,
                           const char *const jsonElement, bool closeLastContainer)
{
}

static void addJSONElementFromFile(const KSCrashReportWriter *const writer, const char *const key,
                                   const char *const filePath, bool closeLastContainer)
{
}

static void beginObject(const KSCrashReportWriter *const writer, const char *const key)
{
    BonjsonWriterContext* ctx = getCtx(writer);
    addKey(writer, key);
    ksbonjson_beginObject(&ctx->bonjsonContext);
    increaseDepth(writer, false);
}

static void beginArray(const KSCrashReportWriter *const writer, const char *const key)
{
    BonjsonWriterContext* ctx = getCtx(writer);
    addKey(writer, key);
    ksbonjson_beginArray(&ctx->bonjsonContext);
    increaseDepth(writer, true);
}

static void endContainer(const KSCrashReportWriter *const writer) {
    BonjsonWriterContext* ctx = getCtx(writer);
    ksbonjson_endContainer(&ctx->bonjsonContext);
    decreaseDepth(writer);
}

static void addTextLinesFromFile(const KSCrashReportWriter *const writer, const char *const key,
                                 const char *const filePath)
{
    return;
}

static ksbonjson_encodeStatus addEncodedData(const uint8_t* KSBONJSON_RESTRICT data,
                                             size_t dataLength,
                                             void* KSBONJSON_RESTRICT userData)
{
    ReportContext* ctx = (ReportContext*)userData;
    ksfu_writeBufferedWriter(&ctx->bufferedWriter, (const char*)data, (int)dataLength);
    ksfu_flushBufferedWriter(&ctx->bufferedWriter);
    return 0;
}

void bitdrift_initBONJSONReportWriter(KSCrashReportWriter *const writer, void* ctx)
{
    writer->addBooleanElement = addBooleanElement;
    writer->addFloatingPointElement = addFloatingPointElement;
    writer->addIntegerElement = addIntegerElement;
    writer->addUIntegerElement = addUIntegerElement;
    writer->addStringElement = addStringElement;
    writer->addTextFileElement = addTextFileElement;
    writer->addTextFileLinesElement = addTextLinesFromFile;
    writer->addJSONFileElement = addJSONElementFromFile;
    writer->addDataElement = addDataElement;
    writer->beginDataElement = beginDataElement;
    writer->appendDataElement = appendDataElement;
    writer->endDataElement = endDataElement;
    writer->addUUIDElement = addUUIDElement;
    writer->addJSONElement = addJSONElement;
    writer->beginObject = beginObject;
    writer->beginArray = beginArray;
    writer->endContainer = endContainer;
    writer->context = ctx;
    ksbonjson_beginEncode(getEncodeCtx(writer), addEncodedData, ctx);
}

void bitdrift_endBONJSONReport(KSCrashReportWriter *const writer)
{
    ksbonjson_endEncode(getEncodeCtx(writer));
}
