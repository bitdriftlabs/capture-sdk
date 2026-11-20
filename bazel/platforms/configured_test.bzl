"""Run tests with a target-specific configuration in mixed RBE CI."""

_MACOS_PLATFORM = "//bazel/platforms:buildbuddy_macos_arm64"
_XCODE_VERSION = "//command_line_option:xcode_version"
_SIMULATOR_VERSION = "//command_line_option:ios_simulator_version"
_PLATFORMS = "//command_line_option:platforms"
_EXECUTION_PLATFORMS = "//command_line_option:extra_execution_platforms"
_EXTRA_TOOLCHAINS = "//command_line_option:extra_toolchains"
_LINUX_LLVM_TOOLCHAIN = "@llvm//toolchain:all"
_MACOS_EXEC_CONSTRAINTS = ["@platforms//os:macos"]
_COMPILATION_MODE = "//command_line_option:compilation_mode"
_RUSTC_FLAGS = "@rules_rust//rust/settings:extra_rustc_flag"

def _macos_configuration(settings, attr):
    config = {
        _XCODE_VERSION: attr.xcode or settings[_XCODE_VERSION],
        _SIMULATOR_VERSION: attr.simulator or settings[_SIMULATOR_VERSION],
    }
    if not attr.remote:
        return config | {
            _PLATFORMS: settings[_PLATFORMS],
            _EXECUTION_PLATFORMS: settings[_EXECUTION_PLATFORMS],
            _EXTRA_TOOLCHAINS: settings[_EXTRA_TOOLCHAINS],
        }
    return config | {
        _PLATFORMS: [_MACOS_PLATFORM],
        # Apple toolchain helpers also use exec transitions of their own.
        _EXECUTION_PLATFORMS: [_MACOS_PLATFORM] + [
            platform
            for platform in settings[_EXECUTION_PLATFORMS]
            if platform != _MACOS_PLATFORM
        ],
        # Use the Xcode toolchain for Apple targets and their exec dependencies.
        _EXTRA_TOOLCHAINS: [
            toolchain
            for toolchain in settings[_EXTRA_TOOLCHAINS]
            if toolchain != _LINUX_LLVM_TOOLCHAIN
        ],
    }

_macos_transition = transition(
    implementation = _macos_configuration,
    inputs = [_EXECUTION_PLATFORMS, _EXTRA_TOOLCHAINS, _PLATFORMS, _XCODE_VERSION, _SIMULATOR_VERSION],
    outputs = [_EXECUTION_PLATFORMS, _EXTRA_TOOLCHAINS, _PLATFORMS, _XCODE_VERSION, _SIMULATOR_VERSION],
)

def _release_configuration(settings, _attr):
    # Match the optimized Rust configuration used by --config=release-common.
    return {
        _COMPILATION_MODE: "opt",
        _RUSTC_FLAGS: settings[_RUSTC_FLAGS] + [
            "-Ccodegen-units=1",
            "-Copt-level=z",
            "--cfg=anyhow_no_backtrace",
        ],
    }

_release_transition = transition(
    implementation = _release_configuration,
    inputs = [_RUSTC_FLAGS],
    outputs = [_COMPILATION_MODE, _RUSTC_FLAGS],
)

def _configured_test_impl(ctx):
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

def _test_attrs(configuration):
    return {
        "runner_env": attr.string_dict(),
        "test": attr.label(cfg = configuration, mandatory = True),
        "_allowlist_function_transition": attr.label(
            default = "@bazel_tools//tools/allowlists/function_transition_allowlist",
        ),
    }

_macos_test = rule(
    implementation = _configured_test_impl,
    attrs = _test_attrs(_macos_transition) | {
        "remote": attr.bool(),
        "simulator": attr.string(),
        "xcode": attr.string(),
    },
    exec_groups = {
        "test": exec_group(exec_compatible_with = _MACOS_EXEC_CONSTRAINTS),
    },
    test = True,
)

def macos_test(name, test, xcode = "", simulator = "", xctest = False, timeout = "moderate", tags = [], visibility = None):
    _macos_test(
        name = name,
        test = test,
        xcode = xcode,
        simulator = simulator,
        remote = select({"//bazel/platforms:mixed_rbe": True, "//conditions:default": False}),
        runner_env = select({
            "//bazel/platforms:mixed_rbe": {
                "APPLE_SDK_PLATFORM": "iPhoneSimulator",
                "STARTUP_TIMEOUT_SEC": "300",
            } if xctest else {},
            "//conditions:default": {},
        }),
        timeout = timeout,
        target_compatible_with = select({
            "//bazel/platforms:mixed_rbe": [],
            "@platforms//os:macos": [],
            "//conditions:default": ["@platforms//:incompatible"],
        }),
        tags = tags,
        visibility = visibility,
    )

_release_test = rule(
    implementation = _configured_test_impl,
    attrs = _test_attrs(_release_transition),
    test = True,
)

def release_backtrace_test(name, test):
    _release_test(
        name = name,
        size = "small",
        test = test,
        runner_env = {
            "ANYHOW_EXPECT_DISABLED_BACKTRACE": "1",
            "RUST_BACKTRACE": "1",
        },
        tags = ["manual"],
    )
