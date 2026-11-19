"""Run an existing Apple test in its macOS target configuration under mixed RBE."""

_MACOS_PLATFORM = "//bazel/platforms:buildbuddy_macos_arm64"
_XCODE_VERSION = "//command_line_option:xcode_version"
_SIMULATOR_VERSION = "//command_line_option:ios_simulator_version"
_PLATFORMS = "//command_line_option:platforms"

def _macos_configuration(settings, attr):
    return {
        _PLATFORMS: [_MACOS_PLATFORM],
        _XCODE_VERSION: attr.xcode or settings[_XCODE_VERSION],
        _SIMULATOR_VERSION: attr.simulator or settings[_SIMULATOR_VERSION],
    }

_macos_transition = transition(
    implementation = _macos_configuration,
    inputs = [_XCODE_VERSION, _SIMULATOR_VERSION],
    outputs = [_PLATFORMS, _XCODE_VERSION, _SIMULATOR_VERSION],
)

def _macos_rbe_test_impl(ctx):
    test = ctx.attr.test[0]
    info = test[DefaultInfo]
    executable = ctx.actions.declare_file(ctx.label.name)
    ctx.actions.symlink(
        output = executable,
        target_file = info.files_to_run.executable,
        is_executable = True,
    )
    providers = [
        DefaultInfo(executable = executable, runfiles = info.default_runfiles),
    ]
    if testing.ExecutionInfo in test:
        providers.append(test[testing.ExecutionInfo])
    if RunEnvironmentInfo in test:
        environment = test[RunEnvironmentInfo]
        providers.append(RunEnvironmentInfo(
            environment = environment.environment | ctx.attr.runner_env,
            inherited_environment = environment.inherited_environment,
        ))
    elif ctx.attr.runner_env:
        providers.append(RunEnvironmentInfo(environment = ctx.attr.runner_env))
    return providers

_macos_rbe_test = rule(
    implementation = _macos_rbe_test_impl,
    attrs = {
        "runner_env": attr.string_dict(),
        "simulator": attr.string(),
        "test": attr.label(cfg = _macos_transition, mandatory = True),
        "xcode": attr.string(),
        "_allowlist_function_transition": attr.label(
            default = "@bazel_tools//tools/allowlists/function_transition_allowlist",
        ),
    },
    test = True,
)

def macos_rbe_test(name, test, xcode = "", simulator = "", xctest = False, timeout = "moderate"):
    _macos_rbe_test(
        name = name,
        test = test,
        xcode = xcode,
        simulator = simulator,
        runner_env = {
            "APPLE_SDK_PLATFORM": "iPhoneSimulator",
            "STARTUP_TIMEOUT_SEC": "300",
        } if xctest else {},
        timeout = timeout,
        target_compatible_with = select({
            "//bazel/platforms:mixed_rbe": [],
            "//conditions:default": ["@platforms//:incompatible"],
        }),
        exec_compatible_with = ["@platforms//os:macos", "@platforms//cpu:arm64"],
        tags = ["macos_only"],
    )
