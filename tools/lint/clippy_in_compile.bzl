"""Opt a Rust target into compiling with clippy-driver in CI."""

# The rules_rust compile patch consumes this provider.
# buildifier: disable=bzl-visibility
load("@rules_rust//rust/private:providers.bzl", "LintsInfo")

def _clippy_compile_config_impl(ctx):
    return [LintsInfo(
        rustc_lint_flags = [],
        rustc_lint_files = [],
        clippy_lint_flags = [],
        clippy_lint_files = [ctx.file.config],
        rustdoc_lint_flags = [],
        rustdoc_lint_files = [],
    )]

clippy_compile_config = rule(
    implementation = _clippy_compile_config_impl,
    attrs = {"config": attr.label(allow_single_file = True, mandatory = True)},
)
