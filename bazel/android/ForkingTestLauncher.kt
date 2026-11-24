// capture-sdk - bitdrift's client SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

@file:Suppress("InvalidPackageDeclaration")

package io.bitdrift.capture

import io.github.classgraph.ClassGraph
import org.w3c.dom.Document
import org.w3c.dom.Element
import java.io.File
import java.lang.management.ManagementFactory
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern
import javax.xml.parsers.DocumentBuilder
import javax.xml.parsers.DocumentBuilderFactory
import javax.xml.transform.OutputKeys
import javax.xml.transform.TransformerFactory
import javax.xml.transform.dom.DOMSource
import javax.xml.transform.stream.StreamResult
import kotlin.system.exitProcess

/** A top-level test class and its nested test classes; each group runs in its own JVM. */
data class TestGroup(
    val name: String,
    val classNames: List<String>,
    val testIds: List<String>,
)

/** Finds the test classes compiled into the current test target. */
object TestDiscovery {
    /** Comma-separated test classes that [TestSuite] runs instead of discovering them. */
    const val CLASSES_PROPERTY = "bitdrift.test.classes"

    private const val TEST_PACKAGE = "io.bitdrift.capture"
    private val junitTestAnnotation = org.junit.Test::class.java.name

    /**
     * Returns the test classes grouped by top-level class, so nested test classes share their outer
     * class's JVM. Test files declare one top-level test class, so this matches the per-file targets
     * the Bazel macro used to create.
     */
    fun discover(): List<TestGroup> =
        ClassGraph()
            .disableModuleScanning()
            .enableAnnotationInfo()
            .enableMethodInfo()
            .ignoreClassVisibility()
            .acceptPackages(TEST_PACKAGE)
            .scan()
            .use { scan ->
                scan
                    .getClassesWithMethodAnnotation(junitTestAnnotation)
                    .groupBy { it.name.substringBefore('$') }
                    .map { (name, classes) ->
                        TestGroup(
                            name = name,
                            classNames = classes.map { it.name }.sorted(),
                            testIds =
                                classes.flatMap { cls ->
                                    cls.declaredMethodInfo
                                        .filter { it.hasAnnotation(junitTestAnnotation) }
                                        .map { "${cls.name}#${it.name}" }
                                },
                        )
                    }.sortedBy { it.name }
            }
}

/**
 * Test entry point that runs each top-level test class in a fresh JVM.
 *
 * Robolectric's class-loader isolation breaks when the JNI library is loaded from more than one
 * class loader, so test classes cannot share a JVM (Gradle uses `forkEvery = 1` for the same
 * reason). Running them all from one Bazel test action instead of one action per class avoids
 * staging the same large runfiles once per class.
 *
 * Each child runs Bazel's JUnit runner with the parent's classpath and JVM flags. The launcher
 * applies Bazel sharding and `--test_filter` per test class, prints each child's output when it
 * finishes, merges the children's JUnit XML into `XML_OUTPUT_FILE`, and fails if any child fails.
 */
object ForkingTestLauncher {
    private const val BAZEL_TEST_RUNNER = "com.google.testing.junit.runner.BazelTestRunner"
    private const val FORKS_ENV = "BITDRIFT_TEST_FORKS"
    private const val DEFAULT_MAX_FORKS = 4
    private const val BANNER = "===================="

    // Variables that only the parent owns: children must neither shard nor signal premature exit.
    private val parentOnlyEnv =
        listOf(
            "TEST_TOTAL_SHARDS",
            "TEST_SHARD_INDEX",
            "TEST_SHARD_STATUS_FILE",
            "TEST_PREMATURE_EXIT_FILE",
            "XML_OUTPUT_FILE",
        )

    // Debugger agents listen on a fixed port, so a child that inherits one fails to start.
    private val parentOnlyJvmArgPrefixes = listOf("-agentlib:jdwp", "-Xrunjdwp")

    private data class ChildResult(
        val group: TestGroup,
        val exitCode: Int,
        val xml: File,
        val elapsedMs: Long,
        val error: String? = null,
    )

    private data class RunningChild(
        val group: TestGroup,
        val process: Process,
        val log: File,
    )

    private val running = mutableSetOf<RunningChild>()

    @JvmStatic
    fun main(args: Array<String>) {
        failUnderCoverage()

        System.getenv("TEST_SHARD_STATUS_FILE")?.let { File(it).apply { parentFile?.mkdirs() }.writeText("") }
        val prematureExit = System.getenv("TEST_PREMATURE_EXIT_FILE")?.let(::File)
        prematureExit?.apply { parentFile?.mkdirs() }?.writeText("")

        val groups = selectGroups(TestDiscovery.discover())
        val workDir = File(System.getenv("TEST_TMPDIR") ?: System.getProperty("java.io.tmpdir"), "forked-tests")
        workDir.mkdirs()

        val forks = (System.getenv(FORKS_ENV)?.toIntOrNull() ?: defaultForks()).coerceAtLeast(1)
        println("Running ${groups.size} test classes in separate JVMs, $forks at a time")

        // On a test timeout Bazel terminates this JVM: show what the unfinished children printed,
        // since their output is otherwise only printed when they exit.
        Runtime.getRuntime().addShutdownHook(Thread(::reportAndKillRunningChildren))

        val executor = Executors.newFixedThreadPool(forks) { task -> Thread(task).apply { isDaemon = true } }
        val results =
            try {
                groups
                    .mapIndexed { index, group ->
                        executor.submit(Callable { runChild(group, File(workDir, index.toString()), args) })
                    }.map { it.get() }
            } finally {
                executor.shutdownNow()
            }

        System.getenv("XML_OUTPUT_FILE")?.let { writeMergedXml(results, File(it)) }

        val failed = results.filter { it.exitCode != 0 }
        println()
        println("Ran ${results.size} test classes; ${failed.size} failed.")
        failed.forEach { println("FAILED: ${it.group.name} (${it.error ?: "exit code ${it.exitCode}"})") }

        // A shard may legitimately receive no classes, but an unsharded run with nothing to run
        // means discovery or --test_filter matched no tests.
        val sharded = (System.getenv("TEST_TOTAL_SHARDS")?.toIntOrNull() ?: 1) > 1
        val nothingRan = results.isEmpty() && !sharded
        if (nothingRan) println("No test classes matched.")

        prematureExit?.delete()
        exitProcess(if (failed.isEmpty() && !nothingRan) 0 else 1)
    }

    // Under `bazel coverage` the JaCoCo runner starts this launcher, but the forked children would
    // run without it and their coverage would be silently dropped.
    private fun failUnderCoverage() {
        if (System.getenv("JACOCO_MAIN_CLASS") != null) {
            System.err.println(
                "ForkingTestLauncher does not support coverage: forked test JVMs do not record it. " +
                    "Use Gradle for Android unit test coverage.",
            )
            exitProcess(1)
        }
    }

    private fun defaultForks(): Int = Runtime.getRuntime().availableProcessors().coerceAtMost(DEFAULT_MAX_FORKS)

    // Selection uses declared method names, so a filter on a Robolectric variant name such as
    // `Foo#bar[33]` only matches if it also matches `Foo#bar`.
    private fun selectGroups(all: List<TestGroup>): List<TestGroup> {
        val filter = System.getenv("TESTBRIDGE_TEST_ONLY")?.takeIf { it.isNotEmpty() }?.let(Pattern::compile)
        val matching = if (filter == null) all else all.filter { group -> group.testIds.any { filter.matcher(it).find() } }

        val totalShards = System.getenv("TEST_TOTAL_SHARDS")?.toIntOrNull() ?: 1
        val shardIndex = System.getenv("TEST_SHARD_INDEX")?.toIntOrNull() ?: 0
        return matching.filterIndexed { index, _ -> index % totalShards == shardIndex }
    }

    private fun childCommand(
        group: TestGroup,
        tmp: File,
        args: Array<String>,
    ): List<String> =
        listOf(File(System.getProperty("java.home"), "bin/java").path) +
            ManagementFactory.getRuntimeMXBean().inputArguments.filterNot { arg ->
                parentOnlyJvmArgPrefixes.any { arg.startsWith(it) }
            } +
            listOf(
                "-D${TestDiscovery.CLASSES_PROPERTY}=${group.classNames.joinToString(",")}",
                "-Djava.io.tmpdir=${tmp.path}",
                "-cp",
                System.getProperty("java.class.path"),
                BAZEL_TEST_RUNNER,
            ) + args

    private fun runChild(
        group: TestGroup,
        dir: File,
        args: Array<String>,
    ): ChildResult {
        val tmp = File(dir, "tmp").apply { mkdirs() }
        val log = File(dir, "output.log")
        val xml = File(dir, "test.xml")
        val start = System.nanoTime()

        val result =
            try {
                val builder = ProcessBuilder(childCommand(group, tmp, args)).redirectErrorStream(true).redirectOutput(log)
                builder.environment().apply {
                    parentOnlyEnv.forEach { remove(it) }
                    put("XML_OUTPUT_FILE", xml.path)
                    put("TEST_TMPDIR", tmp.path)
                }

                val child = RunningChild(group, builder.start(), log)
                synchronized(running) { running.add(child) }
                val exitCode = child.process.waitFor()
                synchronized(running) { running.remove(child) }
                ChildResult(group, exitCode, xml, TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start))
            } catch (e: Exception) {
                ChildResult(group, -1, xml, TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start), "launcher error: $e")
            }

        synchronized(this) {
            val status = if (result.exitCode == 0) "PASSED" else "FAILED (${result.error ?: "exit code ${result.exitCode}"})"
            println("$BANNER ${group.name}: $status in ${result.elapsedMs}ms $BANNER")
            if (log.isFile) log.forEachLine { println(it) }
            System.out.flush()
        }
        return result
    }

    private fun reportAndKillRunningChildren() {
        val unfinished = synchronized(running) { running.toList() }
        unfinished.forEach { child ->
            child.process.destroyForcibly()
            synchronized(this) {
                println("$BANNER ${child.group.name}: DID NOT FINISH (launcher terminated) $BANNER")
                if (child.log.isFile) child.log.forEachLine { println(it) }
                System.out.flush()
            }
        }
    }

    private fun writeMergedXml(
        results: List<ChildResult>,
        output: File,
    ) {
        val builder = DocumentBuilderFactory.newInstance().newDocumentBuilder()
        val merged = builder.newDocument()
        val root = merged.createElement("testsuites")
        merged.appendChild(root)

        results.forEach { result ->
            val suites = readSuites(builder, result.xml)
            suites.forEach { root.appendChild(merged.importNode(it, true)) }

            // A child can fail without reporting a failed test: it crashed before writing results,
            // wrote unreadable XML, or exited non-zero after its tests passed (e.g. a crash during
            // JVM shutdown). Record a failed case so the XML agrees with the exit code.
            val reportedFailure =
                suites.any { (it.getAttribute("failures").toIntOrNull() ?: 0) + (it.getAttribute("errors").toIntOrNull() ?: 0) > 0 }
            if (result.exitCode != 0 && !reportedFailure) {
                root.appendChild(crashedSuite(merged, result, wroteResults = suites.isNotEmpty()))
            }
        }

        output.parentFile?.mkdirs()
        TransformerFactory.newInstance().newTransformer().apply {
            setOutputProperty(OutputKeys.INDENT, "yes")
            transform(DOMSource(merged), StreamResult(output))
        }
    }

    private fun readSuites(
        builder: DocumentBuilder,
        xml: File,
    ): List<Element> {
        if (!xml.isFile) return emptyList()
        val root =
            try {
                builder.parse(xml).documentElement
            } catch (e: Exception) {
                println("Could not read $xml: $e")
                return emptyList()
            }
        if (root.tagName == "testsuite") return listOf(root)
        val list = root.getElementsByTagName("testsuite")
        return (0 until list.length).map { list.item(it) as Element }
    }

    private fun crashedSuite(
        doc: Document,
        result: ChildResult,
        wroteResults: Boolean,
    ): Element {
        val suite = doc.createElement("testsuite")
        suite.setAttribute("name", result.group.name)
        suite.setAttribute("tests", "1")
        suite.setAttribute("errors", "1")
        suite.setAttribute("failures", "0")
        suite.setAttribute("time", (result.elapsedMs / 1000.0).toString())
        val case = doc.createElement("testcase")
        case.setAttribute("name", "forked JVM")
        case.setAttribute("classname", result.group.name)
        val error = doc.createElement("error")
        val detail = result.error ?: "exited with code ${result.exitCode}"
        val outcome = if (wroteResults) "after reporting no failed tests" else "without readable test results"
        error.setAttribute("message", "Forked JVM $detail $outcome")
        case.appendChild(error)
        suite.appendChild(case)
        return suite
    }
}
