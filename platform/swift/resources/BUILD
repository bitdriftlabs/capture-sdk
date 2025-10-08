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
    srcs = [
        ":info_plist_template",
        "//platform/shared:sdk_version",
    ],
    outs = ["Info.plist"],
    cmd = """
set -euo pipefail

template="$(location :info_plist_template)"
sdk_version_file="$(location //platform/shared:sdk_version)"

# Read version from the sdk_version file (strip newline)
version="$$(tr -d '\\n' < "$$sdk_version_file")"

# plists don't allow for "-" in version numbers (e.g., 0.9.112-rc.1)
IFS='-' read -r version _ <<< "$$version"

sed -e "s/{version}/$$version/g" "$$template" > "$@"
""",
    visibility = ["//visibility:public"],
)
