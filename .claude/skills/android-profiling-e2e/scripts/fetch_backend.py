#!/usr/bin/env python3
"""Fetch a workflow-command attachment (raw, processed, default) and its processing status.

Auth: the bd CLI's own browser-session JWT from ~/.bd/tokens.yaml (refreshed by running a cheap bd
command first), sent the way bd does: `Authorization: Bearer <jwt>`. The token is never printed.
The API host is derived from ~/.bd/profile.yaml when possible, else --base.

    fetch_backend.py <artifact_id> --out <dir> [--wait 600] [--base https://api-cli.bitdrift.dev]
"""
import argparse
import json
import pathlib
import re
import subprocess
import sys
import time
import urllib.error
import urllib.request


def default_base():
    try:
        prof = pathlib.Path.home().joinpath(".bd/profile.yaml").read_text()
        m = re.search(r"https://api-cli\.[a-z0-9.-]+", prof)
        if m:
            return m.group(0)
        m = re.search(r"https://api\.([a-z0-9.-]+)", prof)
        if m:
            return f"https://api-cli.{m.group(1)}"
    except OSError:
        pass
    return "https://api-cli.bitdrift.dev"


def jwt():
    subprocess.run(["bd", "app", "list", "-ojson"], capture_output=True)  # refreshes the session if needed
    text = pathlib.Path.home().joinpath(".bd/tokens.yaml").read_text()
    m = re.search(r"session_jwt:\s*\n\s*value:\s*(\S+)", text)
    if not m:
        sys.exit("no session jwt in ~/.bd/tokens.yaml; run `bd auth` (browser login) first")
    return m.group(1).strip("'\"")


def get(url, token):
    req = urllib.request.Request(url, headers={"Authorization": f"Bearer {token}",
                                               "User-Agent": "android-profiling-e2e", "Accept-Encoding": "zstd"})
    try:
        with urllib.request.urlopen(req, timeout=180) as r:
            return r.status, {k.lower(): v for k, v in r.headers.items()}, r.read()
    except urllib.error.HTTPError as e:
        return e.code, {k.lower(): v for k, v in e.headers.items()}, e.read()


def decode(headers, body):
    if headers.get("content-encoding", "").lower() == "zstd":
        return subprocess.run(["zstd", "-d", "-c"], input=body, capture_output=True, check=True).stdout
    return body


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("artifact_id")
    ap.add_argument("--out", required=True)
    ap.add_argument("--wait", type=int, default=600, help="seconds to wait for processing")
    ap.add_argument("--base", default=default_base())
    a = ap.parse_args()
    out = pathlib.Path(a.out)
    out.mkdir(parents=True, exist_ok=True)
    token = jwt()
    url = f"{a.base}/v1/workflow-commands/{a.artifact_id}"

    deadline = time.time() + a.wait
    while True:
        code, h, body = get(url + "/status", token)
        status = {"http": code}
        try:
            status.update(json.loads(decode(h, body)))
        except Exception:
            status["body"] = body[:300].decode(errors="replace")
        if status.get("processing_state") not in ("pending", "processing") or time.time() > deadline:
            break
        time.sleep(5)
    (out / "backend_status.json").write_text(json.dumps(status, indent=2))

    result = {"artifact_id": a.artifact_id, "base": a.base, "status": status, "downloads": {}}
    for variant in ("raw", "processed", "default"):
        code, h, body = get(url + ("" if variant == "default" else f"?variant={variant}"), token)
        entry = {"http": code, "content_type": h.get("content-type"),
                 "disposition": h.get("x-bitdrift-attachment-disposition"),
                 "content_encoding": h.get("content-encoding")}
        if code == 200:
            data = decode(h, body)
            name = f"backend_{variant}" + (".tar" if entry["disposition"] == "processed" else ".perfetto-trace")
            (out / name).write_bytes(data)
            entry.update(file=str(out / name), bytes=len(data))
        else:
            entry["body"] = decode(h, body)[:500].decode(errors="replace")
        result["downloads"][variant] = entry
    (out / "backend.json").write_text(json.dumps(result, indent=2))
    print(json.dumps({"processing_state": status.get("processing_state"), "partial": status.get("partial"),
                      "downloads": {k: (v.get("http"), v.get("bytes")) for k, v in result["downloads"].items()}}))


if __name__ == "__main__":
    main()
