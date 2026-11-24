"""Choose the iOS CPU for an artifact dependency."""

_IOS_CPUS = "//command_line_option:ios_multi_cpus"

def _ios_artifact_configuration(_settings, attr):
    return {_IOS_CPUS: [attr.cpu]}

_ios_artifact_transition = transition(
    implementation = _ios_artifact_configuration,
    inputs = [],
    outputs = [_IOS_CPUS],
)

def _ios_artifacts_impl(ctx):
    return [DefaultInfo(files = depset(transitive = [src[DefaultInfo].files for src in ctx.attr.srcs]))]

ios_artifacts = rule(
    implementation = _ios_artifacts_impl,
    attrs = {
        "cpu": attr.string(mandatory = True, values = ["arm64", "sim_arm64"]),
        "srcs": attr.label_list(cfg = _ios_artifact_transition, allow_files = True),
        "_allowlist_function_transition": attr.label(
            default = "@bazel_tools//tools/allowlists/function_transition_allowlist",
        ),
    },
)
