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

/**
 * JUnit suite run by Bazel's test runner. [ForkingTestLauncher] forks one JVM per top-level test class
 * and passes the classes to run through [TestDiscovery.CLASSES_PROPERTY]; without that property,
 * every discovered test class runs in the current JVM.
 *
 * Bazel's `--test_filter` does not reach tests inside this JUnit 3 style suite, so the suite applies
 * `TESTBRIDGE_TEST_ONLY` itself, matching the regex against `ClassName#methodName` as Bazel does.
 */
@RunWith(org.junit.runners.AllTests::class)
object TestSuite {
    @JvmStatic
    fun suite(): TestSuite {
        val suite = TestSuite()

        val classNames =
            System
                .getProperty(TestDiscovery.CLASSES_PROPERTY)
                ?.split(',')
                ?.filter { it.isNotEmpty() }
                ?: TestDiscovery.discover().flatMap { it.classNames }

        val filter = System.getenv("TESTBRIDGE_TEST_ONLY")?.takeIf { it.isNotEmpty() }?.let(::regexFilter)

        classNames
            .sortedDescending()
            .map { JUnit4TestAdapter(Class.forName(it)) }
            .filter { adapter -> filter == null || adapter.keepsAnyTest(filter) }
            .forEach(suite::addTest)

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
