"""Prepares and measures the distribution XCFramework."""

load("@build_bazel_apple_support//lib:apple_support.bzl", "apple_support")

def _strip_rust_metadata_xcframework_impl(ctx):
    outdir = ctx.actions.declare_directory(ctx.attr.framwork_name + ".xcframework")
    zip_in = ctx.file.xcframework
    metadata_stripper = ctx.executable.metadata_stripper
    ar = ctx.executable.ar
    lipo = ctx.executable.lipo
    ranlib = ctx.executable.ranlib

    ctx.actions.run_shell(
        inputs = [zip_in],
        tools = [metadata_stripper, ar, lipo, ranlib],
        outputs = [outdir],
        env = {
            "AR": ar.path,
            "LIPO": lipo.path,
            "RANLIB": ranlib.path,
        },
        use_default_shell_env = True,
        command = """
set -euo pipefail

TMP="$(mktemp -d)"
unzip -q {zip_in} -d "$TMP"
DIR="$(find "$TMP" -maxdepth 1 -name '*.xcframework' -print -quit)"
"{metadata_stripper}" "$DIR"
rsync -a "$DIR/" "{outdir}/"
""".format(
            zip_in = zip_in.path,
            metadata_stripper = metadata_stripper.path,
            outdir = outdir.path,
        ),
    )

    return [DefaultInfo(files = depset([outdir]))]

strip_rust_metadata_xcframework = rule(
    implementation = _strip_rust_metadata_xcframework_impl,
    attrs = {
        "ar": attr.label(
            allow_single_file = True,
            cfg = "exec",
            default = Label("//bazel/ios:llvm_ar"),
            executable = True,
        ),
        "framwork_name": attr.string(default = "Capture"),
        "lipo": attr.label(
            allow_single_file = True,
            cfg = "exec",
            default = Label("//bazel/ios:llvm_lipo"),
            executable = True,
        ),
        "metadata_stripper": attr.label(
            cfg = "exec",
            executable = True,
            mandatory = True,
        ),
        "ranlib": attr.label(
            allow_single_file = True,
            cfg = "exec",
            default = Label("//bazel/ios:llvm_ranlib"),
            executable = True,
        ),
        "xcframework": attr.label(allow_single_file = [".zip"]),
    },
)

def _ios_xcframework_archive_breakdown_impl(ctx):
    xcframework = ctx.file.xcframework
    report = ctx.actions.declare_file(ctx.label.name + ".json")
    apple_support.run(
        actions = ctx.actions,
        apple_fragment = ctx.fragments.apple,
        xcode_config = ctx.attr._xcode_config[apple_common.XcodeVersionConfig],
        executable = ctx.attr.tool[DefaultInfo].files_to_run,
        inputs = [xcframework],
        outputs = [report],
        arguments = [
            xcframework.path + "/ios-arm64/Capture.framework/Capture",
            "--output",
            report.path,
        ],
        env = {"LC_ALL": "C"},
        mnemonic = "IosArchiveBreakdown",
    )
    return [DefaultInfo(files = depset([report]))]

ios_xcframework_archive_breakdown = rule(
    implementation = _ios_xcframework_archive_breakdown_impl,
    attrs = apple_support.action_required_attrs() | {
        "tool": attr.label(cfg = "exec", executable = True, mandatory = True),
        "xcframework": attr.label(allow_single_file = True, mandatory = True),
    },
    fragments = ["apple"],
)
