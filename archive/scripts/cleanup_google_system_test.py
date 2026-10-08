from pathlib import Path
import datetime as dt
import json
import subprocess
import sys
import time

sys.stdout.reconfigure(encoding="utf-8")
root = Path(__file__).resolve().parent.parent
out = root / "outputs/toss-front2-prepared"
adb = str(Path.home() / "AppData/Local/Android/Sdk/platform-tools/adb.exe")
serial = sys.argv[1]
prefix = [adb, "-s", serial]
report = {"started_at": dt.datetime.now().astimezone().isoformat(), "actions": [],
          "reason": "Official YouTube playback remained unstable; RAM overlay is not a permanent Google integration.",
          "google_packages_disabled": ["com.google.android.gsf", "com.google.android.gms", "com.android.vending"]}
resume = "--resume" in sys.argv[2:]
if resume:
    report = json.loads((out / "google-system-test-cleanup.json").read_text(encoding="utf-8"))

def run(*args, timeout=20, check=True):
    p = subprocess.run([*prefix, *args], capture_output=True, text=True,
                       encoding="utf-8", errors="replace", timeout=timeout)
    r = {"args": list(args), "code": p.returncode, "out": p.stdout.strip(), "err": p.stderr.strip()}
    report["actions"].append(r)
    if check:
        assert p.returncode == 0, r
    return r

assert run("shell", "getprop", "ro.serialno")["out"] == "FIRST_DEVICE_SERIAL_REDACTED"
if not resume:
    before = run("shell", "cat", "/proc/sys/kernel/random/boot_id")["out"]
    report["before_boot_id"] = before
    for pkg in ["com.google.android.youtube", *report["google_packages_disabled"]]:
        run("shell", "am", "force-stop", pkg)
    for pkg in report["google_packages_disabled"]:
        assert "disabled-user" in run("shell", "pm", "disable-user", "--user", "0", pkg)["out"]
    run("shell", "input", "keyevent", "3")
    run("reboot")
    (out / "google-system-test-cleanup.json").write_text(json.dumps(report, ensure_ascii=False, indent=2), encoding="utf-8")
    time.sleep(6)
else:
    before = report["before_boot_id"]
deadline = time.monotonic() + 120
while time.monotonic() < deadline:
    try:
        subprocess.run([adb, "connect", serial], capture_output=True, timeout=8)
    except subprocess.TimeoutExpired:
        time.sleep(4)
        continue
    r = run("shell", "getprop", "sys.boot_completed", check=False)
    if r["code"] == 0 and r["out"] == "1":
        break
    time.sleep(4)
else:
    raise RuntimeError("Reboot reconnect timeout; disabled state saved before reboot")
assert run("shell", "getprop", "ro.serialno")["out"] == "FIRST_DEVICE_SERIAL_REDACTED"
after = run("shell", "cat", "/proc/sys/kernel/random/boot_id")["out"]
assert after != before
report["after_boot_id"] = after
mounts = run("shell", "cat", "/proc/mounts")["out"]
assert "front2-google-system-test" not in mounts
report["temporary_mounts_removed"] = True
disabled = run("shell", "pm", "list", "packages", "-d", "--user", "0")["out"].splitlines()
assert all("package:" + pkg in disabled for pkg in report["google_packages_disabled"])
report["disabled_state_persisted"] = True
run("shell", "pm", "revoke", "com.google.android.gms", "android.permission.INTERACT_ACROSS_USERS", check=False)
run("shell", "find", "/data/local/tmp/front2-google-system-test", "-maxdepth", "4", "-type", "f")
run("shell", "rm", "-rf", "/data/local/tmp/front2-google-system-test")
run("shell", "test", "!", "-e", "/data/local/tmp/front2-google-system-test")
report["staged_test_files_removed"] = True
for pkg in report["google_packages_disabled"]:
    r = run("shell", "dumpsys", "package", pkg)
    report.setdefault("final_package_flags", {})[pkg] = [line.strip() for line in r["out"].splitlines() if "pkgFlags=" in line or "privateFlags=" in line]
report["complete"] = True
report["completed_at"] = dt.datetime.now().astimezone().isoformat()
(out / "google-system-test-cleanup.json").write_text(json.dumps(report, ensure_ascii=False, indent=2), encoding="utf-8")
print(json.dumps({k: report[k] for k in ["after_boot_id", "temporary_mounts_removed", "disabled_state_persisted", "staged_test_files_removed", "final_package_flags", "complete"]}, ensure_ascii=False))
