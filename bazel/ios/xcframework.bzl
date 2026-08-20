"""Materializes the XCFramework ZIP as a tree artifact for `pkg_zip`."""

def _materialize_xcframework_impl(ctx):
    outdir = ctx.actions.declare_directory(ctx.attr.framwork_name + ".xcframework")
    zip_in = ctx.file.xcframework

    ctx.actions.run_shell(
        inputs = [zip_in],
        outputs = [outdir],
        use_default_shell_env = True,
        command = """
set -euo pipefail

TMP="$(mktemp -d)"
unzip -q {zip_in} -d "$TMP"
DIR="$(find "$TMP" -maxdepth 1 -name '*.xcframework' -print -quit)"
rsync -a "$DIR/" "{outdir}/"
""".format(zip_in = zip_in.path, outdir = outdir.path),
    )

    return [DefaultInfo(files = depset([outdir]))]

materialize_xcframework = rule(
    implementation = _materialize_xcframework_impl,
    attrs = {
        "framwork_name": attr.string(default = "Capture"),
        "xcframework": attr.label(allow_single_file = [".zip"]),
    },
)
