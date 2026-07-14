load("@crates//:defs.bzl", "aliases", "all_crate_deps")
load("@rules_rs//rs:rust_binary.bzl", "rust_binary")
load("@rules_rs//rs:rust_library.bzl", "rust_library")
load("@rules_rs//rs:rust_shared_library.bzl", "rust_shared_library")
load("@rules_rs//rs:rust_test.bzl", "rust_test")
load("@rules_rust//rust:defs.bzl", "rust_clippy")

def bitdrift_rust_binary(name, srcs = None, deps = [], proc_macro_deps = [], **args):
    rust_binary(
        name = name,
        srcs = srcs if srcs else native.glob(["src/**/*.rs"]),
        deps = all_crate_deps(normal = True, cargo_only = True) + deps,
        proc_macro_deps = proc_macro_deps,
        aliases = _crate_aliases(),
        edition = "2021",
        rustc_flags = _rustc_flags(),
        **args
    )

    rust_clippy(
        name = "_{}_rust_clippy".format(name),
        testonly = True,
        deps = [
            name,
        ],
        tags = [
            "manual",
        ],
    )

def bitdrift_rust_shared_library(name, srcs = None, deps = [], proc_macro_deps = [], rustc_flags = [], **args):
    rust_shared_library(
        name = name,
        srcs = srcs if srcs else native.glob(["src/**/*.rs"]),
        deps = all_crate_deps(normal = True, cargo_only = True) + deps,
        proc_macro_deps = proc_macro_deps,
        aliases = _crate_aliases(),
        edition = "2021",
        rustc_flags = rustc_flags + _rustc_flags(),
        **args
    )

    rust_clippy(
        name = "_{}_rust_clippy".format(name),
        testonly = True,
        deps = [
            name,
        ],
        tags = [
            "manual",
        ],
    )

def bitdrift_rust_test(name, deps = [], proc_macro_deps = [], **args):
    rust_test(
        name = name,
        rustc_flags = _rustc_flags(),
        edition = "2021",
        deps = all_crate_deps(normal = True, normal_dev = True, cargo_only = True) + deps,
        proc_macro_deps = proc_macro_deps,
        aliases = _crate_aliases(),
        **args
    )

def bitdrift_rust_integration_test(name, **args):
    bitdrift_rust_library(
        name = name,
        srcs = native.glob(["tests/**/*.rs"]),
        crate_root = "tests/{}.rs".format(name),
        testonly = True,
        **args
    )

def bitdrift_rust_library_only(name, srcs, deps = []):
    rust_library(
        name = name,
        srcs = srcs,
        deps = deps + all_crate_deps(normal = True, cargo_only = True),
        disable_pipelining = True,
        aliases = _crate_aliases(),
        rustc_flags = _rustc_flags(),
        edition = "2021",
    )

def bitdrift_rust_library(name, srcs = None, deps = [], test_deps = [], tags = [], data = [], extra_aliases = {}, **args):
    rust_library(
        name = name,
        deps = deps + all_crate_deps(normal = True, cargo_only = True),
        srcs = srcs if srcs else native.glob(["src/**/*.rs"]),
        disable_pipelining = True,
        aliases = _crate_aliases(extra_aliases),
        rustc_flags = _rustc_flags(),
        edition = "2021",
        tags = tags,
        data = data,
        **args
    )

    rust_test(
        name = "{}_test".format(name),
        crate = name,
        tags = tags,
        rustc_flags = _rustc_flags(),
        aliases = _crate_aliases(extra_aliases),
        data = data,
        deps = all_crate_deps(
            normal_dev = True,
            cargo_only = True,
        ) + test_deps,
        edition = "2021",
    )

    rust_clippy(
        name = "_{}_rust_clippy".format(name),
        testonly = True,
        deps = [
            name,
        ],
        tags = [
            "manual",
        ],
    )

def _rustc_flags():
    return [
        "-Dwarnings",
        "-Dfuture-incompatible",
        "-Dnonstandard-style",
        "-Drust-2018-compatibility",
        "-Drust-2018-idioms",
        "-Drust-2021-compatibility",
        "-Dunused",
        "-Dclippy::all",
        "-Dclippy::correctness",
        "-Dclippy::suspicious",
        "-Dclippy::style",
        "-Dclippy::complexity",
        "-Dclippy::perf",
        "-Dclippy::pedantic",
        "-Dclippy::nursery",
        "-Aclippy::future-not-send",
        "-Aclippy::missing-errors-doc",
        "-Aclippy::missing-panics-doc",
        "-Aclippy::similar-names",
        "-Aclippy::too-long-first-doc-paragraph",
        "-Aclippy::too-many-arguments",
        "-Aclippy::too-many-lines",
        "-Aclippy::unused-async",
        "-Arust-2021-incompatible-closure-captures",

        # Appears spurious on 1.62. Try to remove later.
        "-Aclippy::redundant-pub-crate",
        "-Aclippy::significant-drop-tightening",
        "-Aclippy::significant-drop-in-scrutinee",
    ]

def _crate_aliases(extra_aliases = {}):
    result = dict(extra_aliases)

    # First-party targets are supplied directly by the BUILD targets, whose names don't always
    # match their package directories. rules_rs aliases those packages by directory, so retain
    # only aliases that resolve through the generated crates repository.
    for dep, crate_name in aliases().items():
        if dep.startswith("@crates//"):
            result[dep] = crate_name

    return result
