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
        frameType: Byte, // e.g. FrameType.JVM
        builder: FlatBufferBuilder,
        frameData: FrameData,
    ): Int =
        Frame.createFrame(
            builder,
            frameType,
            builder.createString(frameData.className),
            frameData.symbolName?.let { builder.createString(it) } ?: 0,
            buildSourceFile(builder, frameData.fileName, frameData.lineNumber),
            0,
            0u,
            0u,
            0,
            0,
            0,
            0u,
            false,
            0,
        )

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
    val className: String,
    val symbolName: String? = null,
    val fileName: String? = null,
    val lineNumber: Long? = null,
)
