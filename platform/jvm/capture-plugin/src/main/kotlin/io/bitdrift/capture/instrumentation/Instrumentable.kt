// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

package io.bitdrift.capture.instrumentation

import com.android.build.api.instrumentation.ClassContext
import io.bitdrift.capture.SpanAddingClassVisitorFactory
import org.objectweb.asm.ClassVisitor
import org.objectweb.asm.MethodVisitor
import java.io.Serializable

interface Instrumentable<Visitor, InstrumentableContext> : Serializable {

    /**
     * Fully-qualified name of the instrumentable. Examples:
     * Class: androidx.sqlite.db.framework.FrameworkSQLiteDatabase
     * Method: query
     */
    val fqName: String get() = ""

    /**
     * Provides a visitor for this instrumentable. A visitor can be one of the visitors defined
     * in [ASM](https://asm.ow2.io/javadoc/org/objectweb/asm/package-summary.html)
     *
     * @param instrumentableContext A context of the instrumentable.
     * @param apiVersion Defines the ASM api version, usually provided from the parent
     * @param originalVisitor The original visitor that ASM provides us with before visiting code
     * @param parameters Parameters that are configured by users and passed via the Sentry gradle plugin
     */
    fun getVisitor(
            instrumentableContext: InstrumentableContext,
            apiVersion: Int,
            originalVisitor: Visitor,
            parameters: SpanAddingClassVisitorFactory.SpanAddingParameters
    ): Visitor

    /**
     * Defines whether this object is instrumentable or not based on [data]
     */
    fun isInstrumentable(data: InstrumentableContext): Boolean
}
interface ClassInstrumentable : Instrumentable<ClassVisitor, ClassContext> {

    override fun isInstrumentable(data: ClassContext): Boolean =
            fqName == data.currentClassData.className
}

interface MethodInstrumentable : Instrumentable<MethodVisitor, MethodContext> {

    override fun isInstrumentable(data: MethodContext): Boolean = fqName == data.name
}
