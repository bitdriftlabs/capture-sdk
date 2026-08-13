"""
This is a workaround to remove lib.rmeta symbols coming from rust that
are included into the archive.
"""

def _rewrite_xcframework_impl(ctx):
    outdir = ctx.actions.declare_directory(ctx.attr.framwork_name + ".xcframework")
    zip_in = ctx.file.xcframework
    tool = ctx.executable.rewrite_tool
    ar = ctx.executable.ar
    lipo = ctx.executable.lipo
    ranlib = ctx.executable.ranlib

    ctx.actions.run_shell(
        inputs = [zip_in],
        tools = [tool, ar, lipo, ranlib],
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
"{tool}" "$DIR"
rsync -a "$DIR/" "{outdir}/"
""".format(zip_in = zip_in.path, tool = tool.path, outdir = outdir.path),
    )

    return [DefaultInfo(files = depset([outdir]))]

rewrite_xcframework = rule(
    implementation = _rewrite_xcframework_impl,
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
        "ranlib": attr.label(
            allow_single_file = True,
            cfg = "exec",
            default = Label("//bazel/ios:llvm_ranlib"),
            executable = True,
        ),
        "rewrite_tool": attr.label(executable = True, cfg = "exec"),
        "xcframework": attr.label(allow_single_file = [".zip"]),
    },
)
