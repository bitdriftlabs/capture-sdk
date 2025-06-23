#!/usr/bin/env python3

import argparse
import datetime
from packaging import version

maven_metadata_template = """<?xml version="1.0" encoding="UTF-8"?>
<metadata modelVersion="1.1.0">
  <groupId>io.bitdrift</groupId>
  <artifactId>{library}</artifactId>
  <version>{latest_version}</version>
  <versioning>
    <latest>{latest_version}</latest>
    <release>{latest_version}</release>
    <versions>
{versions}
    </versions>
    <lastUpdated>{last_updated}</lastUpdated>
  </versioning>
</metadata>"""


def generate_maven_metadata(releases, library):
    if len(releases) == 0:
        print("releases cannot be empty")
        exit(1)

    print(f"provided releases: '{releases}'")

    releases = sorted(releases, key=lambda x: version.Version(x))
    latest_release = releases[-1]
    releases_tags = "\n".join(
        [f"        <version>{release}</version>" for release in releases]
    )

    maven_metadata = maven_metadata_template
    maven_metadata = maven_metadata.replace("{library}", library)
    maven_metadata = maven_metadata.replace("{latest_version}", latest_release)
    maven_metadata = maven_metadata.replace(
        "{last_updated}", datetime.datetime.now().strftime("%y%m%d%H%M%S")
    )
    maven_metadata = maven_metadata.replace("{versions}", releases_tags)

    with open("maven-metadata.xml", "w") as f:
        f.write(maven_metadata)


# Define a custom argument type for a list of strings
def list_of_strings(arg):
    return arg.split(",")


def _build_parser():
    parser = argparse.ArgumentParser()
    parser.add_argument(
        "--releases",
        help="The list of available SDK releases",
        type=list_of_strings,
        required=True,
    )
    parser.add_argument(
        "--library",
        help="The name of the library to generate maven-metadata for",
        type=str,
        required=True,
    )
    return parser


if __name__ == "__main__":
    args = _build_parser().parse_args()
    generate_maven_metadata(args.releases, args.library)
