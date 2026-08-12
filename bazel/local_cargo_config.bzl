"""Optional, non-mutating Cargo overrides for composition checkouts."""

def _local_cargo_config_impl(repository_ctx):
    shared_core = repository_ctx.getenv("CAPTURE_SDK_SHARED_CORE", "")
    contents = ""
    if shared_core:
        # Cargo path overrides keep capture-sdk's committed Git dependencies
        # untouched. They are intentionally supplied only by the composition
        # wrapper, which also owns the local shared-core checkout.
        contents = "paths = [{}]\n".format(repr(shared_core))

    repository_ctx.file("BUILD.bazel", 'exports_files(["config.toml"])\n')
    repository_ctx.file("config.toml", contents)

local_cargo_config = repository_rule(
    implementation = _local_cargo_config_impl,
    environ = ["CAPTURE_SDK_SHARED_CORE"],
)
