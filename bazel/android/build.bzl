load(
    "@io_bazel_rules_kotlin//kotlin:android.bzl",
    "kt_android_library",
    "kt_android_local_test",
)
load("@io_bazel_rules_kotlin//kotlin:jvm.bzl", "kt_jvm_library")
load("@io_bazel_rules_kotlin//kotlin:lint.bzl", "ktlint_fix", "ktlint_test")
load("@rules_detekt//detekt:defs.bzl", "detekt")

# Configures a kt_android_library with lint targets.
def bitdrift_kt_android_library(name, srcs, require_javadocs = True, **args):
    kt_android_library(
        name = name,
        srcs = srcs,
        **args
    )

    _jvm_lint_support(name, srcs, require_javadocs)

def bitdrift_kt_jvm_library(name, srcs, require_javadocs = True, **args):
    kt_jvm_library(
        name = name,
        srcs = srcs,
        **args
    )

    _jvm_lint_support(name, srcs, require_javadocs)

def bitdrift_kt_android_local_test(name, deps = [], jvm_flags = [], **kwargs):
    lib_deps = native.glob(["src/test/**/*.kt"], exclude = ["src/test/**/*Test.kt"])

    if len(lib_deps) != 0:
        # We want the tests below to be able to depend on non-test files defined within this package,
        # so generate a lib target containing everything that doesn't look like a test and depend on that.
        # This means we get no cross-test file dependency, but test files depend on non-test files.
        kt_android_library(
            name = "_{}_lib".format(name),
            srcs = native.glob(["src/test/**/*.kt"], exclude = ["**/*Test.kt"]),
            deps = deps,
        )

        deps = [":_{}_lib".format(name)]

    # Robolectric does some class loader magic which doesn't work well with loading a static
    # library. To get around this, we create one test target per source file to avoid multiple
    # class loaders being used. An alternative to this that we might consider is to avoid loading
    # the dynamic library in each test and instead delegate that to the test runner which can
    # hopefully ensure that the load happens exactly once.
    for s in native.glob(["src/test/**/*Test.kt"]):
        kt_android_local_test(
            name = "_{}_test".format(s),
            custom_package = "io.bitdrift.capture",
            test_class = "io.bitdrift.capture.TestSuite",
            deps = deps + ["//bazel/android:test_suite_lib"],
            manifest = "//platform/jvm:AndroidManifest.xml",
            jvm_flags = jvm_flags + ["-Dcapture_api_url=https://localhost:1234"],
            srcs = [s],
            **kwargs
        )

    _jvm_lint_support(name, native.glob(["src/test/**/*.kt"]), False)

# Configures ktlint and detekt for the provided sources.
def _jvm_lint_support(name, srcs, require_javadocs):
    ktlint_fix(
        name = "_{}_ktlint_fix".format(name),
        srcs = srcs,
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
