"""Validate FrontAudio on an explicitly selected root-ADB device, restoring its route.

Switches media outputs briefly. Does not play audio or change any volume.
Writes only device types and pass/fail results, excluding tokens, serials and addresses.
"""
from pathlib import Path
import argparse
import json
import os
import time
from start_audio import adb_run, bridge_request, CONFIG


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--serial", required=True)
    parser.add_argument("--adb", type=Path, default=Path(os.environ.get("ANDROID_HOME", Path.home() / "AppData/Local/Android/Sdk")) / "platform-tools/adb.exe")
    parser.add_argument("--report", type=Path)
    args = parser.parse_args()
    if adb_run(args.adb, args.serial, "shell", "getprop ro.product.model") != "toss_front2":
        raise SystemExit("Selected device must be toss_front2")
    if adb_run(args.adb, args.serial, "shell", "getprop ro.build.version.sdk") != "33":
        raise SystemExit("Selected device must be Android 13 / API 33")
    config = json.loads(adb_run(args.adb, args.serial, "shell", "cat " + CONFIG))
    def request(action="status", **fields):
        result = bridge_request(args.adb, args.serial, config, action, **fields)
        assert result["ok"], "Bridge rejected expected operation"
        return result
    original = request()
    original_device = next((d for d in original["devices"] if any(d["type"] == a["type"] and d["address"] == a["address"] for a in original["active"])), None)
    if not original["automatic"] and original_device is None:
        raise SystemExit("Cannot safely restore original output; no test changes made")
    results = {"package": "dev.tossfront.audio", "availableTypes": [d["type"] for d in original["devices"]], "routes": []}
    try:
        for device in original["devices"]:
            request("select", id=device["id"])
            time.sleep(.4)
            state = request()
            assert not state["automatic"]
            assert any(a["type"] == device["type"] and a["address"] == device["address"] for a in state["active"]), "Actual media route differs from requested device"
            before_volume = state["volume"]
            request("select", id=device["id"])
            assert request()["volume"] == before_volume, "Selection changed current device volume"
            results["routes"].append({"type": device["type"], "actualRouteMatches": True, "volumeUnchanged": True})
        before = request()
        assert not bridge_request(args.adb, args.serial, dict(config, token="wrong"))["ok"]
        assert not bridge_request(args.adb, args.serial, config, "select", id=999999)["ok"]
        assert not bridge_request(args.adb, args.serial, config, "shell")["ok"]
        after = request()
        assert before["active"] == after["active"], "Rejected request changed routing"
        results["unauthenticatedRequestRejected"] = True
        results["missingDeviceRejected"] = True
        results["unknownActionRejected"] = True
        results["rejectionsPreserveRoute"] = True
        request("auto")
        assert request()["automatic"], "Automatic selection preference not cleared"
        results["automaticSelectionRestored"] = True
    finally:
        if original["automatic"]:
            request("auto")
        else:
            request("select", id=original_device["id"])
    results["originalPreferenceRestored"] = True
    if args.report:
        args.report.parent.mkdir(parents=True, exist_ok=True)
        args.report.write_text(json.dumps(results, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(json.dumps(results, ensure_ascii=False))


if __name__ == "__main__": main()
