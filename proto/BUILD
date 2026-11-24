# capture-sdk - bitdrift's client SDK
# Copyright Bitdrift, Inc. All rights reserved.
#
# Use of this source code is governed by a source available license that can be found in the
# LICENSE file or at:
# https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt

load("@flatbuffers//:build_defs.bzl", "flatbuffer_library_public")
load("//bazel:bitdrift_build_system.bzl", "bitdrift_rust_binary", "bitdrift_rust_test")
load(":schema_codegen.bzl", "schema_codegen")

flatbuffer_library_public(
    name = "descriptor",
    srcs = [
        "@bitdrift_api//:buffer_log",
    ],
    outs = [
        "buffer_log.schema.json",
    ],
    # This seems crazy hacky
    include_paths = ["external/+new_git_repository+bitdrift_api/src"],
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
    include_paths = ["external/+new_git_repository+bitdrift_api/src"],
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
    name = "builtin_command_codegen",
)

bitdrift_rust_test(
    name = "test",
    crate = ":builtin_command_codegen",
)

config_setting(
    name = "linux_schema_codegen",
    define_values = {"capture_linux_schema_codegen": "true"},
)

schema_codegen(
    name = "log_type_kt_source",
    srcs = [":descriptor"],
    out = "LogType.kt",
    args = "$(location :descriptor) kotlin",
    tool = "//proto/log_enum:log_enum_codegen",
)

schema_codegen(
    name = "log_type_swift_source",
    srcs = [":descriptor"],
    out = "LogType.swift",
    args = "$(location :descriptor) swift",
    tool = "//proto/log_enum:log_enum_codegen",
)

schema_codegen(
    name = "builtin_command_kt_source",
    out = "BuiltinCommandType.kt",
    args = "kotlin",
    tool = ":builtin_command_codegen",
)

schema_codegen(
    name = "builtin_command_swift_source",
    out = "BuiltinCommandType.swift",
    args = "swift",
    tool = ":builtin_command_codegen",
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
        ":common_swift",
        ":report_swift",
    ],
    visibility = ["//visibility:public"],
)
