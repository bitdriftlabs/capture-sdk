load("@bazel_tools//tools/build_defs/repo:http.bzl", "http_archive")

def capture_repositories():
    http_archive(
        name = "google_bazel_common",
        sha256 = "d8c9586b24ce4a5513d972668f94b62eb7d705b92405d4bc102131f294751f1d",
        strip_prefix = "bazel-common-413b433b91f26dbe39cdbc20f742ad6555dd1e27",
        urls = ["https://github.com/google/bazel-common/archive/413b433b91f26dbe39cdbc20f742ad6555dd1e27.zip"],
    )

    http_archive(
        name = "com_github_google_flatbuffers",
        strip_prefix = "flatbuffers-22.9.29",
        sha256 = "89df9e247521f2b8c2d85cd0a5ab79cc9f34de6edf7fd69f91887b4426ebc46c",
        urls = ["https://github.com/google/flatbuffers/archive/refs/tags/v22.9.29.zip"],
    )

    http_archive(
        name = "rules_jvm_external",
        sha256 = "cd1a77b7b02e8e008439ca76fd34f5b07aecb8c752961f9640dea15e9e5ba1ca",
        strip_prefix = "rules_jvm_external-4.2",
        url = "https://github.com/bazelbuild/rules_jvm_external/archive/4.2.zip",
    )

    rules_detekt_version = "0.8.1.2"
    rules_detekt_sha = "a5ae68f2487568d2c4145a8fc45da096edaaaed46487fb3d108ffe24b31d99da"
    http_archive(
        name = "rules_detekt",
        sha256 = rules_detekt_sha,
        strip_prefix = "bazel_rules_detekt-{v}".format(v = rules_detekt_version),
        url = "https://github.com/buildfoundation/bazel_rules_detekt/releases/download/v{v}/bazel_rules_detekt-v{v}.tar.gz".format(v = rules_detekt_version),
    )

    http_archive(
        name = "io_bazel_rules_kotlin",
        urls = ["https://github.com/bazelbuild/rules_kotlin/releases/download/v1.9.5/rules_kotlin-v1.9.5.tar.gz"],
        sha256 = "34e8c0351764b71d78f76c8746e98063979ce08dcf1a91666f3f3bc2949a533d",
    )
