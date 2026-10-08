from pathlib import Path
import datetime as dt
import json
import socket
import subprocess
import sys
import time

sys.stdout.reconfigure(encoding="utf-8")
adb = str(Path.home() / "AppData/Local/Android/Sdk/platform-tools/adb.exe")
start = time.monotonic()
previous_boot_id = sys.argv[1] if len(sys.argv) > 1 else None
print("Waiting for this Toss Front 2's wireless ADB service", flush=True)
while time.monotonic() - start < 180:
    mdns = subprocess.run([adb, "mdns", "services"], capture_output=True, text=True, timeout=5)
    targets = []
    for line in mdns.stdout.splitlines():
        columns = line.split()
        if len(columns) == 3 and columns[0].startswith("adb-FIRST_DEVICE_SERIAL_REDACTED-") and columns[1] == "_adb-tls-connect._tcp":
            targets.append(columns[2])
    for target in targets:
        host, port = target.rsplit(":", 1)
        if host != "DEVICE_ADDRESS_REDACTED" or not port.isdigit():
            continue
        with socket.socket(socket.AF_INET, socket.SOCK_STREAM) as sock:
            sock.settimeout(1)
            if sock.connect_ex((host, int(port))) != 0:
                continue
        connection = subprocess.run([adb, "connect", target], capture_output=True, text=True, timeout=5)
        q = subprocess.run([adb, "-s", target, "shell", "getprop", "ro.serialno"], capture_output=True, text=True, timeout=5)
        if q.returncode == 0 and q.stdout.strip() == "FIRST_DEVICE_SERIAL_REDACTED":
            boot = subprocess.run([adb, "-s", target, "shell", "getprop", "sys.boot_completed"], capture_output=True, text=True, timeout=5)
            if boot.stdout.strip() != "1":
                continue
            boot_id = subprocess.run([adb, "-s", target, "shell", "cat", "/proc/sys/kernel/random/boot_id"], capture_output=True, text=True, timeout=5)
            if boot_id.returncode != 0 or not boot_id.stdout.strip() or boot_id.stdout.strip() == previous_boot_id:
                continue
            record = {"time": dt.datetime.now().astimezone().isoformat(), "target": target, "boot_completed": True}
            record["boot_id"] = boot_id.stdout.strip()
            (Path(__file__).resolve().parent / "removal-network-current.json").write_text(json.dumps(record, indent=2), encoding="utf-8")
            print(json.dumps(record), flush=True)
            sys.exit(0)
    time.sleep(1)
raise SystemExit("Wireless ADB reconnection timed out")
