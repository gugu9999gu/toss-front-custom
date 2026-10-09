"""Install/start FrontAudio on one explicitly selected, root-ADB Toss Front 2.

No partition writes, boot hooks, network ADB changes, or signature-permission grants.
The on-device bridge lives until reboot; rerun this command after a reboot.
"""
from pathlib import Path
import argparse
import json
import os
import secrets
import shlex
import socket
import subprocess
import time

ROOT = Path(__file__).resolve().parents[1]
PACKAGE = "dev.tossfront.audio"
COMPONENT = PACKAGE + "/.MainActivity"
CONFIG = "/data/user/0/" + PACKAGE + "/files/bridge.json"
PID = "/data/local/tmp/frontaudio-bridge.pid"
LOG = "/data/local/tmp/frontaudio-bridge.log"


def adb_run(adb, serial, *args, input=None, check=True):
    result = subprocess.run([str(adb), "-s", serial, *args], input=input, capture_output=True, timeout=30)
    if check and result.returncode:
        raise RuntimeError((result.stdout + result.stderr).decode("utf-8", errors="replace"))
    return result.stdout.decode("utf-8", errors="replace").strip()


def bridge_request(adb, serial, config, action="status", **extra):
    forward = adb_run(adb, serial, "forward", "tcp:0", "tcp:" + str(config["port"]))
    try:
        with socket.create_connection(("127.0.0.1", int(forward)), timeout=3) as connection:
            payload = dict(token=config["token"], action=action, **extra)
            connection.sendall((json.dumps(payload) + "\n").encode())
            connection.settimeout(4)
            data = b""
            while not data.endswith(b"\n"):
                chunk = connection.recv(4096)
                if not chunk or len(data) > 65536:
                    raise RuntimeError("Bridge reply missing or too large")
                data += chunk
            return json.loads(data)
    finally:
        adb_run(adb, serial, "forward", "--remove", "tcp:" + forward, check=False)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--serial", required=True, help="Explicit first-device ADB serial")
    parser.add_argument("--adb", type=Path, default=Path(os.environ.get("ANDROID_HOME", Path.home() / "AppData/Local/Android/Sdk")) / "platform-tools/adb.exe")
    parser.add_argument("--install", action="store_true", help="Install locally built FrontAudio APK first")
    args = parser.parse_args()
    adb, serial = args.adb, args.serial
    if adb_run(adb, serial, "shell", "getprop ro.product.model") != "toss_front2":
        raise SystemExit("Refusing: selected device is not toss_front2")
    if adb_run(adb, serial, "shell", "getprop ro.build.version.sdk") != "33":
        raise SystemExit("This bridge was verified on Android 13 / API 33 only; no changes made")
    if "uid=0(root)" not in adb_run(adb, serial, "shell", "id"):
        raise SystemExit("Root ADB is required; no changes made")
    if args.install:
        apk = ROOT / "dist/FrontAudio-1.0.0.apk"
        if not apk.is_file(): raise SystemExit("Build first: python tools/build_android.py --app audio")
        print(adb_run(adb, serial, "install", "-r", str(apk)))
    apk_path = adb_run(adb, serial, "shell", "pm path " + PACKAGE)
    if not apk_path.startswith("package:") or "\n" in apk_path:
        raise SystemExit("FrontAudio is not installed as a single APK")
    apk_path = apk_path.removeprefix("package:")
    if not apk_path.startswith("/data/app/") or not apk_path.endswith("/base.apk"):
        raise SystemExit("Unexpected installed APK path")
    adb_run(adb, serial, "shell", "am start -W -n " + COMPONENT)
    uid = adb_run(adb, serial, "shell", "stat -c %u /data/user/0/" + PACKAGE)
    if not uid.isdigit() or int(uid) < 10000: raise SystemExit("Unexpected app UID")
    # Stop only our exact root helper. Never kill an arbitrary PID from a stale file.
    old_pid = adb_run(adb, serial, "shell", "cat " + PID, check=False)
    if old_pid.isdigit():
        cmdline = adb_run(adb, serial, "shell", "cat /proc/" + old_pid + "/cmdline", check=False)
        owner = adb_run(adb, serial, "shell", "stat -c %u /proc/" + old_pid, check=False)
        if "dev.tossfront.audio.AudioBridge" in cmdline and owner == "0":
            adb_run(adb, serial, "shell", "kill " + old_pid)
    config = {"port": 39261, "token": secrets.token_hex(32)}
    directory = str(Path(CONFIG).parent).replace("\\", "/")
    setup = "mkdir -p " + shlex.quote(directory) + " && chmod 700 " + shlex.quote(directory) + " && chown " + uid + ":" + uid + " " + shlex.quote(directory)
    adb_run(adb, serial, "shell", setup)
    # Secret sent via stdin into the app's private directory, never printed or put in arguments.
    adb_run(adb, serial, "shell", "umask 077; cat > " + shlex.quote(CONFIG), input=json.dumps(config).encode())
    adb_run(adb, serial, "shell", "chown " + uid + ":" + uid + " " + shlex.quote(CONFIG) + " && chmod 600 " + shlex.quote(CONFIG) + " && restorecon -R " + shlex.quote(directory))
    launch = "CLASSPATH=" + shlex.quote(apk_path) + " /system/bin/app_process /system/bin dev.tossfront.audio.AudioBridge " + shlex.quote(CONFIG)
    detached = "nohup " + launch.replace("CLASSPATH=", "env CLASSPATH=", 1) + " > " + LOG + " 2>&1 < /dev/null & echo $! > " + PID
    adb_run(adb, serial, "shell", detached)
    reply = None
    for _ in range(8):
        time.sleep(.5)
        try:
            reply = bridge_request(adb, serial, config)
            if reply.get("ok"): break
        except (OSError, RuntimeError, ValueError): pass
    if not reply or not reply.get("ok"):
        # This log contains no credential or hardware addresses at startup.
        raise SystemExit("Bridge did not start: " + adb_run(adb, serial, "shell", "tail -n 12 " + LOG))
    adb_run(adb, serial, "shell", "am force-stop " + PACKAGE)
    adb_run(adb, serial, "shell", "am start -W -n " + COMPONENT)
    print(json.dumps({"ok": True, "package": PACKAGE, "outputTypes": [d["type"] for d in reply["devices"]],
                      "activeTypes": [d["type"] for d in reply["active"]], "mediaStrategy": reply["strategy"],
                      "survivesPcDisconnect": True, "restartAfterDeviceReboot": True}, ensure_ascii=False))


if __name__ == "__main__": main()
