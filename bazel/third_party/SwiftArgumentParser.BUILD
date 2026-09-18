load("@rules_swift//swift:swift.bzl", "swift_library")

swift_library(
    name = "swift_argument_parser",
    srcs = glob(["Sources/ArgumentParser/**/*.swift"]),
    copts = [
        "-language-mode",
        "5",
    ],
    features = [
        "swift.enable_library_evolution",
    ],
    module_name = "ArgumentParser",
    tags = ["manual"],
    visibility = ["//visibility:public"],
    deps = [":swift_argument_parser_tool_info"],
)

swift_library(
    name = "swift_argument_parser_tool_info",
    srcs = glob(["Sources/ArgumentParserToolInfo/**/*.swift"]),
    copts = [
        "-language-mode",
        "5",
    ],
    library_evolution = True,
    module_name = "ArgumentParserToolInfo",
    tags = ["manual"],
)
