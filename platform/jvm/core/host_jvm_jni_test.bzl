"""A host-JVM test wrapper that uses Bazel's selected Java runtime."""

def _host_jvm_jni_test_impl(ctx):
    java_runtime = ctx.toolchains["@bazel_tools//tools/jdk:runtime_toolchain_type"].java_runtime
    launcher = ctx.actions.declare_file(ctx.label.name + ".sh")

    ctx.actions.expand_template(
        template = ctx.file.wrapper_template,
        output = launcher,
        substitutions = {
            "__JAVA_HOME_RUNFILES_PATH__": java_runtime.java_home_runfiles_path,
            "__TEST_BINARY_RUNFILES_PATH__": ctx.executable.test_binary.short_path,
        },
        is_executable = True,
    )

    runfiles = ctx.runfiles(files = [ctx.executable.test_binary])
    runfiles = runfiles.merge(ctx.attr.test_binary[DefaultInfo].default_runfiles)
    runfiles = runfiles.merge(
        ctx.runfiles(transitive_files = java_runtime.files),
    )
    return [DefaultInfo(executable = launcher, runfiles = runfiles)]

host_jvm_jni_test = rule(
    implementation = _host_jvm_jni_test_impl,
    test = True,
    attrs = {
        "test_binary": attr.label(
            executable = True,
            cfg = "target",
            mandatory = True,
        ),
        "wrapper_template": attr.label(
            allow_single_file = True,
            mandatory = True,
        ),
    },
    toolchains = ["@bazel_tools//tools/jdk:runtime_toolchain_type"],
)
