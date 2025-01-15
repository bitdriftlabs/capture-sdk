// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

@file:Suppress("InvalidPackageDeclaration")

package io.bitdrift.capture

import io.github.classgraph.ClassGraph
import junit.framework.JUnit4TestAdapter
import junit.framework.TestSuite
import org.junit.runner.RunWith

@RunWith(org.junit.runners.AllTests::class)
object TestSuite {
    private val junitTestAnnotation = org.junit.Test::class.java.name

    @JvmStatic
    fun suite(): TestSuite {
        val suite = TestSuite()

        val scan =
            ClassGraph()
                .disableModuleScanning()
                .enableAnnotationInfo()
                .enableMethodInfo()
                .ignoreClassVisibility()
                .acceptPackages("io.bitdrift.capture")
                .scan()
        scan
            .getClassesWithMethodAnnotation(junitTestAnnotation)
            .asSequence()
            .sortedByDescending { it.name }
            .map { JUnit4TestAdapter(it.loadClass()) }
            .forEach(suite::addTest)

        return suite
    }
}
