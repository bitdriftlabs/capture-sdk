load("@crates//:data.bzl", "DEP_DATA")
load("@crates//:defs.bzl", "all_crate_deps")
load("@rules_rust//rust:defs.bzl", "rust_binary", "rust_clippy", "rust_library", "rust_proc_macro", "rust_shared_library", "rust_test")
load("@rules_rust//cargo/private:cargo_build_script_wrapper.bzl", "cargo_build_script")


def _crate_deps(kind, package_name):
    if not DEP_DATA.get(package_name):
        return []
    if kind == "normal":
        name = "_deps"
        kwargs = {"normal": True}
    elif kind == "normal_dev":
        name = "_dev_deps"
        kwargs = {"normal_dev": True}
    elif kind == "proc_macro":
        name = "_proc_macro_deps"
        kwargs = {"proc_macro": True}
    elif kind == "proc_macro_dev":
        name = "_proc_macro_dev_deps"
        kwargs = {"proc_macro_dev": True}
    elif kind == "build":
        name = "_build_script_deps"
        kwargs = {"build": True}
    elif kind == "build_proc_macro":
        name = "_build_script_proc_macro_deps"
        kwargs = {"build_proc_macro": True}
    else:
        fail("Unknown deps kind: {}".format(kind))

    if native.existing_rule(name):
        return [name]

    return all_crate_deps(package_name = package_name, **kwargs)


def bitdrift_rust_crate(
        name,
        crate_name,
        crate_features = [],
        crate_srcs = None,
        crate_edition = None,
        proc_macro = False,
        shared_library = False,
        library = True,
        binary = False,
        binary_name = None,
        binary_crate_root = None,
        binary_srcs = None,
        binary_crate_name = None,
        build_script_enabled = True,
        build_script_data = [],
        compile_data = [],
        lib_data_extra = [],
        rustc_flags_extra = [],
        rustc_env = {},
        proc_macro_deps_extra = [],
        proc_macro_dev_deps_extra = [],
        deps_extra = [],
        integration_deps_extra = [],
        integration_compile_data_extra = [],
        test_data_extra = [],
        test_tags = [],
        extra_binaries = [],
        data = [],
        tags = [],
        visibility = None,
        alwayslink = None,
        disable_pipelining = True,
        **kwargs):
    package_name = native.package_name()
    has_dep_data = DEP_DATA.get(package_name) != None
    deps_name = name + "_deps"
    dev_deps_name = name + "_dev_deps"
    proc_macro_deps_name = name + "_proc_macro_deps"
    proc_macro_dev_deps_name = name + "_proc_macro_dev_deps"
    build_deps_name = name + "_build_deps"
    build_proc_macro_deps_name = name + "_build_proc_macro_deps"
    deps = _crate_deps("normal", package_name) + deps_extra
    dev_deps = _crate_deps("normal_dev", package_name)
    proc_macro_deps = _crate_deps("proc_macro", package_name) + proc_macro_deps_extra
    proc_macro_dev_deps = _crate_deps("proc_macro_dev", package_name) + proc_macro_dev_deps_extra

    rustc_env = {
        "BAZEL_PACKAGE": native.package_name(),
    } | rustc_env

    binaries = DEP_DATA.get(package_name, {}).get("binaries", {})
    lib_srcs = crate_srcs or native.glob(["src/**/*.rs"], exclude = binaries.values(), allow_empty = True)

    if build_script_enabled and native.glob(["build.rs"], allow_empty = True):
        cargo_build_script(
            name = name + "-build-script",
            srcs = ["build.rs"],
            deps = _crate_deps("build", package_name),
            proc_macro_deps = _crate_deps("build_proc_macro", package_name),
            data = build_script_data,
            version = "0.0.0",
        )

        deps = deps + [name + "-build-script"]

    if proc_macro and shared_library:
        fail("bitdrift_rust_crate: proc_macro and shared_library are mutually exclusive")

    if library and lib_srcs:
        if proc_macro:
            lib_rule = rust_proc_macro
            lib_kwargs = {
                "crate_features": crate_features,
            }
        elif shared_library:
            lib_rule = rust_shared_library
            lib_kwargs = {}
        else:
            lib_rule = rust_library
            lib_kwargs = {
                "crate_features": crate_features,
                "alwayslink": alwayslink,
                "disable_pipelining": disable_pipelining,
            }

        lib_args = {
            "name": name,
            "crate_name": crate_name,
            "deps": ["//core/alloc:alloc"] + deps,
            "proc_macro_deps": proc_macro_deps,
            "compile_data": compile_data,
            "data": data + lib_data_extra,
            "srcs": lib_srcs,
            "edition": crate_edition,
            "rustc_flags": rustc_flags_extra + _rustc_flags(),
            "rustc_env": rustc_env,
            "visibility": visibility,
            "tags": tags,
        }
        lib_args.update(lib_kwargs)
        lib_args.update(kwargs)
        lib_rule(**lib_args)

        rust_test(
            name = name + "-unit-tests",
            crate = name,
            deps = deps + dev_deps,
            proc_macro_deps = proc_macro_deps + proc_macro_dev_deps,
            rustc_flags = rustc_flags_extra + _rustc_flags(),
            rustc_env = rustc_env,
            data = data + test_data_extra,
            tags = test_tags + tags,
        )

        if proc_macro:
            proc_macro_deps += [name]
        else:
            deps += [name]

    sanitized_binaries = []
    cargo_env = {}
    for binary, main in binaries.items():
        sanitized_binaries.append(binary)
        cargo_env["CARGO_BIN_EXE_" + binary] = "$(rlocationpath :%s)" % binary

        rust_binary(
            name = binary,
            crate_name = binary.replace("-", "_"),
            crate_root = main,
            deps = deps,
            proc_macro_deps = proc_macro_deps,
            edition = crate_edition,
            rustc_flags = rustc_flags_extra + _rustc_flags(),
            srcs = native.glob(["src/**/*.rs"], allow_empty = True),
            visibility = visibility,
        )

    if binary:
        resolved_binary_name = binary_name or name
        binary_crate_name = binary_crate_name or resolved_binary_name.replace("-", "_")
        binary_root = binary_crate_root or "src/main.rs"
        binary_srcs = binary_srcs or native.glob(["src/**/*.rs"], allow_empty = True)
        sanitized_binaries.append(resolved_binary_name)
        cargo_env["CARGO_BIN_EXE_" + resolved_binary_name] = "$(rlocationpath :%s)" % resolved_binary_name

        rust_binary(
            name = resolved_binary_name,
            crate_name = binary_crate_name,
            crate_root = binary_root,
            deps = deps,
            proc_macro_deps = proc_macro_deps,
            edition = crate_edition,
            rustc_flags = rustc_flags_extra + _rustc_flags(),
            srcs = binary_srcs,
            visibility = visibility,
        )

    for binary_label in extra_binaries:
        sanitized_binaries.append(binary_label)
        binary = Label(binary_label).name
        cargo_env["CARGO_BIN_EXE_" + binary] = "$(rlocationpath %s)" % binary_label

    for test in native.glob(["tests/*.rs"], allow_empty = True):
        test_file_stem = test.removeprefix("tests/").removesuffix(".rs")
        test_crate_name = test_file_stem.replace("-", "_")
        test_name = name + "-" + test_file_stem.replace("/", "-")
        if not test_name.endswith("-test"):
            test_name += "-test"

        rust_test(
            name = test_name,
            crate_name = test_crate_name,
            crate_root = test,
            srcs = [test],
            data = native.glob(["tests/**"], allow_empty = True) + sanitized_binaries + data + test_data_extra,
            compile_data = native.glob(["tests/**"], allow_empty = True) + integration_compile_data_extra,
            deps = deps + dev_deps + integration_deps_extra,
            proc_macro_deps = proc_macro_deps + proc_macro_dev_deps,
            rustc_flags = rustc_flags_extra + _rustc_flags(),
            rustc_env = rustc_env,
            env = cargo_env,
            tags = test_tags + tags,
        )

    if not has_dep_data and (library or binary):
        fail("bitdrift_rust_crate: no DEP_DATA entry for package '%s'; make sure this directory is part of the Cargo workspace." % package_name)


def bitdrift_rust_library(
        name,
        crate_name = None,
        srcs = None,
        deps = [],
        test_deps = [],
        tags = [],
        data = [],
        **kwargs):
    bitdrift_rust_crate(
        name = name,
        crate_name = crate_name or name.replace("-", "_"),
        crate_srcs = srcs,
        deps_extra = deps,
        test_tags = tags,
        data = data,
        tags = tags,
        integration_deps_extra = test_deps,
        **kwargs
    )


def bitdrift_rust_library_only(name, srcs, deps = [], **kwargs):
    rust_library(
        name = name,
        srcs = srcs,
        deps = [
            "//core/alloc:alloc",
        ] + all_crate_deps(normal = True) + deps,
        proc_macro_deps = all_crate_deps(proc_macro = True),
        disable_pipelining = True,
        rustc_flags = _rustc_flags(),
        edition = "2021",
        **kwargs
    )


def bitdrift_rust_shared_library(name, srcs = None, deps = [], proc_macro_deps = [], rustc_flags = [], **args):
    package_name = native.package_name()
    rust_shared_library(
        name = name,
        srcs = srcs if srcs else native.glob(["src/**/*.rs"], allow_empty = True),
        deps = deps + all_crate_deps(normal = True, package_name = package_name),
        proc_macro_deps = all_crate_deps(proc_macro = True, package_name = package_name) + proc_macro_deps,
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


def bitdrift_rust_binary(name, srcs = None, deps = [], proc_macro_deps = [], **args):
    package_name = native.package_name()
    rust_binary(
        name = name,
        srcs = srcs if srcs else native.glob(["src/**/*.rs"], allow_empty = True),
        deps = deps + all_crate_deps(normal = True, package_name = package_name),
        proc_macro_deps = all_crate_deps(proc_macro = True, package_name = package_name) + proc_macro_deps,
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


def bitdrift_rust_test(name, deps = [], proc_macro_deps = [], **args):
    rust_test(
        name = name,
        rustc_flags = _rustc_flags(),
        edition = "2021",
        deps = deps + all_crate_deps(normal = True),
        proc_macro_deps = all_crate_deps(proc_macro = True) + proc_macro_deps,
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
        "-Aclippy::redundant-pub-crate",
        "-Aclippy::significant-drop-tightening",
        "-Aclippy::significant-drop-in-scrutinee",
    ]
