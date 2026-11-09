"""On-device Android instrumentation tests without Gradle.

rules_android does not provide android_instrumentation_test, so this builds a self-instrumenting
test APK and runs it with adb against whichever device or emulator is connected.
"""

load("@rules_android//android:rules.bzl", "android_binary")
load("@rules_shell//shell:sh_test.bzl", "sh_test")

def bitdrift_android_instrumentation_test(
        name,
        package,
        manifest,
        deps,
        runner = "androidx.test.runner.AndroidJUnitRunner",
        tags = [],
        **kwargs):
    """Builds `<name>_apk` and a device-backed `<name>` test.

    Args:
      name: Test target name.
      package: Application ID of the test APK; must match the manifest and its instrumentation.
      manifest: Manifest declaring the self-targeting <instrumentation>.
      deps: Libraries holding the test classes and their runtime dependencies.
      runner: Instrumentation runner class.
      tags: Extra tags for the test target.
      **kwargs: Passed to android_binary.
    """
    apk_name = name + "_apk"
    android_binary(
        name = apk_name,
        testonly = True,
        custom_package = package,
        manifest = manifest,
        manifest_values = {"applicationId": package},
        deps = deps,
        **kwargs
    )

    sh_test(
        name = name,
        srcs = ["//bazel/android:run_instrumentation.sh"],
        args = [
            "$(rootpath :{}.apk)".format(apk_name),
            package,
            runner,
        ],
        data = [":{}.apk".format(apk_name)],
        env_inherit = [
            "ANDROID_HOME",
            "ANDROID_SDK_ROOT",
            "ANDROID_SERIAL",
            "HOME",
            "INSTRUMENTATION_ARGS",
            "PATH",
        ],
        # Needs a device reachable through the host's adb server, so it cannot be sandboxed,
        # executed remotely, or cached: the result depends on the attached device.
        tags = tags + [
            "external",
            "local",
            "manual",
            "no-remote",
            "requires-device",
        ],
        timeout = "long",
    )
