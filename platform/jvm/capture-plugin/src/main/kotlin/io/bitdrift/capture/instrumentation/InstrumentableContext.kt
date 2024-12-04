// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

package io.bitdrift.capture.instrumentation

import com.android.build.api.instrumentation.ClassData
import com.android.build.gradle.internal.instrumentation.ClassContextImpl
import com.android.build.gradle.internal.instrumentation.ClassesDataCache
import com.android.build.gradle.internal.instrumentation.ClassesHierarchyResolver

data class MethodContext(
        val access: Int,
        val name: String?,
        val descriptor: String?,
        val signature: String?,
        val exceptions: List<String>?
)

fun ClassData.toClassContext() =
        ClassContextImpl(this, ClassesHierarchyResolver.Builder(ClassesDataCache()).build())