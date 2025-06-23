// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

@file:Suppress("UnstableApiUsage")

package io.bitdrift.capture.instrumentation.fakes

import com.android.build.api.instrumentation.ClassContext
import com.android.build.api.instrumentation.ClassData

data class TestClassData(
    override val className: String,
    override val classAnnotations: List<String> = emptyList(),
    override val interfaces: List<String> = emptyList(),
    override val superClasses: List<String> = emptyList()
) : ClassData

data class TestClassContext(
    override val currentClassData: ClassData,
    private val classLoader: (String) -> ClassData? = { null }
) : ClassContext {

    constructor(className: String) : this(TestClassData(className))

    constructor(className: String, classLoader: (String) -> ClassData?) :
        this(TestClassData(className), classLoader)

    override fun loadClassData(className: String): ClassData? = classLoader(className)
}
