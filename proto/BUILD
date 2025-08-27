# capture-sdk - bitdrift's client SDK
# Copyright Bitdrift, Inc. All rights reserved.
#
# Use of this source code is governed by a source available license that can be found in the
# LICENSE file or at:
# https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

load("@flatbuffers//:build_defs.bzl", "flatbuffer_library_public")
load("//bazel:bitdrift_build_system.bzl", "bitdrift_rust_binary")

flatbuffer_library_public(
    name = "descriptor",
    srcs = [
        "@bitdrift_api//:buffer_log",
    ],
    outs = [
        "buffer_log.schema.json",
    ],
    # This seems crazy hacky
    include_paths = ["external/+_repo_rules3+bitdrift_api/src"],
    includes = ["@bitdrift_api//:common"],
    language_flag = "--jsonschema",
)

flatbuffer_library_public(
    name = "gen_report_swift",
    srcs = [
        "@bitdrift_api//:report",
    ],
    outs = [
        "report_generated.swift",
    ],
    # This seems crazy hacky
    include_paths = ["external/+_repo_rules3+bitdrift_api/src"],
    includes = ["@bitdrift_api//:common"],
    language_flag = "--swift",
)

flatbuffer_library_public(
    name = "gen_common_swift",
    srcs = [
        "@bitdrift_api//:common",
    ],
    outs = [
        "common_generated.swift",
    ],
    language_flag = "--swift",
)

bitdrift_rust_binary(
    name = "log_enum_codegen",
)

genrule(
    name = "log_type_kt_source",
    srcs = [":descriptor"],
    outs = ["LogType.kt"],
    cmd = "$(location :log_enum_codegen) $(location :descriptor) kotlin > $(OUTS)",
    tools = [":log_enum_codegen"],
    visibility = ["//visibility:public"],
)

genrule(
    name = "log_type_swift_source",
    srcs = [":descriptor"],
    outs = ["LogType.swift"],
    cmd = "$(location :log_enum_codegen) $(location :descriptor) swift > $(OUTS)",
    tools = [":log_enum_codegen"],
    visibility = ["//visibility:public"],
)

genrule(
    name = "report_swift",
    srcs = [":gen_report_swift"],
    outs = ["Report.swift"],
    # you'd think that the generated file would include all of the imports
    # needed, and you would be wrong.
    cmd = "echo import FlatBuffers > $(OUTS) && cat $(location :gen_report_swift) >> $(OUTS)",
    visibility = ["//visibility:public"],
)

genrule(
    name = "common_swift",
    srcs = [":gen_common_swift"],
    outs = ["Common.swift"],
    # you'd think that the generated file would include all of the imports
    # needed, and you would be wrong.
    cmd = "echo import FlatBuffers > $(OUTS) && cat $(location :gen_common_swift) >> $(OUTS)",
    visibility = ["//visibility:public"],
)

filegroup(
    name = "report_swift_source",
    srcs = [
        ":report_swift",
        ":common_swift",
    ],
    visibility = ["//visibility:public"],
)
