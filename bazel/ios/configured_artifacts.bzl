"""Choose the iOS CPU for an artifact dependency."""

_IOS_CPUS = "//command_line_option:ios_multi_cpus"
_OMIT_FRAMEWORK_PLIST = "//bazel/ios:omit_framework_plist"
_RUST_LTO = str(Label("@rules_rust//rust/settings:lto"))

def _ios_artifact_configuration(settings, attr):
    return {
        _IOS_CPUS: [attr.cpu],
        _OMIT_FRAMEWORK_PLIST: attr.test_bundle,
        # Xcode's linker cannot consume Rust's release LLVM bitcode in XCTest.
        _RUST_LTO: "off" if attr.test_bundle else settings[_RUST_LTO],
    }

_ios_artifact_transition = transition(
    implementation = _ios_artifact_configuration,
    inputs = [_RUST_LTO],
    outputs = [_IOS_CPUS, _OMIT_FRAMEWORK_PLIST, _RUST_LTO],
)

def _ios_artifacts_impl(ctx):
    return [DefaultInfo(files = depset(transitive = [src[DefaultInfo].files for src in ctx.attr.srcs]))]

ios_artifacts = rule(
    implementation = _ios_artifacts_impl,
    attrs = {
        "cpu": attr.string(mandatory = True, values = ["arm64", "sim_arm64"]),
        "srcs": attr.label_list(cfg = _ios_artifact_transition, allow_files = True),
        "test_bundle": attr.bool(),
        "_allowlist_function_transition": attr.label(
            default = "@bazel_tools//tools/allowlists/function_transition_allowlist",
        ),
    },
)
