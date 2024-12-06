# Check if a sha in a dependency is included in a release.
# Releases are inferred from tags in the main repo and dependecies
# are read from the Cargo.lock file.
#
# Usage: python check-release.py --find <sha>

import argparse
import re
import subprocess

from pathlib import Path
from typing import Dict, Optional, Generator, Tuple

SCRIPT_DIR = Path(__file__).resolve().parent
REPOS_PATH = SCRIPT_DIR / Path("../..")


def find_all_tags(sha: str) -> Generator[Tuple[str, str], None, None]:
    # Enumerates all existing tags, sorted by creation date and then checks
    # if the provided sha is included in all dependencies of the tag (parsed from
    # Cargo.lock).
    #
    # Returns every tag (from newer to older) where the sha is found.
    tags = subprocess.check_output(['git', 'tag', '--sort=-creatordate'])
    versions = [tag for tag in tags.decode().split('\n')
                if tag and tag.startswith('v')]
    for tag in versions:
        modules = parse_cargo_lock(tag)
        seen = (None, None)
        for module, sha_at_version in modules.items():
            if check_ancestor(module, sha_at_version, sha):
                seen = (module, tag)
                break
        else:
            return

        yield seen


def parse_cargo_lock(tag: str) -> Dict[str, str]:
    # Parse the Cargo.lock file of a given tag and return a dictionary
    # with the module name as key and the sha as value (e.g. shared-core abcdef0123).
    cargo_lock = subprocess.check_output(['git', 'show', f'{tag}:Cargo.lock'])
    modules = re.findall(
        r'source = "git.https://github.com/bitdriftlabs/(.*?).git\?rev=(.*?)[#$].*"', cargo_lock.decode())
    module_map = {}
    for module, sha in modules:
        if module in module_map and module_map[module] != sha:
            raise ValueError(
                "Found multiple shas for one module in Cargo.lock")

        module_map[module] = sha

    return module_map


def check_ancestor(module: str, sha_at_version: str, sha_to_check: str) -> bool:
    # Checks if the sha_at_version is an ancestor of sha_to_check in the repository
    # of the module. IMPORTANTE: The repository is expected to be at ../../
    path = REPOS_PATH / module / ".git"
    code = subprocess.call(['git', '--git-dir', path,
                            'merge-base', '--is-ancestor', sha_to_check, sha_at_version], stderr=subprocess.DEVNULL)
    return code == 0


def tag_for_version(version: str) -> Optional[str]:
    # Normalizes the given version and returns the tag if it exists
    tag = version if version.startswith('v') else f'v{version}'
    code = subprocess.call(
        ['git', 'rev-list', '-n', '1', tag], stderr=subprocess.DEVNULL, stdout=subprocess.DEVNULL)
    return tag if code == 0 else None


def main() -> None:
    parser = argparse.ArgumentParser(
        description='Check if a sha in a submodule is included in a release.')
    parser.add_argument('-f', '--find', metavar='sha',
                        help='sha to check', required=True)
    args = parser.parse_args()

    short = args.find[:8]
    tags = list(find_all_tags(args.find))
    if len(tags) > 0:
        module, tag = tags[-1]
        print(f'{short} first seen in {module} at {tag}')
    else:
        print(f'{short} not found in any tag')


if __name__ == '__main__':
    main()
