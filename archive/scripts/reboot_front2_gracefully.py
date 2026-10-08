from pathlib import Path
import datetime as dt
import json
import subprocess
import sys
import time

adb = str(Path.home() / "AppData/Local/Android/Sdk/platform-tools/adb.exe")
serial = sys.argv[1]
prefix = [adb, "-s", serial]
root = Path(__file__).resolve().parent.parent
path = root / "outputs/toss-front2-prepared/final-device-reboot.json"
def shell(*args, timeout=15):
    return subprocess.run([*prefix, "shell", *args], capture_output=True, text=True,
                          encoding="utf-8", errors="replace", timeout=timeout)
assert shell("getprop", "ro.serialno").stdout.strip() == "FIRST_DEVICE_SERIAL_REDACTED"
before = shell("cat", "/proc/sys/kernel/random/boot_id").stdout.strip()
report = {"started_at": dt.datetime.now().astimezone().isoformat(), "before_boot_id": before, "method": "svc power reboot (graceful Android shutdown)"}
path.write_text(json.dumps(report, indent=2), encoding="utf-8")
try:
    p = shell("svc", "power", "reboot", timeout=10)
    report["request"] = {"code": p.returncode, "out": p.stdout.strip(), "err": p.stderr.strip()}
except subprocess.TimeoutExpired:
    report["request"] = {"timeout": True}
time.sleep(6)
deadline = time.monotonic() + 120
while time.monotonic() < deadline:
    try:
        subprocess.run([adb, "connect", serial], capture_output=True, timeout=8)
        if shell("getprop", "sys.boot_completed").stdout.strip() == "1":
            after = shell("cat", "/proc/sys/kernel/random/boot_id").stdout.strip()
            if after and after != before:
                break
    except subprocess.TimeoutExpired:
        pass
    time.sleep(4)
else:
    raise RuntimeError("Graceful reboot reconnect timeout")
assert shell("getprop", "ro.serialno").stdout.strip() == "FIRST_DEVICE_SERIAL_REDACTED"
report["after_boot_id"] = after
report["watermark"] = shell("settings", "get", "global", "hide_toss_wartermark").stdout.strip()
report["disabled_packages"] = shell("pm", "list", "packages", "-d", "--user", "0").stdout.strip().splitlines()
report["google_disable_persisted"] = all("package:" + p in report["disabled_packages"] for p in ["com.google.android.gms", "com.google.android.gsf", "com.android.vending"])
report["temporary_mounts_removed"] = "front2-google-system-test" not in shell("cat", "/proc/mounts").stdout
report["completed_at"] = dt.datetime.now().astimezone().isoformat()
path.write_text(json.dumps(report, indent=2), encoding="utf-8")
print(json.dumps(report))
assert report["watermark"] == "1" and report["google_disable_persisted"] and report["temporary_mounts_removed"]
