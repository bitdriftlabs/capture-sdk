"""Configure Apple tests and lint targets in mixed RBE CI."""

load("@rules_rust//rust:defs.bzl", "rust_clippy_aspect")

_MACOS_PLATFORM = "//bazel/platforms:buildbuddy_macos_arm64"
_XCODE_VERSION = "//command_line_option:xcode_version"
_SIMULATOR_VERSION = "//command_line_option:ios_simulator_version"
_PLATFORMS = "//command_line_option:platforms"
_EXECUTION_PLATFORMS = "//command_line_option:extra_execution_platforms"
_EXTRA_TOOLCHAINS = "//command_line_option:extra_toolchains"
_LINUX_LLVM_TOOLCHAIN = "@llvm//toolchain:all"
_MACOS_EXEC_CONSTRAINTS = ["@platforms//os:macos"]
_RUSTC_FLAGS = "@rules_rust//rust/settings:extra_rustc_flag"

def _macos_configuration(settings, attr):
    config = {
        _XCODE_VERSION: getattr(attr, "xcode", "") or settings[_XCODE_VERSION],
        _SIMULATOR_VERSION: getattr(attr, "simulator", "") or settings[_SIMULATOR_VERSION],
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

def _macos_clippy_impl(ctx):
    return [OutputGroupInfo(clippy_checks = depset(transitive = [
        dep[OutputGroupInfo].clippy_checks
        for dep in ctx.attr.deps
    ]))]

_macos_clippy = rule(
    implementation = _macos_clippy_impl,
    attrs = {
        "deps": attr.label_list(aspects = [rust_clippy_aspect], cfg = _macos_transition),
        "remote": attr.bool(default = True),
        "_allowlist_function_transition": attr.label(
            default = "@bazel_tools//tools/allowlists/function_transition_allowlist",
        ),
    },
)

def macos_clippy(name, deps):
    _macos_clippy(
        name = name,
        deps = deps,
        tags = ["clippy"],
        target_compatible_with = select({
            "//bazel/platforms:mixed_rbe": [],
            "//conditions:default": ["@platforms//:incompatible"],
        }),
        testonly = True,
    )

def _ci_clippy_check_impl(ctx):
    # In test mode, runfiles keep these checks reachable with --build_tests_only.
    # The build actions report lint failures before this no-op test runs.
    checks = [dep[DefaultInfo].files for dep in ctx.attr.compile_deps]
    checks.extend([dep[OutputGroupInfo].clippy_checks for dep in ctx.attr.lint_deps])
    executable = ctx.actions.declare_file(ctx.label.name)
    ctx.actions.write(executable, "#!/bin/sh\nexit 0\n", is_executable = True)
    return [DefaultInfo(
        executable = executable,
        runfiles = ctx.runfiles(transitive_files = depset(transitive = checks)),
    )]

_ci_clippy_check_test = rule(
    implementation = _ci_clippy_check_impl,
    attrs = {
        "compile_deps": attr.label_list(cfg = _macos_transition),
        "lint_deps": attr.label_list(aspects = [rust_clippy_aspect], cfg = _macos_transition),
        "remote": attr.bool(),
        "_allowlist_function_transition": attr.label(
            default = "@bazel_tools//tools/allowlists/function_transition_allowlist",
        ),
    },
    test = True,
)

def ci_clippy_check(name, deps, in_compile = False, macos = False, tags = [], target_compatible_with = []):
    """Include the given Rust targets in the combined BuildBuddy Test check.

    Args:
        name: Base name for the CI check.
        deps: Rust targets to lint.
        in_compile: Whether the targets compile with clippy-driver.
        macos: Whether to configure the Rust targets for the remote Mac.
        tags: Tags on the original Rust targets.
        target_compatible_with: Constraints on the CI check itself.
    """
    ignored_tags = ["manual", "no_clippy", "no_lint", "noclippy", "nolint"]
    if any([tag.replace("-", "_").lower() in ignored_tags for tag in tags]):
        return
    _ci_clippy_check_test(
        name = name + "_ci_clippy",
        compile_deps = deps if in_compile else [],
        lint_deps = [] if in_compile else deps,
        remote = macos,
        size = "small",
        tags = ["no-remote-exec"],
        target_compatible_with = target_compatible_with + select({
            "//tools/lint:clippy_in_compile_enabled": [],
            "//conditions:default": ["@platforms//:incompatible"],
        }) + (select({
            "//bazel/platforms:mixed_rbe": [],
            "//conditions:default": ["@platforms//:incompatible"],
        }) if macos else []),
    )

def _anyhow_no_backtrace_configuration(settings, _attr):
    # The flag must reach the anyhow dependency to exercise its release behavior.
    return {_RUSTC_FLAGS: settings[_RUSTC_FLAGS] + ["--cfg=anyhow_no_backtrace"]}

_anyhow_no_backtrace_transition = transition(
    implementation = _anyhow_no_backtrace_configuration,
    inputs = [_RUSTC_FLAGS],
    outputs = [_RUSTC_FLAGS],
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

def macos_test(name, test, xcode = "", simulator = "", xctest = False, timeout = "moderate", tags = [], visibility = None, target_compatible_with = []):
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
        }) + target_compatible_with,
        tags = tags,
        visibility = visibility,
    )

_anyhow_no_backtrace_test = rule(
    implementation = _configured_test_impl,
    attrs = _test_attrs(_anyhow_no_backtrace_transition),
    test = True,
)

def anyhow_no_backtrace_test(name, test):
    _anyhow_no_backtrace_test(
        name = name,
        size = "small",
        test = test,
        runner_env = {
            "ANYHOW_EXPECT_DISABLED_BACKTRACE": "1",
            "RUST_BACKTRACE": "1",
        },
    )
