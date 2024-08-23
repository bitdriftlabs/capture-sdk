load("@rules_multirun//:defs.bzl", "multirun", command = "command_force_opt")

# Commands

command(
    name = "lint-docstrings",
    arguments = ["check"],
    command = ":drstring_script",
)

command(
    name = "lint-swiftlint",
    arguments = ["--strict"],
    command = ":swiftlint_script",
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
    name = "fix-swiftlint",
    arguments = [
        "--fix",
        "--format",
    ],
    command = ":swiftlint_script",
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
    name = "swiftlint_script",
    srcs = ["swiftlint.sh"],
    data = ["@SwiftLint//:swiftlint"],
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
        "lint-swiftlint",
    ],
)

multirun(
    name = "fix",
    commands = [
        "fix-docstrings",
        "fix-swiftlint",
        "fix-swiftformat",
        "lint-swiftlint",
    ],
)
