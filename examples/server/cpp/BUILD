load("@rules_cc//cc:defs.bzl", "cc_binary", "cc_library")

cc_library(
    name = "capture_lib",
    linkopts = ["-lz"],
    deps = [
        "//platform/server/cpp:cxx_bridge_linked",
        "//platform/server/cpp:platform-linux",
    ],
)

cc_binary(
    name = "hello_world",
    srcs = ["main.cpp"],
    deps = [":capture_lib"],
)
