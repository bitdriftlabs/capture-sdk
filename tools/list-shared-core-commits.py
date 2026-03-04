#!/usr/bin/env python3
#
# List commits from shared-core main and mark capture-sdk tags that pin them.
#
# Usage: python list-shared-core-commits.py

import re
import subprocess

from collections import defaultdict
from pathlib import Path
from typing import Dict, Generator, List, Tuple

SCRIPT_DIR = Path(__file__).resolve().parent
REPOS_PATH = SCRIPT_DIR / Path("../..")


def capture_sdk_tags() -> List[str]:
    tags = subprocess.check_output(["git", "tag", "--sort=creatordate"])
    return [tag for tag in tags.decode().split("\n") if tag and tag.startswith("v")]


def parse_cargo_lock(tag: str) -> Dict[str, str]:
    cargo_lock = subprocess.check_output(["git", "show", f"{tag}:Cargo.lock"])
    modules = re.findall(
        r'source = "git.https://github.com/bitdriftlabs/(.*?).git\?rev=(.*?)[#$].*"',
        cargo_lock.decode(),
    )

    module_map: Dict[str, str] = {}
    for module, sha in modules:
        if module in module_map and module_map[module] != sha:
            raise ValueError("Found multiple shas for one module in Cargo.lock")

        module_map[module] = sha

    return module_map


def shared_core_main_commits() -> Generator[Tuple[str, str], None, None]:
    path = REPOS_PATH / "shared-core" / ".git"
    commits = subprocess.check_output(
        [
            "git",
            "--git-dir",
            path,
            "log",
            "--first-parent",
            "--reverse",
            "--pretty=format:%H%x09%s",
            "main",
        ]
    )

    for line in commits.decode().splitlines():
        sha, subject = line.split("\t", 1)
        yield sha, subject


def main() -> None:
    tags_by_shared_core_sha = defaultdict(list)
    for tag in capture_sdk_tags():
        modules = parse_cargo_lock(tag)
        if "shared-core" in modules:
            tags_by_shared_core_sha[modules["shared-core"]].append(tag)

    section_commits = []
    for sha, subject in shared_core_main_commits():
        section_commits.append((sha, subject))
        tags = sorted(set(tags_by_shared_core_sha.get(sha, [])))
        if not tags:
            continue

        print(f"capture-sdk versions: {', '.join(tags)}")
        for section_sha, section_subject in section_commits:
            print(f"  {section_sha[:8]} {section_subject}")
        print()
        section_commits = []

    if section_commits:
        print("capture-sdk versions: unreleased")
        for section_sha, section_subject in section_commits:
            print(f"  {section_sha[:8]} {section_subject}")


if __name__ == "__main__":
    main()
