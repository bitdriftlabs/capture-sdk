"""Generate schema sources on Linux in Linux-enabled RBE configurations."""

_LINUX_PLATFORM = "//bazel/platforms:buildbuddy_linux_x86_64"
_LINUX_LLVM = "@llvm//toolchain:all"
_PLATFORMS = "//command_line_option:platforms"
_EXECUTION_PLATFORMS = "//command_line_option:extra_execution_platforms"
_EXTRA_TOOLCHAINS = "//command_line_option:extra_toolchains"
_XCODE_VERSION = "//command_line_option:xcode_version"
_SIMULATOR_VERSION = "//command_line_option:ios_simulator_version"

def _linux_configuration(settings, _attr):
    # The Mac test transition removes Linux LLVM. Restore it for portable tools.
    return {
        _PLATFORMS: [_LINUX_PLATFORM],
        _EXECUTION_PLATFORMS: [_LINUX_PLATFORM],
        # Xcode 26 and 27 consume the same generated sources.
        _XCODE_VERSION: None,
        _SIMULATOR_VERSION: None,
        _EXTRA_TOOLCHAINS: [_LINUX_LLVM] + [
            toolchain
            for toolchain in settings[_EXTRA_TOOLCHAINS]
            if toolchain != _LINUX_LLVM
        ],
    }

_linux_transition = transition(
    implementation = _linux_configuration,
    inputs = [_EXTRA_TOOLCHAINS],
    outputs = [_PLATFORMS, _EXECUTION_PLATFORMS, _EXTRA_TOOLCHAINS, _XCODE_VERSION, _SIMULATOR_VERSION],
)

def _schema_output_impl(ctx):
    return [DefaultInfo(files = ctx.attr.source[0][DefaultInfo].files)]

_linux_schema_output = rule(
    implementation = _schema_output_impl,
    attrs = {
        "source": attr.label(cfg = _linux_transition, mandatory = True),
        "_allowlist_function_transition": attr.label(
            default = "@bazel_tools//tools/allowlists/function_transition_allowlist",
        ),
    },
)

def schema_codegen(name, out, tool, args, srcs = []):
    generator = name + "_generate"
    native.genrule(
        name = generator,
        srcs = srcs,
        outs = [out],
        cmd = "$(location {}) {} > $@".format(tool, args),
        tags = ["manual"],
        tools = [tool],
    )
    _linux_schema_output(
        name = name + "_on_linux",
        source = ":" + generator,
        tags = ["manual"],
    )
    native.alias(
        name = name,
        actual = select({
            ":linux_schema_codegen": ":" + name + "_on_linux",
            "//conditions:default": ":" + generator,
        }),
        visibility = ["//visibility:public"],
    )
