from pathlib import Path
from datetime import datetime
import json
import subprocess
import time

root = Path(__file__).resolve().parent.parent
adb = r"C:\Users\YOUR_USER\AppData\Local\Android\Sdk\platform-tools\adb.exe"
deadline = time.monotonic() + 100
attempts = []
success = False
while time.monotonic() < deadline:
    try:
        result = subprocess.run([adb, "shell", "id"], capture_output=True, text=True, timeout=4)
        identity = result.stdout.strip()
        attempts.append({"time": datetime.now().astimezone().isoformat(), "returncode": result.returncode,
                         "shell_id": identity, "error": result.stderr.strip()})
        if result.returncode == 0 and "uid=" in identity:
            success = True
            break
    except subprocess.TimeoutExpired:
        attempts.append({"time": datetime.now().astimezone().isoformat(), "timeout": True})
    time.sleep(2)

commands = {
    "android_version": ["getprop", "ro.build.version.release"],
    "boot_completed": ["getprop", "sys.boot_completed"],
    "debuggable": ["getprop", "ro.debuggable"],
    "device_policy": ["dumpsys", "device_policy"],
    "current_user": ["am", "get-current-user"],
    "home_activity": ["cmd", "package", "resolve-activity", "--brief", "-a", "android.intent.action.MAIN", "-c", "android.intent.category.HOME"],
    "installed_packages": ["pm", "list", "packages", "-f"],
    "disabled_packages": ["pm", "list", "packages", "-d"],
}
captured = {}
if success:
    for key, args in commands.items():
        try:
            result = subprocess.run([adb, "shell", *args], capture_output=True, text=True, timeout=12)
            captured[key] = {"returncode": result.returncode, "stdout": result.stdout, "stderr": result.stderr}
        except subprocess.TimeoutExpired:
            captured[key] = {"timeout": True}
report = {"persistent_shell_observed": success, "attempts": attempts, "commands": captured}
(root / "work" / "android-boot-inspection.json").write_text(json.dumps(report, indent=2), encoding="utf-8")
print(json.dumps({"shell_connected": success, "shell_id": attempts[-1].get("shell_id", ""),
                  "android_version": captured.get("android_version", {}).get("stdout", "").strip(),
                  "debuggable": captured.get("debuggable", {}).get("stdout", "").strip(),
                  "boot_completed": captured.get("boot_completed", {}).get("stdout", "").strip(),
                  "home_activity": captured.get("home_activity", {}).get("stdout", "").strip()}, indent=2))
