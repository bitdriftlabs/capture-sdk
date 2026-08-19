#!/usr/bin/env python3
"""Device control for capture-sdk Android runs.

Subcommands:
  devices                          list connected devices (serial, api, model, locked)
  install [--app X] [--serial S]   build + install the debug app (all devices by default)
  logprop <filter> [--serial S]    set debug.bitdrift.internal_rust_log
  mode <name> --on|--off           apply/undo a device state; `mode reset` restores everything
  verify <name>                    print the verification output for a mode
  action <name> [--seconds N]      launch|home|back|recents|screen-off|wake|kill|force-stop|
                                   freeze|unfreeze|rotate|rotate-reset|launch-activity|wait
  mark <text>                      stamp a device-clock marker into logcat
  state                            dump the full device state relevant to these runs

Everything targets every connected device in parallel unless --serial or --sequential is given.
Actions are stamped into logcat with `log -t ANDROID-RUN` so timelines stay on the device clock.
"""
from __future__ import annotations

import argparse
import concurrent.futures as cf
import re
import subprocess
import sys

MARKER_TAG = "ANDROID-RUN"

APPS = {
    "gradle-test-app": (
        "io.bitdrift.gradletestapp",
        "io.bitdrift.gradletestapp.ui.activities.MainActivity",
        ":gradle-test-app:installDebug",
    ),
    "gradle-tv-test-app": (
        "io.bitdrift.gradletvtestapp",
        "io.bitdrift.gradletvtestapp.MainActivity",
        ":gradle-tv-test-app:installDebug",
    ),
}
DEFAULT_APP = "gradle-test-app"


# ---------------------------------------------------------------- adb plumbing


def adb(serial: str, cmd: str, timeout: int = 60) -> str:
    """Run `adb -s <serial> shell <cmd>` and return stdout with CRs stripped."""
    try:
        r = subprocess.run(["adb", "-s", serial, "shell", cmd],
                           capture_output=True, text=True, timeout=timeout)
        return r.stdout.replace("\r", "")
    except subprocess.TimeoutExpired:
        return ""


def devices() -> list[str]:
    out = subprocess.run(["adb", "devices"], capture_output=True, text=True).stdout
    return [ln.split()[0] for ln in out.splitlines()[1:]
            if ln.strip() and ln.split()[-1] == "device"]


def resolve_targets(args) -> list[str]:
    if getattr(args, "serial", None):
        return [args.serial]
    found = devices()
    if not found:
        sys.exit("no devices connected (check `adb devices`)")
    return found


def fanout(targets: list[str], fn, sequential: bool = False) -> dict[str, object]:
    """Apply fn(serial) across targets. Parallel by default; sequential when timing matters."""
    if sequential or len(targets) == 1:
        return {s: fn(s) for s in targets}
    with cf.ThreadPoolExecutor(max_workers=len(targets)) as ex:
        futs = {ex.submit(fn, s): s for s in targets}
        return {futs[f]: f.result() for f in cf.as_completed(futs)}


def api_level(serial: str) -> int:
    try:
        return int(adb(serial, "getprop ro.build.version.sdk").strip() or 0)
    except ValueError:
        return 0


def app_uid(serial: str, pkg: str) -> str | None:
    for ln in adb(serial, "pm list packages -U").splitlines():
        if f"{pkg} " in ln:
            m = re.search(r"uid:(\d+)", ln)
            if m:
                return m.group(1)
    return None


def is_locked(serial: str) -> bool:
    return "deviceLocked=1" in adb(serial, "dumpsys trust")


def require_unlocked(serial: str) -> None:
    """A locked device silently invalidates runs: the app launches behind the keyguard and
    backgrounds itself, so any later action lands on an already-backgrounded app."""
    if is_locked(serial):
        sys.exit(f"{serial}: device is LOCKED. Runs would be invalid — the app launches behind "
                 f"the keyguard and backgrounds itself. Ask the user to unlock it; do not try to "
                 f"bypass a secure keyguard.")


def mark(serial: str, text: str) -> None:
    adb(serial, f'log -p i -t {MARKER_TAG} "{text}"')


# ---------------------------------------------------------------- modes

def _wifi_networks(serial: str) -> list[str]:
    return [ln.split(";")[0].strip()
            for ln in adb(serial, "cmd netpolicy list wifi-networks").splitlines()
            if ln.split(";")[0].strip()]


def mode(serial: str, name: str, on: bool, pkg: str) -> str:
    """Apply or undo a device state. Returns the verification string."""
    if name == "airplane":
        adb(serial, f"cmd connectivity airplane-mode {'enable' if on else 'disable'}")

    elif name == "battery-saver":
        if on:
            adb(serial, "dumpsys battery unplug")
            adb(serial, "dumpsys battery set level 15")
            adb(serial, "cmd power set-adaptive-power-saver-enabled false")
            adb(serial, "settings put global low_power 1")
        else:
            adb(serial, "settings put global low_power 0")
            adb(serial, "dumpsys battery reset")

    elif name in ("doze-deep", "doze-light"):
        depth = "deep" if name == "doze-deep" else "light"
        if on:
            adb(serial, "dumpsys battery unplug")
            adb(serial, f"dumpsys deviceidle whitelist -{pkg}")
            adb(serial, f"dumpsys deviceidle enable {depth}")
            adb(serial, f"dumpsys deviceidle force-idle {depth}")
        else:
            adb(serial, "dumpsys deviceidle unforce")
            adb(serial, "dumpsys deviceidle enable all")
            adb(serial, "dumpsys battery reset")

    elif name == "doze-prearm":
        # Everything except force-idle, so the final command lands fast after ON_STOP.
        adb(serial, "dumpsys battery unplug")
        adb(serial, f"dumpsys deviceidle whitelist -{pkg}")
        adb(serial, "dumpsys deviceidle enable deep")

    elif name == "data-saver":
        for nid in _wifi_networks(serial):
            adb(serial, f"cmd netpolicy set metered-network {nid} "
                        f"{'true' if on else 'undefined'}")
        adb(serial, f"cmd netpolicy set restrict-background {'true' if on else 'false'}")
        # The global toggle and the metered-network marks are not the whole story: a per-uid
        # REJECT_METERED_BACKGROUND policy can also be set, and it survives both of the above.
        # It then shows up as METERED_USER_RESTRICTED in the effective blocked state and silently
        # restricts every later run — on someone's real phone. Clearing it is part of turning
        # data-saver off, not a separate step.
        uid = app_uid(serial, pkg)
        if uid and not on:
            adb(serial, f"cmd netpolicy remove restrict-background-blacklist {uid}")

    elif name == "standby-restricted":
        adb(serial, f"am set-standby-bucket {pkg} {'restricted' if on else 'active'}")

    elif name == "bg-restricted":
        adb(serial, f"am set-bg-restriction-level {pkg} "
                    f"{'background_restricted' if on else 'unrestricted'}")
        adb(serial, f"cmd appops set {pkg} RUN_ANY_IN_BACKGROUND "
                    f"{'ignore' if on else 'allow'}")

    elif name == "freezer":
        adb(serial, f"am {'freeze' if on else 'unfreeze'} --sticky {pkg}")

    elif name == "idle-allowlist":
        adb(serial, f"dumpsys deviceidle whitelist {'+' if on else '-'}{pkg}")

    elif name == "reset":
        for m in ("airplane", "battery-saver", "doze-deep", "data-saver",
                  "standby-restricted", "bg-restricted", "freezer", "idle-allowlist"):
            mode(serial, m, False, pkg)
        adb(serial, "svc power stayon false")
        adb(serial, "input keyevent KEYCODE_WAKEUP")
        adb(serial, "wm dismiss-keyguard")
        # A device left in forced landscape silently changes layout for every later run.
        adb(serial, "settings put system user_rotation 0")
        adb(serial, "settings put system accelerometer_rotation 1")

    else:
        sys.exit(f"unknown mode: {name}")

    return verify(serial, name, pkg)


def verify(serial: str, name: str, pkg: str) -> str:
    """One-line verification per mode. Several modes fail silently, so always check."""
    uid = app_uid(serial, pkg) or "?"
    checks = {
        "airplane": lambda: adb(serial, "cmd connectivity airplane-mode").strip(),
        "battery-saver": lambda: "low_power=" + adb(serial, "settings get global low_power").strip()
                                 + " " + _grep(adb(serial, "dumpsys netpolicy"), "Restrict power"),
        "doze-deep": lambda: "deep=" + adb(serial, "dumpsys deviceidle get deep").strip(),
        "doze-light": lambda: "light=" + adb(serial, "dumpsys deviceidle get light").strip(),
        # Three independent switches, all of which must be off for data-saver to really be off.
        # uid_policy is the one that used to be missed; it reads as METERED_USER_RESTRICTED.
        "data-saver": lambda: adb(serial, "cmd netpolicy get restrict-background").strip()
                              + " | metered=" + str(sum(
                                  1 for ln in adb(serial, "cmd netpolicy list wifi-networks").splitlines()
                                  if ln.strip().endswith("true")))
                              + " | uid_policy=" + (
                                  "REJECT_METERED_BACKGROUND" if f"UID={uid} policy=1" in
                                  adb(serial, "dumpsys netpolicy") else "none"),
        # get-bg-restriction-level returns 'unknown' on API 36/37 — the appop is the usable check.
        "bg-restricted": lambda: _grep(adb(serial, f"cmd appops get {pkg} RUN_ANY_IN_BACKGROUND"),
                                       "RUN_ANY_IN_BACKGROUND"),
        "standby-restricted": lambda: "bucket=" + adb(serial, f"am get-standby-bucket {pkg}").strip(),
        "freezer": lambda: "frozen_events=" + str(
            adb(serial, "dumpsys activity processes").count("frozen=true")),
        "idle-allowlist": lambda: "allowlisted=" + str(
            pkg in adb(serial, "dumpsys deviceidle whitelist")),
        "reset": lambda: state_line(serial, pkg),
    }
    fn = checks.get(name)
    return fn() if fn else f"(no verification for {name})"


def _grep(text: str, needle: str) -> str:
    for ln in text.splitlines():
        if needle in ln:
            return ln.strip()
    return ""


def state_line(serial: str, pkg: str) -> str:
    uid = app_uid(serial, pkg) or "?"
    eff = ""
    for ln in adb(serial, "dumpsys netpolicy").splitlines():
        if f"UID={uid} state=" in ln:
            m = re.search(r"effective=([A-Z_|]+)", ln)
            eff = m.group(1) if m else ""
            break
    return (f"airplane={adb(serial, 'cmd connectivity airplane-mode').strip()} "
            f"low_power={adb(serial, 'settings get global low_power').strip()} "
            f"doze={adb(serial, 'dumpsys deviceidle get deep').strip()}/"
            f"{adb(serial, 'dumpsys deviceidle get light').strip()} "
            f"bucket={adb(serial, f'am get-standby-bucket {pkg}').strip()} "
            f"blocked={eff or 'NONE'}")


# ---------------------------------------------------------------- actions

ACTIONS = {
    "home": "input keyevent KEYCODE_HOME",
    "back": "input keyevent KEYCODE_BACK",
    "recents": "input keyevent KEYCODE_APP_SWITCH",
    "screen-off": "input keyevent KEYCODE_SLEEP",
    "wake": "input keyevent KEYCODE_WAKEUP",
}


def action(
    serial: str,
    name: str,
    pkg: str,
    activity: str,
    seconds: float = 0,
    component: str | None = None,
) -> str:
    if name == "wait":
        import time
        mark(serial, f"ACTION wait {seconds}s")
        time.sleep(seconds)
        return f"waited {seconds}s"

    mark(serial, f"ACTION {name}")

    if name in ACTIONS:
        adb(serial, ACTIONS[name])
    elif name == "launch":
        adb(serial, f"am start -W -n {pkg}/{activity}", timeout=90)
    elif name == "launch-activity":
        # Start a specific component, for cases where the transition itself is under test.
        if not component:
            sys.exit("launch-activity needs a `component` in the scenario step")
        target = component if "/" in component else f"{pkg}/{component}"
        adb(serial, f"am start -W -n {target}", timeout=90)
    elif name == "rotate":
        # user_rotation is ignored while auto-rotate is on, so disable the sensor first or this is a
        # silent no-op — the same class of trap as data-saver needing a metered network.
        adb(serial, "settings put system accelerometer_rotation 0")
        adb(serial, "settings put system user_rotation 1")
    elif name == "rotate-reset":
        adb(serial, "settings put system user_rotation 0")
        adb(serial, "settings put system accelerometer_rotation 1")
    elif name == "kill":
        adb(serial, f"am kill {pkg}")
    elif name == "force-stop":
        adb(serial, f"am force-stop {pkg}")
    elif name == "freeze":
        adb(serial, f"am freeze --sticky {pkg}")
    elif name == "unfreeze":
        adb(serial, f"am unfreeze --sticky {pkg}")
    else:
        sys.exit(f"unknown action: {name}")
    return name


# ---------------------------------------------------------------- install

def install(app: str, serial: str, repo_root: str = ".") -> str:
    _, _, task = APPS[app]
    r = subprocess.run(
        ["./platform/jvm/gradlew", "-p", "platform/jvm", task],
        capture_output=True, text=True, cwd=repo_root,
        env={**__import__("os").environ, "ANDROID_SERIAL": serial},
        timeout=900,
    )
    tail = [ln for ln in r.stdout.splitlines() if "Installed on" in ln or "BUILD" in ln]
    return "; ".join(tail) or (r.stderr.strip().splitlines() or ["failed"])[-1]


# ---------------------------------------------------------------- cli

def main() -> None:
    ap = argparse.ArgumentParser(description=__doc__,
                                 formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--serial", help="target one device (default: all connected)")
    ap.add_argument("--app", default=DEFAULT_APP, choices=list(APPS))
    ap.add_argument("--sequential", action="store_true",
                    help="one device at a time; use when sub-second timings must be trustworthy")
    sub = ap.add_subparsers(dest="cmd", required=True)

    sub.add_parser("devices")
    sub.add_parser("state")
    sub.add_parser("install")
    p = sub.add_parser("logprop"); p.add_argument("filter")
    p = sub.add_parser("mode"); p.add_argument("name")
    g = p.add_mutually_exclusive_group(); g.add_argument("--on", action="store_true")
    g.add_argument("--off", action="store_true")
    p = sub.add_parser("verify"); p.add_argument("name")
    p = sub.add_parser("action"); p.add_argument("name"); p.add_argument("--seconds", type=float, default=0)
    p.add_argument("--component", help="for launch-activity: Class or pkg/Class")
    p = sub.add_parser("mark"); p.add_argument("text")

    args = ap.parse_args()
    pkg, activity, _ = APPS[args.app]

    if args.cmd == "devices":
        for s in devices():
            print(f"{s:24s} api={api_level(s):3d} "
                  f"model={adb(s, 'getprop ro.product.model').strip():18s} "
                  f"uid={app_uid(s, pkg)} locked={is_locked(s)}")
        return

    targets = resolve_targets(args)

    if args.cmd == "install":
        for s, out in fanout(targets, lambda s: install(args.app, s), args.sequential).items():
            print(f"[{s}] {out}")
        return

    if args.cmd == "logprop":
        fanout(targets, lambda s: adb(s, f"setprop debug.bitdrift.internal_rust_log "
                                         f"'{args.filter}'"), args.sequential)
        print(f"set on {len(targets)} device(s): {args.filter}  "
              f"(read at launch; set before starting the app)")
        return

    if args.cmd == "mode":
        on = args.on or not args.off
        for s, out in fanout(targets, lambda s: mode(s, args.name, on, pkg), args.sequential).items():
            print(f"[{s}] {args.name} {'on' if on else 'off'} -> {out}")
        return

    if args.cmd == "verify":
        for s, out in fanout(targets, lambda s: verify(s, args.name, pkg), args.sequential).items():
            print(f"[{s}] {args.name}: {out}")
        return

    if args.cmd == "action":
        if args.name == "launch":
            for s in targets:
                require_unlocked(s)
        for s, out in fanout(targets,
                            lambda s: action(s, args.name, pkg, activity, args.seconds,
                                             getattr(args, "component", None)),
                            args.sequential).items():
            print(f"[{s}] {out}")
        return

    if args.cmd == "mark":
        fanout(targets, lambda s: mark(s, args.text), args.sequential)
        print(f"marked on {len(targets)} device(s): {args.text}")
        return

    if args.cmd == "state":
        for s, out in fanout(targets, lambda s: state_line(s, pkg), args.sequential).items():
            print(f"[{s}] {out}")
        return


if __name__ == "__main__":
    main()
