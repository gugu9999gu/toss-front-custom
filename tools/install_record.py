"""Install the record UI and web companion on an explicit Android 13/16 Front 2."""
from pathlib import Path
import argparse
import hashlib
import json
import os
import shlex
from start_audio import adb_run
from device import Device

ROOT = Path(__file__).resolve().parents[1]
PACKAGE = "dev.tossfront.record"


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--serial", required=True)
    parser.add_argument("--adb", type=Path, default=Path(os.environ.get("ANDROID_HOME", Path.home() / "AppData/Local/Android/Sdk")) / "platform-tools/adb.exe")
    parser.add_argument("--update-web", action="store_true", help="Update the locally signed YouTube web app; its original signing key is required")
    args = parser.parse_args()
    adb, serial = args.adb, args.serial
    info = Device(serial, adb).inspect()
    if info["sdk"] not in ("33", "36"):
        raise SystemExit("Select the intended Toss Front 2 on Android 13 or 16")
    legacy_root = info["sdk"] == "33" and "uid=0(root)" in adb_run(adb, serial, "shell", "id")
    paths = [ROOT / "dist/FrontRecord-1.9.8.apk"]
    if args.update_web: paths.append(ROOT / "dist/YouTube-Web-2.3.apk")
    if not all(path.is_file() for path in paths): raise SystemExit("Build requested APKs before installation")
    verified = []
    for path in paths:
        build_name, package = ("record", PACKAGE) if path.name.startswith("FrontRecord-") else ("youtubeweb", "local.tossfront.youtubeweb")
        report = json.loads((ROOT / "build" / build_name / "verification.json").read_text(encoding="utf-8"))
        if report["package"] != package or hashlib.sha256(path.read_bytes()).hexdigest() != report["sha256"]:
            raise RuntimeError("Build verification does not match " + path.name)
        verified.append((path, package, report["sha256"]))
    for path, package, expected in verified:
        result = adb_run(adb, serial, "install", "-r", str(path))
        if "Success" not in result: raise RuntimeError("APK installation did not report success")
        installed = adb_run(adb, serial, "shell", "pm path " + package)
        if not installed.startswith("package:") or "\n" in installed:
            raise RuntimeError("Cannot verify installed APK path")
        actual = adb_run(adb, serial, "shell", "sha256sum " + shlex.quote(installed[8:])).split()[0]
        if actual != expected: raise RuntimeError("Installed APK hash mismatch")
        print("Installed and SHA-256 verified " + path.name)
    # Add only this app's listener and overlay access; other listeners/app operations stay intact.
    adb_run(adb, serial, "shell", "cmd notification allow_listener " + PACKAGE + "/.SessionListener 0")
    adb_run(adb, serial, "shell", "cmd appops set " + PACKAGE + " SYSTEM_ALERT_WINDOW allow")
    adb_run(adb, serial, "shell", "cmd appops set " + PACKAGE + " SCHEDULE_EXACT_ALARM allow")
    adb_run(adb, serial, "shell", "pm grant " + PACKAGE + " android.permission.POST_NOTIFICATIONS")
    adb_run(adb, serial, "shell", "pm grant " + PACKAGE + " android.permission.RECORD_AUDIO")
    listener = adb_run(adb, serial, "shell", "settings get secure enabled_notification_listeners")
    if PACKAGE not in listener: raise RuntimeError("Media listener access was not enabled")
    if "allow" not in adb_run(adb, serial, "shell", "cmd appops get " + PACKAGE + " SYSTEM_ALERT_WINDOW"):
        raise RuntimeError("Floating UI access was not enabled")
    # Add a shortcut only into an unoccupied visible cell, using Launcher3's own provider.
    uri = "content://com.android.launcher3.settings/favorites"
    favorites = adb_run(adb, serial, "shell", "content query --uri " + uri + " --projection intent:container:screen:cellX:cellY") if legacy_root else PACKAGE
    if PACKAGE not in favorites:
        import re
        occupied = set()
        for line in favorites.splitlines():
            if "container=-100" in line and "screen=0" in line:
                x, y = re.search(r"cellX=(\d+)", line), re.search(r"cellY=(\d+)", line)
                if x and y: occupied.add((int(x[1]), int(y[1])))
        cell = next(((x,y) for y in (2,3,0) for x in range(4) if (x,y) not in occupied), None)
        if cell:
            intent = "#Intent;action=android.intent.action.MAIN;category=android.intent.category.LAUNCHER;launchFlags=0x10200000;component=" + PACKAGE + "/.MainActivity;end"
            command = ["content", "insert", "--uri", uri]
            for name, kind, value in [("title","s","레코드 플레이어"),("intent","s",intent),("container","i",-100),("screen","i",0),("cellX","i",cell[0]),("cellY","i",cell[1]),("spanX","i",1),("spanY","i",1),("itemType","i",0),("profileId","l",0),("rank","i",0)]:
                command += ["--bind", f"{name}:{kind}:{value}"]
            adb_run(adb, serial, "shell", shlex.join(command))
    adb_run(adb, serial, "shell", "am start -W -n " + PACKAGE + "/.MainActivity")
    print("FrontRecord installed; media access and optional floating UI enabled. No root bridge required after reboot.")


if __name__ == "__main__": main()
