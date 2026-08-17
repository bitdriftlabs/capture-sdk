def _rustfmt_runner_impl(ctx):
    rustfmt_toolchain = ctx.toolchains[Label("@rules_rust//rust/rustfmt:toolchain_type")]
    launcher = ctx.actions.declare_file(ctx.label.name + ".sh")

    ctx.actions.write(
        output = launcher,
        content = """\
#!/usr/bin/env bash
set -euo pipefail

cd "${{BUILD_WORKSPACE_DIRECTORY}}"
rustfmt_runfile="{rustfmt_runfile}"
rustfmt="${{RUNFILES_DIR:-}}/$rustfmt_runfile"
if [[ ! -x "$rustfmt" ]]; then
  rustfmt="$(grep -sm1 "^$rustfmt_runfile " "${{RUNFILES_MANIFEST_FILE:-/dev/null}}" | cut -d ' ' -f 2- || true)"
fi
if [[ ! -x "$rustfmt" ]]; then
  rustfmt="$0.runfiles/$rustfmt_runfile"
fi
if [[ ! -x "$rustfmt" ]]; then
  rustfmt="$(grep -sm1 "^$rustfmt_runfile " "$0.runfiles_manifest" | cut -d ' ' -f 2- || true)"
fi
if [[ ! -x "$rustfmt" ]]; then
  echo "Unable to locate rustfmt in Bazel runfiles" >&2
  exit 1
fi

rust_sources=()
while IFS= read -r -d '' source; do
  rust_sources+=("$source")
done < <(git ls-files -z -- '*.rs')

"$rustfmt" --config-path "$BUILD_WORKSPACE_DIRECTORY/rustfmt.toml" "${{rust_sources[@]}}"
""".format(
            rustfmt_runfile = rustfmt_toolchain.rustfmt.owner.workspace_name + "/bin/rustfmt",
        ),
        is_executable = True,
    )

    runfiles = ctx.runfiles(
        files = [launcher],
        transitive_files = rustfmt_toolchain.all_files,
    )

    return [DefaultInfo(executable = launcher, runfiles = runfiles)]

rustfmt_runner = rule(
    implementation = _rustfmt_runner_impl,
    executable = True,
    toolchains = ["@rules_rust//rust/rustfmt:toolchain_type"],
)
