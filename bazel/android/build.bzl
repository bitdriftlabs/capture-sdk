load("@rules_detekt//detekt:defs.bzl", "detekt")
load(
    "@rules_kotlin//kotlin:android.bzl",
    "kt_android_library",
    "kt_android_local_test",
)
load("@rules_kotlin//kotlin:jvm.bzl", "kt_jvm_library")
load("@rules_kotlin//kotlin:lint.bzl", "ktlint_fix", "ktlint_test")
load(":robolectric.bzl", "robolectric_android_all")

# Configures a kt_android_library with lint targets. `generated_srcs` are compiled with `srcs` but
# not linted.
def bitdrift_kt_android_library(name, srcs, generated_srcs = [], require_javadocs = True, **args):
    kt_android_library(
        name = name,
        srcs = srcs + generated_srcs,
        **args
    )

    _jvm_lint_support(
        name,
        native.glob(srcs, exclude = ["**/binformat/**"]),
        require_javadocs,
    )

def bitdrift_kt_jvm_library(name, srcs, require_javadocs = True, **args):
    kt_jvm_library(
        name = name,
        srcs = srcs,
        **args
    )

    _jvm_lint_support(name, srcs, require_javadocs)

# Test JVMs each test target's launcher runs at once; also the CPU it reserves.
_TEST_FORKS = 4

def bitdrift_kt_android_local_test(
        name,
        robolectric_sdks,
        deps = [],
        jvm_flags = [],
        shard_count = 8,
        timeout = "long",
        tags = [],
        env = {},
        exec_properties = {},
        **kwargs):
    """One sharded test target for every `src/test/**/*Test.kt` file in the package.

    Args:
      name: Test target name.
      robolectric_sdks: API levels the tests select with `@Config(sdk = [...])`. Only these
        android-all jars are staged; see robolectric.bzl.
      deps: Dependencies of the tests and of the non-test helper sources.
      jvm_flags: Extra JVM flags, passed on to every forked test JVM.
      shard_count: Bazel test shards. Each shard is one test action that stages the full runfiles
        (about 2 GB for capture), so more shards shorten the critical path at the cost of cache
        reads.
      timeout: Bazel test timeout for each shard.
      tags: Test tags; a `cpu:` reservation for the forked JVMs is added.
      env: Test environment, passed to every forked test JVM.
      exec_properties: Remote execution properties; EstimatedCPU is added.
      **kwargs: Passed to kt_android_local_test.
    """
    lib_deps = native.glob(["src/test/**/*.kt"], exclude = ["src/test/**/*Test.kt"], allow_empty = True)

    if len(lib_deps) != 0:
        lib_name = "_{}_lib".format(name)
        associates = kwargs.pop("associates", [])

        # We want the tests below to be able to depend on non-test files defined within this package,
        # so generate a lib target containing everything that doesn't look like a test and depend on that.
        # This means we get no cross-test file dependency, but test files depend on non-test files.
        # The lib takes the same associates as the tests so helpers (fakes, utils) can implement
        # `internal` declarations of the library under test.
        kt_android_library(
            name = lib_name,
            srcs = native.glob(["src/test/**/*.kt"], exclude = ["**/*Test.kt"]),
            deps = deps,
            associates = associates,
            testonly = True,
        )

        # Associating (rather than plain-depending) on the lib keeps the tests in the same Kotlin
        # module as the helpers, so `internal` helpers are visible from test files.
        kwargs["associates"] = associates + [":" + lib_name]
        deps = []

    java_test_files = native.glob(["src/test/**/*.java"], allow_empty = True)

    android_all = "_{}_robolectric".format(name)
    robolectric_android_all(name = android_all, sdks = robolectric_sdks)

    # Robolectric does some class loader magic which doesn't work well with loading a static
    # library, so each test class must run in its own JVM (Gradle uses `forkEvery = 1` for
    # the same reason). Rather than one Bazel test target per file, which stages the same large
    # runfiles (JDK, Robolectric android-all jars, deploy jar, JNI library) once per file, a single
    # sharded target runs ForkingTestLauncher, which forks one JVM per top-level test class.
    kt_android_local_test(
        name = name,
        custom_package = "io.bitdrift.capture",
        main_class = "io.bitdrift.capture.ForkingTestLauncher",
        test_class = "io.bitdrift.capture.TestSuite",
        deps = deps + [
            ":" + android_all,
            "//bazel/android:test_suite_lib",
        ],
        manifest = "//platform/jvm:AndroidManifest.xml",
        robolectric_properties_file = ":{}.properties".format(android_all),
        jvm_flags = jvm_flags + ["-Dcapture_api_url=https://localhost:1234"],
        srcs = native.glob(["src/test/**/*Test.kt"]) + java_test_files,
        shard_count = shard_count,
        # A shard runs many test classes, _TEST_FORKS at a time, so it needs more than the default
        # 300s that each per-class target used to get.
        timeout = timeout,
        env = dict(env, BITDRIFT_TEST_FORKS = str(_TEST_FORKS)),
        # Reserve the CPUs the forked JVMs use: `cpu:N` for Bazel's local scheduler,
        # EstimatedCPU for BuildBuddy's executor task sizing.
        tags = tags + ["cpu:{}".format(_TEST_FORKS)],
        exec_properties = dict(exec_properties, EstimatedCPU = str(_TEST_FORKS)),
        **kwargs
    )

    _jvm_lint_support(name, native.glob(["src/test/**/*.kt"]), False)

# Configures ktlint and detekt for the provided sources.
def _jvm_lint_support(name, srcs, require_javadocs):
    ktlint_fix(
        name = "_{}_ktlint_fix".format(name),
        srcs = srcs,
        visibility = ["//visibility:public"],
    )

    ktlint_test(
        name = "_{}_ktlint_test".format(name),
        srcs = srcs,
    )

    cfgs = ["//bazel/android:detekt.yml"]

    if require_javadocs:
        cfgs.append("//bazel/android:detekt_javadocs.yml")

    detekt(
        name = name + "_detekt_lint",
        srcs = srcs,
        tags = ["detekt_target"],
        build_upon_default_config = True,
        cfgs = cfgs,
    )
