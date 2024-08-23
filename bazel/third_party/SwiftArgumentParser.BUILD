load("@build_bazel_rules_swift//swift:swift.bzl", "swift_library")

swift_library(
    name = "swift_argument_parser",
    srcs = glob(["Sources/ArgumentParser/**/*.swift"]),
    tags = ["manual"],
    module_name = "ArgumentParser",
    features = [
        "swift.enable_library_evolution",
    ],
    visibility = ["//visibility:public"],
    deps = [":swift_argument_parser_tool_info"],
)

swift_library(
    name = "swift_argument_parser_tool_info",
    srcs = glob(["Sources/ArgumentParserToolInfo/**/*.swift"]),
    tags = ["manual"],
    module_name = "ArgumentParserToolInfo",
)
