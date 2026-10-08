from pathlib import Path
import json
import subprocess

root = Path(__file__).resolve().parent.parent
p = root / "outputs/toss-front2-prepared/toss-removal-result.json"
r = json.loads(p.read_text(encoding="utf-8"))
a = [str(Path.home() / "AppData/Local/Android/Sdk/platform-tools/adb.exe"), "-s", "DEVICE_ADDRESS_REDACTED:47055", "shell"]
assert subprocess.check_output(a + ["getprop", "ro.serialno"], text=True).strip() == "FIRST_DEVICE_SERIAL_REDACTED"
extra = ["com.sunmi.usbscreen", "com.sunmi.aging", "com.sunmi.cit", "com.sunmi.obatest"]
system = subprocess.check_output(a + ["pm", "list", "packages", "-s", "--user", "0"], text=True).splitlines()
assert all("package:" + v in system for v in extra)
r["persist_adb_notify_before"] = subprocess.check_output(a + ["getprop", "persist.adb.notify"], text=True).strip()
subprocess.run(a + ["setprop", "persist.adb.notify", "0"], check=True)
r["persist_adb_notify_after"] = subprocess.check_output(a + ["getprop", "persist.adb.notify"], text=True).strip()
assert r["persist_adb_notify_after"] == "0"
p.write_text(json.dumps(r, ensure_ascii=False, indent=2), encoding="utf-8")
for v in extra:
    q = subprocess.run(a + ["pm", "uninstall", "-k", "--user", "0", v], capture_output=True, text=True)
    assert q.returncode == 0 and q.stdout.strip() == "Success", q.stderr + q.stdout
    r["selected_packages"].append(v)
    r["recovery_commands"].append("cmd package install-existing --user 0 " + v)
    r["actions"].append({"args": ["pm", "uninstall", "-k", "--user", "0", v], "code": q.returncode, "out": q.stdout.strip()})
    p.write_text(json.dumps(r, ensure_ascii=False, indent=2), encoding="utf-8")
    print(v, q.stdout.strip())
subprocess.run(a + ["input", "keyevent", "3"], check=True)
