#!/usr/bin/env python3
"""Map the APK's native libraries and the local debug files to their build IDs, and say which debug
file belongs to which packaged library.

    apk_build_ids.py <apk> [--dif build/release/dif] [--out build/release/build_ids.json]

The JSON is what verify_native.py needs. Note: libraries stored uncompressed in the APK are loaded in
place, so the trace names their mapping `.../base.apk` rather than `libcapture.so`. Use the build ID,
not the mapping name, to find their frames.
"""
import argparse
import json
import pathlib
import re
import subprocess
import tempfile
import zipfile

BID = re.compile(r"BuildID\[[^\]]*\]=([0-9a-f]+)")


def build_id(path):
    m = BID.search(subprocess.run(["file", "-b", str(path)], capture_output=True, text=True).stdout)
    return m.group(1) if m else None


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("apk")
    ap.add_argument("--dif", help="directory with *.debug files")
    ap.add_argument("--abi", default="arm64-v8a")
    ap.add_argument("--out")
    a = ap.parse_args()
    libs = {}
    with zipfile.ZipFile(a.apk) as z, tempfile.TemporaryDirectory() as tmp:
        for n in z.namelist():
            if n.startswith(f"lib/{a.abi}/") and n.endswith(".so"):
                p = z.extract(n, tmp)
                libs[pathlib.Path(n).name] = {"build_id": build_id(p),
                                              "stored_uncompressed": z.getinfo(n).compress_type == zipfile.ZIP_STORED}
    difs = {}
    if a.dif:
        for p in sorted(pathlib.Path(a.dif).glob("*.debug")):
            difs[p.name] = {"build_id": build_id(p), "path": str(p.resolve())}
    for name, info in libs.items():
        match = next((v["path"] for v in difs.values() if v["build_id"] and v["build_id"] == info["build_id"]), None)
        info["debug_file"] = match
    res = {"apk": a.apk, "libraries": libs, "debug_files": difs,
           "app_libs_without_debug_file": [n for n in ("libcapture.so", "libgradletestapp_native.so")
                                           if n in libs and not libs[n]["debug_file"]]}
    if a.out:
        pathlib.Path(a.out).write_text(json.dumps(res, indent=2))
    print(json.dumps({n: v for n, v in libs.items() if n in ("libcapture.so", "libgradletestapp_native.so")}, indent=1))
    if a.dif and res["app_libs_without_debug_file"]:
        print("NO MATCHING DEBUG FILE:", res["app_libs_without_debug_file"])


if __name__ == "__main__":
    main()
