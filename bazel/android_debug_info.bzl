"""
Rule to create objcopy debug info from a native dynamic library built for
Android.

This is a workaround for generally not being able to produce dwp files for
Android https://github.com/bazelbuild/bazel/pull/14765

But even if we could create those we'd need to get them out of the build
somehow, this rule provides a separate --output_group for this
"""

load("@rules_android//rules:android_split_transition.bzl", "android_split_transition")
load("@rules_cc//cc/common:cc_common.bzl", "cc_common")
load("@rules_cc//cc/common:cc_info.bzl", "CcInfo")

def _impl(ctx):
    library_outputs = []
    objcopy_outputs = []
    for platform, dep in ctx.split_attr.dep.items():
        # When --android_platforms isn't set, the platform is None
        if len(dep.files.to_list()) != 1:
            fail("Expected exactly one file in the library")

        cc_toolchain = ctx.split_attr._cc_toolchain[platform][cc_common.CcToolchainInfo]
        lib = dep.files.to_list()[0]
        platform_name = platform or ctx.fragments.android.android_cpu
        objcopy_output = ctx.actions.declare_file(platform_name + "/" + platform_name + ".debug.gz")

        ctx.actions.run_shell(
            inputs = [lib],
            outputs = [objcopy_output],
            command = cc_toolchain.objcopy_executable + " --compress-debug-sections=zlib --only-keep-debug " + lib.path + " - | gzip -c >" + objcopy_output.path,
            tools = [cc_toolchain.all_files],
            progress_message = "Generating symbol map " + platform_name,
        )

        # See docs/android-native-unwind.md#supported-abis for why ARMv7 is excluded.
        compress_cfi = ctx.attr.compress_cfi and platform_name in ("arm64-v8a", "x86", "x86_64")
        strip_output = ctx.actions.declare_file(platform_name + "/" + lib.basename)
        if compress_cfi:
            ctx.actions.run_shell(
                inputs = [lib],
                outputs = [strip_output],
                arguments = [
                    cc_toolchain.objcopy_executable,
                    lib.path,
                    strip_output.path,
                    ctx.executable._xz.path,
                    cc_toolchain.strip_executable,
                ],
                command = """
set -euo pipefail
work_dir="$(mktemp -d "$3.cfi.XXXXXX")"
trap 'rm -rf "$work_dir"' EXIT
"$1" --only-keep-debug "$2" "$work_dir/full.elf"
"$1" --strip-all --keep-section=.eh_frame "$work_dir/full.elf" "$work_dir/cfi.elf"
"$1" --dump-section=.eh_frame="$work_dir/eh_frame" "$work_dir/cfi.elf" "$work_dir/checked.elf"
test -s "$work_dir/eh_frame"
"$4" --threads=1 --check=crc64 --lzma2=preset=9,dict=1MiB --stdout "$work_dir/cfi.elf" > "$work_dir/cfi.xz"
"$5" --strip-all "$2" -o "$work_dir/stripped.so"
"$1" --remove-section=.eh_frame --remove-section=.eh_frame_hdr \
    --add-section=.gnu_debugdata="$work_dir/cfi.xz" "$work_dir/stripped.so" "$3"
""",
                tools = [cc_toolchain.all_files, ctx.attr._xz[DefaultInfo].files_to_run],
                mnemonic = "CompressAndroidCfi",
                progress_message = "Compressing Android CFI " + lib.path,
            )
        else:
            ctx.actions.run_shell(
                inputs = [lib],
                outputs = [strip_output],
                command = cc_toolchain.strip_executable + " --strip-all " + lib.path + " -o " + strip_output.path,
                tools = [cc_toolchain.all_files],
                progress_message = "Stripping library " + lib.path,
            )
        library_outputs.append(strip_output)
        objcopy_outputs.append(objcopy_output)

    return [
        DefaultInfo(files = depset(library_outputs)),
        OutputGroupInfo(objcopy = objcopy_outputs),
    ]

android_debug_info = rule(
    implementation = _impl,
    attrs = dict(
        compress_cfi = attr.bool(default = False),
        dep = attr.label(
            providers = [CcInfo],
            cfg = android_split_transition,
        ),
        _cc_toolchain = attr.label(
            default = Label("@bazel_tools//tools/cpp:current_cc_toolchain"),
            cfg = android_split_transition,
        ),
        _xz = attr.label(
            default = Label("@xz//:xz"),
            executable = True,
            cfg = "exec",
        ),
    ),
    fragments = ["cpp", "android"],
)
