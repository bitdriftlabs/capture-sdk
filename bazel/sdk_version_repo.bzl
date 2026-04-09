def _sdk_version_repo_impl(repository_ctx):
    version = repository_ctx.read(repository_ctx.path(repository_ctx.attr.version_file)).strip()
    repository_ctx.file("BUILD", "")
    repository_ctx.file("sdk_version.bzl", "SDK_VERSION = \"%s\"\n" % version)

sdk_version_repo = repository_rule(
    implementation = _sdk_version_repo_impl,
    attrs = {
        "version_file": attr.label(allow_single_file = True),
    },
)
