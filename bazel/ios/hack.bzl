"""
This is a workaround to remove lib.rmeta symbols coming from rust that
are included into the archive.
"""

def _rewrite_xcframework_impl(ctx):
    outdir = ctx.actions.declare_directory(ctx.attr.framwork_name + ".xcframework")
    zip_in = ctx.file.xcframework
    tool = ctx.executable.rewrite_tool

    ctx.actions.run_shell(
        inputs = [zip_in],
        tools = [tool],
        outputs = [outdir],
        command = """
TMP="$(mktemp -d)"
unzip -q {zip_in} -d "$TMP"
DIR="$(cd "$TMP" && ls -1d *.xcframework | head -1)"
"{tool}" "$TMP/$DIR"
rsync -a "$TMP/$DIR/" "{outdir}/"
""".format(zip_in = zip_in.path, tool = tool.path, outdir = outdir.path),
    )

    return [DefaultInfo(files = depset([outdir]))]

rewrite_xcframework = rule(
    implementation = _rewrite_xcframework_impl,
    attrs = {
        "framwork_name": attr.string(default = "Capture"),
        "rewrite_tool": attr.label(executable = True, cfg = "exec"),
        "xcframework": attr.label(allow_single_file = [".zip"]),
    },
)
