"""
This is a workaround to remove lib.rmeta symbols coming from rust that
are included into the archive.
"""

def _rewrite_xcframework_impl(ctx):
    outdir = ctx.actions.declare_directory(ctx.attr.framwork_name + ".xcframework")
    zip_in = ctx.file.xcframework
    tool = ctx.executable.rewrite_tool
    ar = ctx.executable.ar
    ran = ctx.executable.ranlib
    lipo = ctx.executable.lipo

    ctx.actions.run_shell(
        inputs = [zip_in],
        tools = [tool, ar, ran, lipo],
        outputs = [outdir],
        env = {
            "AR": ctx.executable.ar.path,
            "RANLIB": ctx.executable.ranlib.path,
            "LIPO": ctx.executable.lipo.path,
        },
        command = """
set -euo pipefail

TMP="$(mktemp -d)"
unzip -q {zip_in} -d "$TMP"
DIR="$(find "$TMP" -maxdepth 1 -name '*.xcframework' -print -quit)"
"{tool}" "$DIR"
rsync -a "$DIR/" "{outdir}/"
""".format(zip_in = zip_in.path, tool = tool.path, outdir = outdir.path, framework_name = ctx.attr.framwork_name),
    )

    return [DefaultInfo(files = depset([outdir]))]

rewrite_xcframework = rule(
    implementation = _rewrite_xcframework_impl,
    attrs = {
        "framwork_name": attr.string(default = "Capture"),
        "rewrite_tool": attr.label(executable = True, cfg = "exec"),
        "xcframework": attr.label(allow_single_file = [".zip"]),

        # Defaults wired to toolchains_llvm’s unpacked distro repo:
        # @llvm_toolchain_llvm//:bin/<tool>
        "ar": attr.label(
            allow_single_file = True,
            executable = True,
            cfg = "exec",
            default = Label("@llvm_toolchain_llvm//:bin/llvm-ar"),
        ),
        "ranlib": attr.label(
            allow_single_file = True,
            executable = True,
            cfg = "exec",
            default = Label("@llvm_toolchain_llvm//:bin/llvm-ranlib"),
        ),
        "lipo": attr.label(
            allow_single_file = True,
            executable = True,
            cfg = "exec",
            default = Label("@llvm_toolchain_llvm//:bin/llvm-lipo"),
        ),
    },
)
