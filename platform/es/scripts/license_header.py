import argparse
from pathlib import Path
import subprocess

HEADER = """// capture-es - bitdrift's ES SDK
// Copyright Bitdrift, Inc. All rights reserved.
//
// Use of this source code is governed by a source available license that can be found in the
// LICENSE file or at:
// https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt
"""

EXTENSIONS = {".kt", ".java", ".swift", ".ts", ".js"}


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--fix", action="store_true", help="Insert missing headers")
    args = parser.parse_args()

    files = subprocess.check_output(["git", "ls-files", "-z", "--", ".", ":(exclude)examples"])
    failed = False
    for name in files.decode().split("\0"):
        path = Path(name)
        if path.suffix not in EXTENSIONS or not path.is_file():
            continue
        content = path.read_text()
        if content.startswith(HEADER):
            continue
        if args.fix:
            path.write_text(HEADER + "\n" + content)
        else:
            print(f"Missing license header: {path}")
            failed = True

    return int(failed)


if __name__ == "__main__":
    raise SystemExit(main())
