// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

#pragma once

#include <stdbool.h>
#include <stdint.h>

#ifdef __cplusplus
extern "C" {
#endif

typedef struct BitdriftReportWriter {
    /** Add a boolean element to the report.
     *
     * @param writer This writer.
     *
     * @param name The name to give this element.
     *
     * @param value The value to add.
     */
    bool (*addBooleanElement)(const struct BitdriftReportWriter *writer, const char *name, bool value);
    
    /** Add a floating point element to the report.
     *
     * @param writer This writer.
     *
     * @param name The name to give this element.
     *
     * @param value The value to add.
     */
    bool (*addFloatingPointElement)(const struct BitdriftReportWriter *writer, const char *name, double value);
    
    /** Add an integer element to the report.
     *
     * @param writer This writer.
     *
     * @param name The name to give this element.
     *
     * @param value The value to add.
     */
    bool (*addIntegerElement)(const struct BitdriftReportWriter *writer, const char *name, int64_t value);
    
    /** Add an unsigned integer element to the report.
     *
     * @param writer This writer.
     *
     * @param name The name to give this element.
     *
     * @param value The value to add.
     */
    bool (*addUIntegerElement)(const struct BitdriftReportWriter *writer, const char *name, uint64_t value);
    
    /** Add a string element to the report.
     *
     * @param writer This writer.
     *
     * @param name The name to give this element.
     *
     * @param value The value to add.
     */
    bool (*addStringElement)(const struct BitdriftReportWriter *writer, const char *name, const char *value);
    
    /** Add a UUID element to the report.
     *
     * @param writer This writer.
     *
     * @param name The name to give this element.
     *
     * @param value A pointer to the binary UUID data.
     */
    bool (*addUUIDElement)(const struct BitdriftReportWriter *writer, const char *name, const unsigned char *value);
    
    /** Begin a new object container.
     *
     * @param writer This writer.
     *
     * @param name The name to give this element.
     */
    bool (*beginObject)(const struct BitdriftReportWriter *writer, const char *name);
    
    /** Begin a new array container.
     *
     * @param writer This writer.
     *
     * @param name The name to give this element.
     */
    bool (*beginArray)(const struct BitdriftReportWriter *writer, const char *name);
    
    /** Leave the current container, returning to the next higher level
     *  container.
     *
     * @param writer This writer.
     */
    bool (*endContainer)(const struct BitdriftReportWriter *writer);
    
    /** Internal contextual data for the writer */
    void *context;
    
} BitdriftReportWriter;

#ifdef __cplusplus
}
#endif
