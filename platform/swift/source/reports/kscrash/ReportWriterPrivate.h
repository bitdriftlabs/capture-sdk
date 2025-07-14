//
//  ReportWriterPrivate.h
//  Capture
//
//  Created by Karl Stenerud on 14.07.25.
//

#pragma once

#ifdef __cplusplus
extern "C" {
#endif

#include <stdbool.h>
#include <stdint.h>

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
