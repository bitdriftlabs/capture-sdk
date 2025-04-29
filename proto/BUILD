load("@flatbuffers//:build_defs.bzl", "flatbuffer_library_public")
load("//bazel:bitdrift_build_system.bzl", "bitdrift_rust_binary")

flatbuffer_library_public(
    name = "descriptor",
    srcs = ["@bitdrift_api//:all_fbs"],
    outs = [
        "buffer_log.schema.json",
        "reports.schema.json", 
    ],
    language_flag = "--jsonschema",
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
