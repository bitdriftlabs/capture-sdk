# Check if a sha in a dependency is included in a release.
# Releases are inferred from tags in the main repo and dependecies
# are read from the Cargo.lock file.
#
# Usage: python check-release.py --find <sha> <version>

import argparse
import re
import subprocess

from pathlib import Path
from typing import Dict, Optional

SCRIPT_DIR = Path(__file__).resolve().parent
REPOS_PATH = SCRIPT_DIR / Path("../..")


def parse_cargo_lock(tag: str) -> Dict[str, str]:
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
    path = REPOS_PATH / module / ".git"
    code = subprocess.call(['git', '--git-dir', path,
                            'merge-base', '--is-ancestor', sha_to_check, sha_at_version])
    return code == 0


def tag_for_version(version: str) -> Optional[str]:
    tag = version if version.startswith('v') else f'v{version}'
    code = subprocess.call(
        ['git', 'rev-list', '-n', '1', tag], stderr=subprocess.DEVNULL, stdout=subprocess.DEVNULL)
    return tag if code == 0 else None


def main() -> None:
    parser = argparse.ArgumentParser(
        description='Check if a sha in a submodule is included in a release.')
    parser.add_argument('version', help='Version to check')
    parser.add_argument('-f', '--find', metavar='sha',
                        help='sha to check', required=True)
    args = parser.parse_args()

    tag = tag_for_version(args.version)
    if not tag:
        print(f'Version {args.version} not found')
        return

    modules = parse_cargo_lock(tag)
    short = args.find[:8]
    for module, sha_at_version in modules.items():
        if check_ancestor(module, sha_at_version, args.find):
            print(f'{short} found in {module} at {args.version}')
            return
    else:
        print(f'{short} not found in any module for tag {tag}')


if __name__ == '__main__':
    main()
