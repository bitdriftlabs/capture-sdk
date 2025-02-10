load("@rules_multirun//:defs.bzl", "multirun", command = "command_force_opt")
load("@rules_shell//shell:sh_binary.bzl", "sh_binary")

# Commands

command(
    name = "lint-docstrings",
    arguments = ["check"],
    command = ":drstring_script",
)

command(
    name = "lint-swiftformat",
    arguments = ["--lint"],
    command = ":swiftformat_script",
)

command(
    name = "fix-docstrings",
    arguments = ["format"],
    command = ":drstring_script",
)

command(
    name = "fix-swiftformat",
    command = ":swiftformat_script",
)

# Scripts

sh_binary(
    name = "drstring_script",
    srcs = ["drstring.sh"],
    data = select({
        "//bazel:linux": ["@DrString_Linux"],
        "//conditions:default": ["@DrString//:drstring"],
    }),
)

sh_binary(
    name = "swiftformat_script",
    srcs = ["swiftformat.sh"],
    data = [
        "swiftformat.txt",
        "@SwiftFormat//:swiftformat",
    ],
)

# Flows

multirun(
    name = "lint",
    commands = [
        "lint-docstrings",
        "lint-swiftformat",
    ],
)

multirun(
    name = "fix",
    commands = [
        "fix-docstrings",
        "fix-swiftformat",
    ],
)
