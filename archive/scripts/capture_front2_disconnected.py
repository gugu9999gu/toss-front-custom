from pathlib import Path
import datetime as dt
import json
import subprocess
import sys
import time

root = Path(__file__).resolve().parent.parent
out = root / "outputs/toss-front2-prepared"
adb = str(Path.home() / "AppData/Local/Android/Sdk/platform-tools/adb.exe")
serial = sys.argv[1]
prefix = [adb, "-s", serial]
def run(*args, timeout=20):
    p = subprocess.run([*prefix, *args], capture_output=True, text=True,
                       encoding="utf-8", errors="replace", timeout=timeout)
    assert p.returncode == 0, p.stderr
    return p.stdout.strip()
assert run("shell", "getprop", "ro.serialno") == "FIRST_DEVICE_SERIAL_REDACTED"
script = root / "work/front2-final-disconnected.sh"
script.write_text("""#!/system/bin/sh
sleep 8
date > /data/local/tmp/front2-final-disconnected-state.txt
cat /proc/sys/kernel/random/boot_id >> /data/local/tmp/front2-final-disconnected-state.txt
settings get global hide_toss_wartermark >> /data/local/tmp/front2-final-disconnected-state.txt
settings get system minicat_launcher_enabled >> /data/local/tmp/front2-final-disconnected-state.txt
screencap -p /data/local/tmp/front2-final-disconnected.png
dumpsys notification > /data/local/tmp/front2-final-disconnected-notifications.txt
""", encoding="utf-8", newline="\n")
run("push", str(script), "/data/local/tmp/front2-final-disconnected.sh")
run("shell", "input", "keyevent", "3")
run("shell", "nohup sh /data/local/tmp/front2-final-disconnected.sh >/data/local/tmp/front2-final-disconnected.log 2>&1 </dev/null &")
p = subprocess.run([adb, "disconnect", serial], capture_output=True, text=True, timeout=10)
assert p.returncode == 0
report = {"disconnected_at": dt.datetime.now().astimezone().isoformat(), "disconnect_result": p.stdout.strip()}
time.sleep(12)
subprocess.run([adb, "connect", serial], capture_output=True, timeout=20, check=True)
assert run("shell", "getprop", "ro.serialno") == "FIRST_DEVICE_SERIAL_REDACTED"
screen = out / "front2-final-no-debug.png"
run("pull", "/data/local/tmp/front2-final-disconnected.png", str(screen))
assert screen.read_bytes().startswith(b"\x89PNG\r\n\x1a\n")
report["device_capture_state"] = run("shell", "cat", "/data/local/tmp/front2-final-disconnected-state.txt").splitlines()
notifications = run("shell", "cat", "/data/local/tmp/front2-final-disconnected-notifications.txt")
report["active_debug_notification_records"] = [line.strip() for line in notifications.splitlines() if "NotificationRecord(" in line and "pkg=android" in line and " id=62 " in line]
report["screen"] = str(screen)
report["wireless_debug_notification_absent"] = not report["active_debug_notification_records"]
report["installed_youtube"] = run("shell", "pm", "list", "packages", "--user", "0", "com.google.android.youtube") == "package:com.google.android.youtube"
report["google_processes"] = {pkg: run("shell", "ps", "-A", "-o", "NAME").splitlines().count(pkg) for pkg in ["com.google.android.gsf", "com.google.android.gms", "com.android.vending"]}
subprocess.run([adb, "disconnect", serial], capture_output=True, timeout=10, check=True)
report["final_disconnected_at"] = dt.datetime.now().astimezone().isoformat()
(out / "final-disconnected-verification.json").write_text(json.dumps(report, ensure_ascii=False, indent=2), encoding="utf-8")
print(json.dumps(report, ensure_ascii=False))
assert report["wireless_debug_notification_absent"]
