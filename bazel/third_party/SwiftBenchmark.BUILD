load("@rules_swift//swift:swift.bzl", "swift_library")

swift_library(
    name = "swift_benchmark",
    srcs = glob(["Sources/Benchmark/**/*.swift"]),
    tags = ["manual"],
    module_name = "Benchmark",
    visibility = ["//visibility:public"],
    deps = [
        "@SwiftArgumentParser//:swift_argument_parser",
    ],
)
