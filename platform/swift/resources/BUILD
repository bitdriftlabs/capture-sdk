filegroup(
    name = "privacy_manifest",
    srcs = [
        "PrivacyInfo.xcprivacy",
    ],
    visibility = ["//visibility:public"],
)

filegroup(
    name = "info_plist_template",
    srcs = [
        "Info_template.plist",
    ],
    visibility = ["//visibility:public"],
)

# Include Info.plist in the framework that's integrated as part of the example apps results in
# duplicated "Info.plist" files in the final .app bundle. To avoid this, we include "Info.plist"
# conditionally.
filegroup(
    name = "maybe_info_plist",
    srcs = select({
        "//bazel/ios:produce_framework_plist": [":info_plist"],
        "//conditions:default": [],
    }),
    visibility = ["//visibility:public"],
)

genrule(
    name = "info_plist",
    srcs = [":info_plist_template"],
    outs = ["Info.plist"],
    cmd = """
# bazel-out/stable-status.txt is created when stamp = True
# Value of BUILD_EMBED_LABEL key comes from --embed_label on the command line
version="$$(grep BUILD_EMBED_LABEL bazel-out/stable-status.txt | cut -d ' ' -f 2)"

# plists don't allow for characters such as "-" in version numbers and we use them for versions like 0.9.112-rc.1.
IFS='-' read -r version string <<< "$$version"

# Set a reasonable default if no version was specified
if [ -z "$$version" ]; then version="0.9"; fi

sed -e "s/{version}/$$version/g" $< > $@
""",
    stamp = True,
    visibility = ["//visibility:public"],
)
