#!/usr/bin/env python3

import json
from pathlib import Path
import shutil
import sys
from urllib.parse import unquote, urlsplit


def main():
    event_file, destination, *artifacts = sys.argv[1:]
    names = {}
    for artifact in artifacts:
        source, separator, staged = artifact.partition("=")
        names[source] = staged if separator else source

    outputs = {name: set() for name in names}
    with open(event_file) as events:
        for event in map(json.loads, events):
            for file in event.get("namedSetOfFiles", {}).get("files", []):
                uri = urlsplit(file.get("uri", ""))
                if uri.scheme != "file":
                    continue
                path = Path(unquote(uri.path))
                if path.name in outputs and path.is_file():
                    outputs[path.name].add(path)

    for name, paths in outputs.items():
        if len(paths) != 1:
            sys.exit(f"Expected one materialized Bazel output named {name}, got {paths}")
    for name, paths in outputs.items():
        shutil.copyfile(paths.pop(), Path(destination) / names[name])


if __name__ == "__main__":
    main()
