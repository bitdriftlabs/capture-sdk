#!/usr/bin/env python3
"""List connected physical iPhones or resolve one CoreDevice ID for Maestro."""

from __future__ import annotations

import json
import subprocess
import sys
import tempfile
from pathlib import Path
from typing import Any


def connected_physical_devices() -> list[dict[str, Any]]:
    """Return connected physical devices reported by Xcode CoreDevice."""
    with tempfile.TemporaryDirectory(prefix="capture-ios-devices-") as temp_dir:
        devices_path = Path(temp_dir) / "devices.json"
        subprocess.run(
            ["xcrun", "devicectl", "--json-output", str(devices_path), "list", "devices"],
            check=True,
            stdout=subprocess.DEVNULL,
        )
        device_data = json.loads(devices_path.read_text())

    devices = device_data.get("result", {}).get("devices", [])
    if not isinstance(devices, list):
        raise ValueError("Unexpected `xcrun devicectl list devices` JSON response.")

    connected_devices = []
    for device in devices:
        if not isinstance(device, dict):
            continue

        hardware_properties = device.get("hardwareProperties", {})
        connection_properties = device.get("connectionProperties", {})
        if not isinstance(hardware_properties, dict) or not isinstance(connection_properties, dict):
            continue

        if hardware_properties.get("reality") == "physical" and connection_properties.get("tunnelState") == "connected":
            connected_devices.append(device)

    return connected_devices


def device_name(device: dict[str, Any]) -> str:
    """Return the human-readable device name from CoreDevice JSON."""
    properties = device.get("deviceProperties", {})
    if isinstance(properties, dict) and isinstance(properties.get("name"), str):
        return properties["name"]

    name = device.get("name")
    return name if isinstance(name, str) else "Unknown iPhone"


def hardware_udid(device: dict[str, Any]) -> str | None:
    """Return a device's hardware UDID, when reported by CoreDevice."""
    properties = device.get("hardwareProperties", {})
    udid = properties.get("udid") if isinstance(properties, dict) else None
    return udid if isinstance(udid, str) else None


def list_devices(devices: list[dict[str, Any]]) -> int:
    """Print the stable CoreDevice IDs that developers can configure locally."""
    if not devices:
        print("No connected physical iPhones found.", file=sys.stderr)
        return 1

    for device in devices:
        identifier = device.get("identifier")
        if isinstance(identifier, str):
            print(f"{device_name(device)}\t{identifier}")
    return 0


def main() -> int:
    if len(sys.argv) > 2:
        print(f"usage: {Path(sys.argv[0]).name} [coredevice-id]", file=sys.stderr)
        return 2

    try:
        devices = connected_physical_devices()
    except (OSError, subprocess.CalledProcessError, ValueError, json.JSONDecodeError) as error:
        print(f"Unable to list connected physical iPhones: {error}", file=sys.stderr)
        return 1

    if len(sys.argv) == 1:
        return list_devices(devices)

    requested_identifier = sys.argv[1]
    for device in devices:
        if device.get("identifier") == requested_identifier:
            udid = hardware_udid(device)
            if udid is not None:
                print(udid)
                return 0
            break

    print(f"No connected physical iPhone found with CoreDevice ID {requested_identifier}.", file=sys.stderr)
    return 1


if __name__ == "__main__":
    raise SystemExit(main())
