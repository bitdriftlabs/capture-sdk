load("@rules_swift//swift:swift.bzl", "swift_library")

swift_library(
    name = "swift_benchmark",
    srcs = glob(["Sources/Benchmark/**/*.swift"]),
    module_name = "Benchmark",
    tags = ["manual"],
    visibility = ["//visibility:public"],
    deps = [
        "@SwiftArgumentParser//:swift_argument_parser",
    ],
)
