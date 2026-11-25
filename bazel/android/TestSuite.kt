// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

@file:Suppress("InvalidPackageDeclaration")

package io.bitdrift.capture

import junit.framework.JUnit4TestAdapter
import junit.framework.TestSuite
import org.junit.runner.Description
import org.junit.runner.RunWith
import org.junit.runner.manipulation.Filter
import org.junit.runner.manipulation.NoTestsRemainException
import kotlin.system.exitProcess

/**
 * JUnit suite run by Bazel's test runner. [ForkingTestLauncher] forks one JVM per top-level test class
 * and passes the classes to run through [TestDiscovery.CLASSES_PROPERTY]; without that property,
 * every discovered test class runs in the current JVM.
 *
 * Bazel's `--test_filter` does not reach tests inside this JUnit 3 style suite, so the suite applies
 * the filter itself, matching the regex against `ClassName#methodName` as Bazel does. The forked
 * JVM gets it as [TestDiscovery.FILTER_PROPERTY]; a JVM with no matching test exits successfully
 * without running anything.
 */
@RunWith(org.junit.runners.AllTests::class)
object TestSuite {
    @JvmStatic
    fun suite(): TestSuite {
        val suite = TestSuite()

        val forked = System.getProperty(TestDiscovery.CLASSES_PROPERTY) != null
        val classNames =
            System
                .getProperty(TestDiscovery.CLASSES_PROPERTY)
                ?.split(',')
                ?.filter { it.isNotEmpty() }
                ?: TestDiscovery.discover().flatMap { it.classNames }

        val regex = System.getProperty(TestDiscovery.FILTER_PROPERTY) ?: System.getenv("TESTBRIDGE_TEST_ONLY")
        val filter = regex?.takeIf { it.isNotEmpty() }?.let(::regexFilter)

        classNames
            .sortedDescending()
            .map { JUnit4TestAdapter(Class.forName(it)) }
            .filter { adapter -> filter == null || adapter.keepsAnyTest(filter) }
            .forEach(suite::addTest)

        // Bazel's runner fails a suite with no tests. In a forked JVM that only means this class has
        // no match; ForkingTestLauncher decides whether the filter matched anything at all.
        if (filter != null && forked && suite.countTestCases() == 0) {
            println("No test in ${classNames.joinToString()} matches $regex.")
            exitProcess(0)
        }

        return suite
    }

    private fun JUnit4TestAdapter.keepsAnyTest(filter: Filter): Boolean =
        try {
            filter(filter)
            true
        } catch (_: NoTestsRemainException) {
            false
        }

    private fun regexFilter(regex: String): Filter {
        val pattern = Regex(regex)
        return object : Filter() {
            override fun shouldRun(description: Description): Boolean =
                description.isSuite || pattern.containsMatchIn("${description.className}#${description.methodName}")

            override fun describe(): String = "tests matching $regex"
        }
    }
}
