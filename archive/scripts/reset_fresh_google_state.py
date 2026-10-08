from pathlib import Path
import datetime as dt
import hashlib
import json
import re
import subprocess
import sys

sys.stdout.reconfigure(encoding="utf-8")
root = Path(__file__).resolve().parent.parent
out = root / "outputs/toss-front2-prepared"
adb = str(Path.home() / "AppData/Local/Android/Sdk/platform-tools/adb.exe")
prefix = [adb, "-s", sys.argv[1]]
report = {"time": dt.datetime.now().astimezone().isoformat(), "actions": []}

def shell(*args, timeout=30):
    p = subprocess.run([*prefix, "shell", *args], capture_output=True, text=True,
                       encoding="utf-8", errors="replace", timeout=timeout)
    report["actions"].append({"args": list(args), "code": p.returncode, "out": p.stdout.strip(), "err": p.stderr.strip()})
    assert p.returncode == 0, p.stderr
    return p.stdout.strip()

assert shell("getprop", "ro.serialno") == "FIRST_DEVICE_SERIAL_REDACTED"
accounts = shell("dumpsys", "account")
assert re.search(r"Accounts: 0\b", accounts) and not re.search(r"Accounts: [1-9]", accounts)
packages = ["com.google.android.youtube", "com.google.android.gms", "com.google.android.gsf", "com.android.vending"]
for pkg in packages:
    shell("am", "force-stop", pkg)
paths = ["user/0/" + p for p in packages] + ["user_de/0/" + p for p in packages]
backup = out / "new-google-data-before-reset.tar"
with backup.open("wb") as f:
    p = subprocess.run([*prefix, "exec-out", "tar", "-cf", "-", "-C", "/data", *paths],
                       stdout=f, stderr=subprocess.PIPE, timeout=90)
assert p.returncode == 0, p.stderr.decode(errors="replace")
assert backup.stat().st_size > 1000000
report["backup"] = {"path": str(backup), "bytes": backup.stat().st_size,
                    "sha256": hashlib.file_digest(backup.open("rb"), "sha256").hexdigest()}
for pkg in packages:
    assert shell("pm", "clear", "--user", "0", pkg) == "Success"
shell("pm", "revoke", "com.google.android.youtube", "android.permission.POST_NOTIFICATIONS")
(out / "fresh-google-state-reset.json").write_text(json.dumps(report, ensure_ascii=False, indent=2), encoding="utf-8")
print(json.dumps({"backup": report["backup"], "cleared_fresh_packages": packages}, ensure_ascii=False))
