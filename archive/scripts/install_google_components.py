from pathlib import Path
import datetime as dt
import hashlib
import json
import subprocess
import sys

sys.stdout.reconfigure(encoding="utf-8")
root = Path(__file__).resolve().parent.parent
downloads = Path.home() / "Downloads"
out = root / "outputs/toss-front2-prepared/official-google-installation.json"
adb = str(Path.home() / "AppData/Local/Android/Sdk/platform-tools/adb.exe")
prefix = [adb, "-s", sys.argv[1]]
serial = subprocess.run([*prefix, "shell", "getprop", "ro.serialno"], capture_output=True, text=True, timeout=10)
assert serial.returncode == 0 and serial.stdout.strip() == "FIRST_DEVICE_SERIAL_REDACTED"
items = [
    ("com.google.android.gsf_13-33_minAPI33(nodpi)_apkmirror.com.apk", "com.google.android.gsf", "7bed0b01fc90a790804235bd79a9779ae33f1ea3ec77a0e1089ca5c0d256077a"),
    ("com.google.android.gms_26.37.37_(190400-994713346)-263737029_minAPI31(arm64-v8a,armeabi-v7a)(nodpi)_apkmirror.com.apk", "com.google.android.gms", "79fd97dca188359009899294a1989229b5f2f3b87823a992811a55874bcdd316"),
    ("com.android.vending_53.4.34-31_0_PR_991240293-85343430_minAPI31(arm64-v8a,armeabi-v7a,x86,x86_64)(nodpi)_apkmirror.com.apk", "com.android.vending", "f7e8f3656958e0defc37cfa30d7013c0848d916c89ab7e50da2b41d4d3e21e19"),
]
report = {"started_at": dt.datetime.now().astimezone().isoformat(), "device_serial": serial.stdout.strip(), "items": [], "complete": False}
def save():
    out.write_text(json.dumps(report, ensure_ascii=False, indent=2), encoding="utf-8")
for filename, package, expected_hash in items:
    path = downloads / filename
    verification = json.loads(path.with_suffix(".verification.json").read_text(encoding="utf-8"))
    assert verification["package"] == package
    assert verification["minimum_sdk"] <= 33
    assert verification["certificates_sha256"] == ["7ce83c1b71f3d572fed04c8d40c5cb10ff75e6d87d9df6fbd53f0468c2905053"]
    assert verification["sha256"] == expected_hash == hashlib.sha256(path.read_bytes()).hexdigest()
    print("Installing verified Google component: " + package, flush=True)
    result = subprocess.run([*prefix, "install", "-r", str(path)], capture_output=True,
                            text=True, encoding="utf-8", errors="replace", timeout=240)
    report["items"].append({"package": package, "verification": verification, "install_code": result.returncode,
                             "install_output": result.stdout, "install_error": result.stderr})
    save()
    print(result.stdout.strip() + result.stderr.strip(), flush=True)
    assert result.returncode == 0 and "Success" in result.stdout, "Installation failed: " + package
report["complete"] = True
report["completed_at"] = dt.datetime.now().astimezone().isoformat()
save()
print("All three Google components installed.", flush=True)
