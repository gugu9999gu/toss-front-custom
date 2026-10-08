from pathlib import Path
from datetime import datetime
import json
import re
import subprocess
import sys
import concurrent.futures as cf

sys.stdout.reconfigure(encoding="utf-8")
root = Path(__file__).resolve().parent.parent
adb = str(Path.home() / "AppData/Local/Android/Sdk/platform-tools/adb.exe")
prefix = [adb]
if len(sys.argv) == 3 and sys.argv[1] == "--serial":
    prefix += ["-s", sys.argv[2]]
commands = {
    "packages": ["pm", "list", "packages", "-f", "-U"],
    "processes": ["ps", "-A", "-o", "NAME"],
    "properties": ["getprop"],
    "window": ["dumpsys", "window", "windows"],
    "services": ["service", "list"],
    "device_policy": ["dumpsys", "device_policy"],
    "home": ["cmd", "package", "resolve-activity", "--brief", "-a", "android.intent.action.MAIN", "-c", "android.intent.category.HOME"],
    "systemui": ["pm", "path", "com.android.systemui"],
    "mounts": ["cat", "/proc/mounts"],
    "settings_system": ["settings", "list", "system"],
    "settings_secure": ["settings", "list", "secure"],
    "settings_global": ["settings", "list", "global"],
    "overlays": ["cmd", "overlay", "list"],
}
report = {"checked_at": datetime.now().astimezone().isoformat()}
def query(args):
    result = subprocess.run([*prefix, "shell", *args], capture_output=True, text=True,
                            encoding="utf-8", errors="replace", timeout=30)
    return {"exit_code": result.returncode, "stdout": result.stdout, "stderr": result.stderr}
with cf.ThreadPoolExecutor(max_workers=4) as pool:
    jobs = {pool.submit(query, args): key for key, args in commands.items()}
    for job in cf.as_completed(jobs):
        report[jobs[job]] = job.result()
(root / "work/toss-removal-inspection.json").write_text(json.dumps(report, ensure_ascii=False, indent=2), encoding="utf-8")
for key in ["packages", "processes", "properties", "services", "settings_system", "settings_secure", "settings_global", "overlays"]:
    print(key)
    print("\n".join(line for line in report[key]["stdout"].splitlines() if any(term in line.lower() for term in ["toss", "sunmi", "minicat", "debugmode", "debug_mode", "customer", "kiosk", "systemui", "smraw", "navigationbar", "statusbar"])))
print("home", report["home"]["stdout"])
print("systemui", report["systemui"]["stdout"])
print("windows")
print("\n".join(line for line in report["window"]["stdout"].splitlines() if "Window #" in line or "package=" in line))
