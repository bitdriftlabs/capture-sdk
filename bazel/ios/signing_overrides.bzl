def _quote(value):
    return "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\""

def _ios_signing_overrides_repo_impl(repository_ctx):
    team_id = repository_ctx.os.environ.get("APPLE_TEAM_ID", "33XQXT255C")
    profile_name = repository_ctx.os.environ.get("APPLE_PROFILE_NAME", "Wildcard")

    repository_ctx.file("BUILD", "")
    repository_ctx.file(
        "signing_overrides.bzl",
        "TEAM_ID = %s\nPROFILE_NAME = %s\n" % (_quote(team_id), _quote(profile_name)),
    )

ios_signing_overrides_repo = repository_rule(
    implementation = _ios_signing_overrides_repo_impl,
    environ = [
        "APPLE_PROFILE_NAME",
        "APPLE_TEAM_ID",
    ],
)

def _ios_signing_overrides_ext_impl(module_ctx):
    ios_signing_overrides_repo(name = "ios_signing_overrides")

ios_signing_overrides_ext = module_extension(
    implementation = _ios_signing_overrides_ext_impl,
)
