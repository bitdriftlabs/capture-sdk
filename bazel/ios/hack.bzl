"""
This is a workaround to rename symbols coming from rust that conflict
with libclang when linking into iOS apps that link using -all_load.

See https://github.com/rust-lang/compiler-builtins/issues/420#issuecomment-1112269764
"""

def workaround_rust_symbols(name, xcframework, out, visibility = []):
    native.genrule(
        name = name,
        srcs = [
            xcframework,
            "//bazel/ios:symbols_to_rewrite",
        ],
        # Ideally we would return Capture.xcframework in here as opposed to its zip but
        # Bazel rules (pkg_zip) are not happy when passing directories as input arguments
        # to them. For this reason, we return zip in here and unzip it later on
        # as part of ios_release.sh script.
        outs = [out],
        cmd = """
        TMP=$$(mktemp -d)
        unzip -o $(location {0}) -d $$TMP
        filename=$$(basename -- "$(location {0})" .zip)
        echo $$filename
        $(location //bazel/ios:rewrite_symbols) $(location //bazel/ios:symbols_to_rewrite) $$TMP/$$filename
        (cd $$TMP && zip -r {1} $$filename)
        mv $$TMP/{1} $@
        """.format(xcframework, out),
        tools = [
            "//bazel/ios:rewrite_symbols",
        ],
        visibility = visibility,
        stamp = True,
        tags = ["local"],
    )
