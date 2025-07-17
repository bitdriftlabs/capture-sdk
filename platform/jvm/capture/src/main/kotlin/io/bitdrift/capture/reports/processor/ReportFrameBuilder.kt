// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

package io.bitdrift.capture.reports.processor

import com.google.flatbuffers.FlatBufferBuilder
import io.bitdrift.capture.reports.binformat.v1.Frame
import io.bitdrift.capture.reports.binformat.v1.SourceFile

/**
 * A builder that allows you to create a [io.bitdrift.capture.reports.binformat.v1.Frame] with
 * the main relevant fields
 */
internal object ReportFrameBuilder {
    /**
     * Builds a [io.bitdrift.capture.reports.binformat.v1.Frame]
     */
    fun build(
        frameType: Byte, // e.g. FrameType.AndroidNative
        builder: FlatBufferBuilder,
        frameData: FrameData,
    ): Int {
        val classNameOffset = frameData.className?.let { builder.createString(it) } ?: 0
        val symbolNameOffset = frameData.symbolName?.let { builder.createString(it) } ?: 0
        val sourceFileOffset = buildSourceFile(builder, frameData.fileName, frameData.lineNumber)
        val imageIdOffset = frameData.imageId?.let { builder.createString(it) } ?: 0
        return Frame.createFrame(
            builder,
            type = frameType,
            classNameOffset = classNameOffset,
            symbolNameOffset = symbolNameOffset,
            sourceFileOffset = sourceFileOffset,
            imageIdOffset = imageIdOffset,
            frameAddress = frameData.frameAddress ?: 0u,
            symbolAddress = frameData.symbolAddress ?: 0u,
            registersOffset = 0,
            stateOffset = 0,
            frameStatus = 0,
            originalIndex = 0u,
            inApp = false,
            symbolicatedNameOffset = 0,
        )
    }

    /**
     * Builds offset from a nullable string.
     *
     * NOTE: Defaults to 0 if nullable string
     */
    fun FlatBufferBuilder.toOffset(originalValue: String?): Int = originalValue?.let { createString(it) } ?: 0

    private fun buildSourceFile(
        builder: FlatBufferBuilder,
        fileName: String?,
        lineNumber: Long?,
    ): Int =
        if (fileName != null) {
            val path = builder.createString(fileName)
            SourceFile.createSourceFile(
                builder,
                path,
                lineNumber ?: 0,
                0,
            )
        } else {
            0
        }
}

internal data class FrameData(
    val className: String? = null,
    val symbolName: String? = null,
    val fileName: String? = null,
    val lineNumber: Long? = null,
    val imageId: String? = null,
    val frameAddress: ULong? = null,
    val symbolAddress: ULong? = null,
)
