from pathlib import Path
import datetime as dt
import json
import re
import subprocess
import sys

sys.stdout.reconfigure(encoding="utf-8")
root = Path(__file__).resolve().parent.parent
adb = str(Path.home() / "AppData/Local/Android/Sdk/platform-tools/adb.exe")
prefix = [adb, "-s", sys.argv[1]]
report = {"time": dt.datetime.now().astimezone().isoformat(), "packages": {}}
for package in ["com.google.android.gsf", "com.google.android.gms", "com.android.vending", "com.google.android.youtube"]:
    p = subprocess.run([*prefix, "shell", "dumpsys", "package", package], capture_output=True, text=True, encoding="utf-8", timeout=20)
    assert p.returncode == 0
    report["packages"][package] = p.stdout
crash = subprocess.run([*prefix, "shell", "logcat", "-b", "crash", "-d"], capture_output=True, text=True, encoding="utf-8", errors="replace", timeout=20)
report["crashes"] = crash.stdout
(root / "outputs/toss-front2-prepared/google-system-permissions.json").write_text(json.dumps(report, ensure_ascii=False, indent=2), encoding="utf-8")
for package, contents in report["packages"].items():
    selected = [line.strip() for line in contents.splitlines() if re.search(r"pkgFlags=|MANAGE_USERS: granted=|READ_DEVICE_CONFIG: granted=|READ_GSERVICES: granted=", line)]
    print(package + ": " + "\n".join(selected[:5]))
fatal = [line for line in crash.stdout.splitlines() if "FATAL EXCEPTION" in line or "Process:" in line or "Caused by:" in line]
print("Latest crash summaries:")
print("\n".join(fatal[-14:]))
