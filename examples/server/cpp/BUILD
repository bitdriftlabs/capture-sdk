
cc_library(
    name = "capture_lib",
    linkopts = ["-lz"],
    deps = [
        "//platform/server/cpp:platform-linux",
        "//platform/server/cpp:cxx_bridge_linked",
    ],
)

cc_binary(
    name = "hello_world",
    srcs = ["main.cpp"],
    deps = [":capture_lib"],
)
