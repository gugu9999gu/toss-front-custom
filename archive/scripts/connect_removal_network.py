from pathlib import Path
import datetime as dt
import json
import socket
import subprocess
import sys
import time

sys.stdout.reconfigure(encoding="utf-8")
adb = str(Path.home() / "AppData/Local/Android/Sdk/platform-tools/adb.exe")
target_ip = "DEVICE_ADDRESS_REDACTED"  # Address captured from this device's earlier connectivity dump.
target = target_ip + ":5555"
start = time.monotonic()
print(json.dumps({"event": "READY", "target": target, "duration_seconds": 240}), flush=True)
while time.monotonic() - start < 240:
    with socket.socket(socket.AF_INET, socket.SOCK_STREAM) as sock:
        sock.settimeout(1)
        available = sock.connect_ex((target_ip, 5555)) == 0
    if available:
        connection = subprocess.run([adb, "connect", target], capture_output=True, text=True, timeout=8)
        model = subprocess.run([adb, "-s", target, "shell", "getprop", "ro.product.model"], capture_output=True, text=True, timeout=6)
        if model.returncode == 0 and "toss_front2" in model.stdout.lower().replace(" ", "_"):
            identity = subprocess.run([adb, "-s", target, "shell", "id"], capture_output=True, text=True, timeout=6)
            report = {"time": dt.datetime.now().astimezone().isoformat(), "target": target,
                      "connection": connection.stdout.strip(), "model": model.stdout.strip(), "identity": identity.stdout.strip()}
            (Path(__file__).resolve().parent / "removal-network-connection.json").write_text(json.dumps(report, indent=2), encoding="utf-8")
            print(json.dumps({"event": "CONNECTED", **report}), flush=True)
            break
        print(json.dumps({"event": "UNVERIFIED", "model": model.stdout.strip(), "error": model.stderr.strip()}), flush=True)
        subprocess.run([adb, "disconnect", target], capture_output=True)
        break
    time.sleep(1)
else:
    print(json.dumps({"event": "TIMEOUT"}), flush=True)
