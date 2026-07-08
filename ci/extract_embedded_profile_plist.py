#!/usr/bin/env python3

import sys
from pathlib import Path


def main() -> int:
    if len(sys.argv) != 3:
        print("usage: extract_embedded_profile_plist.py <source.mobileprovision> <destination.plist>", file=sys.stderr)
        return 1

    source = Path(sys.argv[1])
    destination = Path(sys.argv[2])
    data = source.read_bytes()
    start = data.find(b"<?xml")
    end = data.find(b"</plist>")
    if start == -1 or end == -1:
        print("error: could not extract plist payload from embedded provisioning profile", file=sys.stderr)
        return 1

    destination.write_bytes(data[start : end + len(b"</plist>")])
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
