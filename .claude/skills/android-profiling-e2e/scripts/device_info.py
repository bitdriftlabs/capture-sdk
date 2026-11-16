#!/usr/bin/env python3
"""Classify the connected Android target and print what that means for this test.

    device_info.py [--out device.json]

Classes (see references/devices.md for how to react to each):
  real-device      physical phone/tablet, normally a `user` build: no adb root
  emulator-nonroot emulator with a `user` build (Google Play system images): no adb root
  emulator-root    emulator with a `userdebug`/`eng` build (Google APIs / AOSP images): adb root possible
Read-only: it never runs `adb root` itself.
"""
import argparse
import json
import pathlib
import re
import sys

sys.path.insert(0, str(pathlib.Path(__file__).resolve().parent))
from common import PKG, adb  # noqa: E402

PROPS = ["ro.product.manufacturer", "ro.product.model", "ro.build.version.release", "ro.build.version.sdk",
         "ro.build.type", "ro.debuggable", "ro.kernel.qemu", "ro.boot.qemu", "ro.hardware", "ro.product.cpu.abi",
         "security.perf_harden"]


def prop(name):
    return adb("shell", "getprop", name, check=False).strip()


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--out")
    a = ap.parse_args()
    p = {k: prop(k) for k in PROPS}
    emulator = p["ro.kernel.qemu"] == "1" or p["ro.boot.qemu"] == "1" or p["ro.hardware"] in ("ranchu", "goldfish")
    root_capable = p["ro.build.type"] in ("userdebug", "eng") or p["ro.debuggable"] == "1"
    shell_uid = adb("shell", "id", "-u", check=False).strip()
    cls = ("emulator-root" if root_capable else "emulator-nonroot") if emulator else (
        "real-device-rooted" if root_capable else "real-device")
    size = re.search(r"(\d+)x(\d+)", adb("shell", "wm", "size", check=False))
    pkg = adb("shell", "dumpsys", "package", PKG, check=False)
    info = {
        "class": cls,
        "props": p,
        "adbd_is_root": shell_uid == "0",
        "screen": {"w": int(size.group(1)), "h": int(size.group(2))} if size else None,
        "sdk_ok_for_profiling_manager": p["ro.build.version.sdk"].isdigit() and int(p["ro.build.version.sdk"]) >= 35,
        "abi": p["ro.product.cpu.abi"],
        "app_installed": bool(re.search(r"versionCode=\d+", pkg)),
        "app_version": (re.search(r"versionCode=(\d+)", pkg) or [None, None])[1],
        "app_debuggable": "DEBUGGABLE" in pkg,
        "rate_limiter_disabled": adb("shell", "device_config", "get", "profiling_testing", "rate_limiter.disabled", check=False).strip(),
    }
    notes = []
    if not info["sdk_ok_for_profiling_manager"]:
        notes.append("API < 35: ProfilingManager is unavailable, every profiling command will fail. Use an Android 15+ target.")
    if info["abi"] not in ("arm64-v8a",):
        notes.append(f"ABI {info['abi']}: build with -Prust-target={info['abi']} and use that ABI's debug files (apk_build_ids.py --abi).")
    if emulator:
        notes.append("Emulator: stack sampling may have no hardware perf counters; if the built-in command fails, report it as environment.")
    if root_capable and not info["adbd_is_root"]:
        notes.append("adb root is possible (ask first): lets you read prefs on release builds and pull app-private files.")
    if not root_capable:
        notes.append("No root: run-as works only for debuggable (debug) builds; release builds are checked by behavior.")
    info["notes"] = notes
    if a.out:
        pathlib.Path(a.out).write_text(json.dumps(info, indent=2))
    print(json.dumps(info, indent=1))


if __name__ == "__main__":
    main()
