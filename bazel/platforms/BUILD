# BuildBuddy remote executor for --config=rbe. It matches the amd64 BuildBuddy Workflows runner
# that drives Bazel, so host tools fetched during analysis (the hermetic LLVM, Android SDK/NDK and
# Rust toolchains) also run on the executor. The Android NDK only ships x86_64 Linux host tools.
# The C/C++ toolchain comes from --config=linux-hermetic-llvm; the image supplies the shell
# utilities that actions run with and the ld that --config=libunwind expects under /usr/bin.
platform(
    name = "buildbuddy_linux_x86_64",
    constraint_values = [
        "@platforms//cpu:x86_64",
        "@platforms//os:linux",
    ],
    exec_properties = {
        "Arch": "amd64",
        "OSFamily": "Linux",
        # BuildBuddy's Ubuntu 24.04 RBE image, pinned by digest so remote cache keys change with
        # the image.
        "container-image": "docker://gcr.io/flame-public/rbe-ubuntu24-04@sha256:be9853fc45092d2cf2bda3abed30d22182881659a50bf35c7125161d7cfc8a75",
    },
    visibility = ["//visibility:public"],
)
