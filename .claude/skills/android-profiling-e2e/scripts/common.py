#!/usr/bin/env python3
"""Shared helpers for the android-profiling-e2e scripts: the cell table, tool discovery,
trace_processor queries and adb.

`python3 common.py --check` reports which tools were found.
"""
import csv
import glob
import io
import os
import shlex
import shutil
import subprocess
import sys

PKG = "io.bitdrift.gradletestapp"
EXT_DIR = f"/sdcard/Android/data/{PKG}/files/e2e-traces"

TRIGGERS = {
    # button text regex -> how the workflow matches the log it emits
    "Log JSON Field": {"operator": "EQUAL", "string_value": "JSON field to match upon."},
    "Log Single Message": {"operator": "REGEX", "string_value": "^Log message with level: .*$"},
}


def _i(v):
    return {"sint_data": str(v)}


def _b(v):
    return {"bool_data": bool(v)}


def _s(v):
    return {"string_data": str(v)}


_HP = {"duration_ms": _i(5000), "buffer_size_kb": _i(8192), "sampling_interval_bytes": _i(65536)}

# One entry per cell type. `command` is a registered command name, or an int for a built-in type.
CELLS = {
    "heap_dump": {
        "command": "java_heap_dump_demo", "args": {"buffer_size_kb": _i(65536)},
        "trigger": "Log Single Message", "wait": 90, "activity": 4, "interval": "30s",
        "device_copy": True, "kind": "heap_graph", "label": "Java heap dump",
    },
    "heap_profile_native": {
        "command": "heap_profile_demo", "args": {**_HP, "track_java_allocations": _b(False)},
        "trigger": "Log JSON Field", "wait": 45, "activity": 7, "interval": "10s",
        "device_copy": True, "kind": "heap_profile", "label": "Heap profile, Java allocations off",
    },
    "heap_profile_java": {
        "command": "heap_profile_demo", "args": {**_HP, "track_java_allocations": _b(True)},
        "trigger": "Log JSON Field", "wait": 45, "activity": 7, "interval": "10s",
        "device_copy": True, "kind": "heap_profile", "label": "Heap profile, Java allocations on",
    },
    "heap_profile_trace": {
        "command": 13, "args": {**_HP, "duration_ms": _i(2000), "sampling_interval_bytes": _i(4096),
                                "track_java_allocations": _b(True)},
        "trigger": "Log Single Message", "wait": 5, "activity": 7, "interval": "60s",
        "device_copy": False, "kind": "heap_profile", "label": "Heap profile (built-in command)",
    },
    "system_trace": {
        "command": 14, "args": {"duration_ms": _i(5000)},
        "trigger": "Log Single Message", "wait": 5, "activity": 7, "interval": "60s",
        "device_copy": False, "kind": "system_timeline", "label": "System trace (built-in command)",
    },
    "java_heap_dump": {
        "command": 15, "args": {"buffer_size_kb": _i(65536)},
        "trigger": "Log Single Message", "wait": 5, "activity": 4, "interval": "60s",
        "device_copy": False, "kind": "heap_graph", "label": "Java heap dump (built-in command)",
    },
    "stack_sampling": {
        # The SDK's built-in STACK_SAMPLING_TRACE command (well-known type 3): the production CPU
        # profiler path. It doesn't go through the test app's SampleCommands, so there is no on-device copy.
        "command": 3, "args": {"duration_ms": _i(5000), "frequency_hz": _i(100), "buffer_size_kb": _i(1024)},
        "trigger": "Log JSON Field", "wait": 45, "activity": 7, "interval": "10s",
        "device_copy": False, "kind": "perf_sample", "label": "Stack sampling (built-in command)",
    },
}

BUILTIN_NAMES = {3: "stack_sampling_trace", 13: "heap_profile_trace", 14: "system_trace", 15: "java_heap_dump"}
STRICT_WORKFLOW_CELLS = {"heap_profile_trace", "system_trace", "java_heap_dump"}


def command_name(cell_type):
    c = CELLS[cell_type]["command"]
    return BUILTIN_NAMES.get(c, str(c)) if isinstance(c, int) else c


def bd(*args):
    command = shlex.split(os.environ.get("BD_CLI", "bd"))
    if not command:
        raise ValueError("BD_CLI must name a command")
    return subprocess.run([*command, *args], capture_output=True, text=True, timeout=60)


# ---------------------------------------------------------------- tools

def find_trace_processor():
    env = os.environ.get("TRACE_PROCESSOR")
    if env:
        return env
    cands = sorted(glob.glob(os.path.expanduser(
        "~/.claude/plugins/cache/perfetto/perfetto/*/skills/perfetto/bin/trace_processor")))
    if cands:
        return cands[-1]
    return shutil.which("trace_processor") or shutil.which("trace_processor_shell")


def find_llvm_symbolizer():
    env = os.environ.get("LLVM_SYMBOLIZER")
    if env:
        return env
    for p in ("/opt/homebrew/opt/llvm/bin/llvm-symbolizer", "/usr/local/opt/llvm/bin/llvm-symbolizer"):
        if os.path.exists(p):
            return p
    w = shutil.which("llvm-symbolizer")
    if w:
        return w
    ndk = glob.glob(os.path.expanduser("~/Library/Android/sdk/ndk/*/toolchains/llvm/prebuilt/*/bin/llvm-symbolizer"))
    return sorted(ndk)[-1] if ndk else None


TP = find_trace_processor()


def tp(trace, sql=None, *, sql_file=None):
    """Run one query; returns a list of dicts with None for NULL. Raises if trace_processor fails."""
    if not TP:
        raise RuntimeError("trace_processor not found; set TRACE_PROCESSOR to its executable")
    if (sql is None) == (sql_file is None):
        raise ValueError("specify exactly one of sql and sql_file")
    env = {k: v for k, v in os.environ.items() if k not in ("PERFETTO_BINARY_PATH", "DEBUGINFOD_URLS")}
    args = [str(trace), sql] if sql_file is None else ["-f", str(sql_file), str(trace)]
    r = subprocess.run([TP, "query", "--format", "csv", *args], capture_output=True, text=True, env=env)
    if r.returncode != 0 and "unrecognized option `--format'" in r.stderr:
        # Older Perfetto versions always emit CSV and do not recognize --format.
        r = subprocess.run([TP, "query", *args], capture_output=True, text=True, env=env)
    if r.returncode != 0:
        raise RuntimeError(f"trace_processor failed on {trace}: {(r.stderr or r.stdout)[:800]}")
    lines = r.stdout.splitlines()
    start = next((i for i, line in enumerate(lines) if line.startswith('"')), None)
    if start is None:
        return []
    rows = csv.DictReader(io.StringIO("\n".join(lines[start:])))
    return [{k: (None if v in ("[NULL]", "") else v) for k, v in row.items()} for row in rows]


def adb(*args, check=True):
    r = subprocess.run(["adb", *args], capture_output=True, text=True)
    if check and r.returncode != 0:
        raise RuntimeError(f"adb {' '.join(args)} failed: {r.stderr.strip()}")
    return r.stdout


def check():
    ok = True
    for name, path in (("trace_processor", TP), ("llvm-symbolizer", find_llvm_symbolizer()),
                       ("zstd", shutil.which("zstd")), ("adb", shutil.which("adb")),
                       ("bd", os.environ.get("BD_CLI") or shutil.which("bd"))):
        print(f"{name:16} {path or 'MISSING'}")
        ok &= bool(path)
    if not ok:
        print("\ntrace_processor comes with the perfetto plugin skill (or set TRACE_PROCESSOR); "
              "llvm-symbolizer from `brew install llvm` or the NDK (or set LLVM_SYMBOLIZER).")
    return ok


if __name__ == "__main__":
    if "--check" in sys.argv:
        sys.exit(0 if check() else 1)
    print(__doc__)
