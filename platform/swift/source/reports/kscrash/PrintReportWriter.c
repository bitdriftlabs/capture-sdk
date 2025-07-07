//
//  PrintReportWriter.c
//  CrashTester
//
//  Created by Karl Stenerud on 02.07.25.
//

#include "PrintReportWriter.h"

#include <stdio.h>
#include <string.h>

typedef struct {
    bool isArray[1000];
    int indentLevel;
} writer_context;

static writer_context g_writer_context = {0};

static void indent(const KSCrashReportWriter *const writer) {
    writer_context* ctx = (writer_context*)writer->context;
    for(int i = 0; i < ctx->indentLevel; i++) {
        printf("    ");
    }
}

static void increase_indent(const KSCrashReportWriter *const writer, bool isArray) {
    writer_context* ctx = (writer_context*)writer->context;
    ctx->indentLevel++;
    ctx->isArray[ctx->indentLevel] = isArray;
}

static bool decrease_indent(const KSCrashReportWriter *const writer) {
    writer_context* ctx = (writer_context*)writer->context;
    bool wasArray = ctx->isArray[ctx->indentLevel];
    if(ctx->indentLevel > 0) {
        ctx->indentLevel--;
    }
    return wasArray;
}

static void addKey(const KSCrashReportWriter *const writer, const char *const key) {
    if(key != NULL) {
        printf("%s = ", key);
    }
}


static void addBooleanElement(const KSCrashReportWriter *const writer, const char *const key, const bool value)
{
    indent(writer);
    addKey(writer, key);
    if(value) {
        printf("true\n");
    } else {
        printf("false\n");
    }
}

static void addFloatingPointElement(const KSCrashReportWriter *const writer, const char *const key, const double value)
{
    indent(writer);
    addKey(writer, key);
    printf("%f\n", value);
}

static void addIntegerElement(const KSCrashReportWriter *const writer, const char *const key, const int64_t value)
{
    indent(writer);
    addKey(writer, key);
    printf("%lld\n", value);
}

static void addUIntegerElement(const KSCrashReportWriter *const writer, const char *const key, const uint64_t value)
{
    indent(writer);
    addKey(writer, key);
    printf("%llu\n", value);
}

static void addStringElement(const KSCrashReportWriter *const writer, const char *const key, const char *const value)
{
    indent(writer);
    addKey(writer, key);
    printf("\"%s\"\n", value);
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
    indent(writer);
    if (value == NULL) {
        printf("null\n");
    } else {
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

        printf("\"%s\"\n", uuidBuffer);
    }
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
    indent(writer);
    addKey(writer, key);
    printf("{\n");
    increase_indent(writer, false);
}

static void beginArray(const KSCrashReportWriter *const writer, const char *const key)
{
    indent(writer);
    addKey(writer, key);
    printf("[\n");
    increase_indent(writer, true);
}

static void endContainer(const KSCrashReportWriter *const writer) {
    bool wasArray = decrease_indent(writer);
    indent(writer);
    if(wasArray) {
        printf("]\n");
    } else {
        printf("}\n");
    }
}

static void addTextLinesFromFile(const KSCrashReportWriter *const writer, const char *const key,
                                 const char *const filePath)
{
    return;
}

void initPrintReportWriter(KSCrashReportWriter *const writer)
{
    memset(&g_writer_context, 0, sizeof(g_writer_context));
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
    writer->context = &g_writer_context;
}
