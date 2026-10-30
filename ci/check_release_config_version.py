#!/usr/bin/env python3

import argparse
import re
import sys


SEMVER = re.compile(
    r"(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)"
    r"(?:-([0-9A-Za-z-]+(?:\.[0-9A-Za-z-]+)*))?"
    r"(?:\+[0-9A-Za-z-]+(?:\.[0-9A-Za-z-]+)*)?"
)


def sdk_minor(version):
    match = SEMVER.fullmatch(version)
    if match is None or any(
        identifier.isdigit() and len(identifier) > 1 and identifier.startswith("0")
        for identifier in (match.group(4) or "").split(".")
    ):
        raise ValueError(f"Invalid SDK SemVer: {version!r}")
    return int(match.group(2))


def validate(version, config_version):
    minor = sdk_minor(version)
    if re.fullmatch(r"[1-9][0-9]*", config_version) is None:
        raise ValueError(f"Invalid config-version binary output: {config_version!r}")
    if minor != int(config_version):
        raise ValueError(
            f"SDK {version} has minor {minor}, but included shared-core config version is "
            f"{config_version}; release MAJOR.{config_version}.PATCH or update shared-core."
        )


def main():
    parser = argparse.ArgumentParser(description="Require the SDK minor to equal its shared-core config version")
    parser.add_argument("--version", required=True)
    parser.add_argument("--config-version", required=True)
    arguments = parser.parse_args()
    try:
        validate(arguments.version, arguments.config_version)
    except ValueError as error:
        print(f"Release version validation failed: {error}", file=sys.stderr)
        return 1
    print(f"SDK {arguments.version} matches shared-core config {arguments.config_version}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
