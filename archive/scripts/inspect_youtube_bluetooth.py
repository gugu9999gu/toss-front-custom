from pathlib import Path
from datetime import datetime
import json
import subprocess

root = Path(__file__).resolve().parent.parent
adb = r"C:\Users\YOUR_USER\AppData\Local\Android\Sdk\platform-tools\adb.exe"
commands = {
    "id": ["id"],
    "version": ["getprop", "ro.build.version.release"],
    "features": ["pm", "list", "features"],
    "bluetooth": ["dumpsys", "bluetooth_manager"],
    "audio": ["dumpsys", "audio"],
    "packages": ["pm", "list", "packages"],
    "bt_config": ["getprop", "bluetooth.profile.a2dp.source.enabled"],
}
report = {"checked_at": datetime.now().astimezone().isoformat()}
for key, args in commands.items():
    result = subprocess.run([adb, "shell", *args], capture_output=True, text=True,
                            encoding="utf-8", errors="replace", timeout=20)
    report[key] = {"exit_code": result.returncode, "stdout": result.stdout, "stderr": result.stderr}
(root / "work" / "youtube-bluetooth-inspection.json").write_text(json.dumps(report, indent=2), encoding="utf-8")
for key in ["id", "version", "features", "bt_config"]:
    print(key, report[key])
print("PACKAGES")
print("\n".join(line for line in report["packages"]["stdout"].splitlines()
                if any(key in line.lower() for key in ["youtube", "google", "bluetooth", "webview", "browser"])))
print("BLUETOOTH SUMMARY")
print("\n".join(line for line in report["bluetooth"]["stdout"].splitlines()
                if any(key in line.lower() for key in ["state:", "enabled:", "a2dp", "avrcp", "profile", "bluetooth status", "adapter state", "supported"])))
