load("@crates//:defs.bzl", "aliases", "all_crate_deps")
load("@rules_rust//rust:defs.bzl", "rust_binary", "rust_clippy", "rust_library", "rust_shared_library", "rust_test")

def bitdrift_rust_binary(name, srcs = None, deps = [], proc_macro_deps = [], **args):
    rust_binary(
        name = name,
        srcs = srcs if srcs else native.glob(["src/**/*.rs"]),
        deps = all_crate_deps(normal = True) + deps,
        proc_macro_deps = all_crate_deps(proc_macro = True) + proc_macro_deps,
        aliases = aliases(),
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
        deps = all_crate_deps(normal = True) + deps,
        proc_macro_deps = all_crate_deps(proc_macro = True) + proc_macro_deps,
        aliases = aliases(),
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
        deps = all_crate_deps(normal = True, normal_dev = True) + deps,
        proc_macro_deps = all_crate_deps(proc_macro = True, proc_macro_dev = True) + proc_macro_deps,
        aliases = aliases(),
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
        deps = [
            # This dependency is required in order to allow clang to link the final binaries. Normally rustc would inject this.
            "//core/alloc:alloc",
        ] + deps + all_crate_deps(normal = True),
        proc_macro_deps = all_crate_deps(
            proc_macro = True,
        ),
        disable_pipelining = True,
        aliases = aliases(),
        rustc_flags = _rustc_flags(),
        edition = "2021",
    )

def bitdrift_rust_library(name, srcs = None, deps = [], test_deps = [], tags = [], data = [], extra_aliases = {}, **args):
    rust_library(
        name = name,
        deps = [
            # This dependency is required in order to allow clang to link the final binaries. Normally rustc would inject this.
            "//core/alloc:alloc",
        ] + deps + all_crate_deps(normal = True),
        srcs = srcs if srcs else native.glob(["src/**/*.rs"]),
        proc_macro_deps = all_crate_deps(
            proc_macro = True,
        ),
        disable_pipelining = True,
        aliases = dict(extra_aliases.items() + aliases().items()),
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
        aliases = dict(extra_aliases.items() + aliases(
            normal_dev = True,
            proc_macro_dev = True,
        ).items()),
        data = data,
        deps = all_crate_deps(
            normal_dev = True,
        ) + test_deps,
        proc_macro_deps = all_crate_deps(
            proc_macro_dev = True,
        ),
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
