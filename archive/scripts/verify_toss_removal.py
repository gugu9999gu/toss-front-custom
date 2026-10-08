from pathlib import Path
import concurrent.futures as cf
import datetime as dt
import json
import subprocess
import sys

sys.stdout.reconfigure(encoding="utf-8")
assert len(sys.argv) >= 3 and sys.argv[1] == "--serial"
phase = sys.argv[3] if len(sys.argv) > 3 else "after-change"
root = Path(__file__).resolve().parent.parent
out = root / "outputs/toss-front2-prepared"
adb = str(Path.home() / "AppData/Local/Android/Sdk/platform-tools/adb.exe")
prefix = [adb, "-s", sys.argv[2]]
queries = {
    "identity": ["id"], "model": ["getprop", "ro.product.model"], "boot": ["getprop", "sys.boot_completed"],
    "packages": ["pm", "list", "packages", "--user", "0"], "processes": ["ps", "-A", "-o", "NAME"],
    "home": ["cmd", "package", "resolve-activity", "--brief", "-a", "android.intent.action.MAIN", "-c", "android.intent.category.HOME"],
    "watermark": ["settings", "get", "global", "hide_toss_wartermark"],
    "minicat": ["settings", "get", "system", "minicat_launcher_enabled"],
    "windows": ["dumpsys", "window", "windows"], "bluetooth": ["dumpsys", "bluetooth_manager"],
    "connectivity": ["dumpsys", "connectivity"], "systemui_pid": ["pidof", "com.android.systemui"],
    "disabled_packages": ["pm", "list", "packages", "-d", "--user", "0"],
    "adb_notify": ["getprop", "persist.adb.notify"],
    "boot_id": ["cat", "/proc/sys/kernel/random/boot_id"], "uptime": ["cat", "/proc/uptime"],
}
def query(args):
    p = subprocess.run([*prefix, "shell", *args], capture_output=True, text=True,
                       encoding="utf-8", errors="replace", timeout=20)
    return {"code": p.returncode, "out": p.stdout.strip(), "err": p.stderr.strip()}
report = {"time": dt.datetime.now().astimezone().isoformat(), "phase": phase, "queries": {}}
with cf.ThreadPoolExecutor(max_workers=4) as pool:
    jobs = {pool.submit(query, args): key for key, args in queries.items()}
    for job in cf.as_completed(jobs):
        report["queries"][jobs[job]] = job.result()
q = report["queries"]
assert all(x["code"] == 0 for x in q.values())
removal = json.loads((out / "toss-removal-result.json").read_text(encoding="utf-8"))
removed = set(removal["removed_packages"])
utilities = set(removal["disabled_vendor_utilities"])
selected = removed | utilities
installed = {x.removeprefix("package:") for x in q["packages"]["out"].splitlines()}
disabled = {x.removeprefix("package:") for x in q["disabled_packages"]["out"].splitlines()}
processes = set(q["processes"]["out"].splitlines())
report["checks"] = {
    "target_model": q["model"]["out"] == "toss_front2", "boot_completed": q["boot"]["out"] == "1",
    "removed_packages_still_absent": not installed.intersection(removed),
    "vendor_utilities_disabled": utilities.issubset(disabled),
    "no_selected_app_process": not any(x == pkg or x.startswith(pkg + ":") for x in processes for pkg in selected),
    "stock_home": "com.android.launcher3/.uioverrides.QuickstepLauncher" in q["home"]["out"],
    "watermark_disabled": q["watermark"]["out"] == "1", "minicat_disabled": q["minicat"]["out"] == "0",
    "systemui_running": q["systemui_pid"]["out"].isdigit(),
    "usb_adb_notification_hidden": q["adb_notify"]["out"] == "0",
    "bluetooth_on": "state: ON" in q["bluetooth"]["out"],
    "a2dp_running": "Profile: A2dpService" in q["bluetooth"]["out"],
    "internet_validated": "VALIDATED" in q["connectivity"]["out"],
}
screen = subprocess.run([*prefix, "exec-out", "screencap", "-p"], capture_output=True, timeout=20)
assert screen.returncode == 0 and screen.stdout.startswith(b"\x89PNG\r\n\x1a\n")
screen_path = out / ("toss-removal-" + phase + ".png")
screen_path.write_bytes(screen.stdout)
report["screen"] = str(screen_path)
(out / ("toss-removal-verification-" + phase + ".json")).write_text(json.dumps(report, ensure_ascii=False, indent=2), encoding="utf-8")
print(json.dumps({"checks": report["checks"], "screen": str(screen_path)}, ensure_ascii=False))
