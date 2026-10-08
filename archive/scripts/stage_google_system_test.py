"""Stage a reboot-reversible overlay; never writes firmware block devices."""
from pathlib import Path
import datetime as dt
import json
import subprocess
import sys

sys.stdout.reconfigure(encoding="utf-8")
root = Path(__file__).resolve().parent.parent
report_path = root / "outputs/toss-front2-prepared/google-system-test.json"
source = json.loads((root / "outputs/toss-front2-prepared/official-google-installation.json").read_text(encoding="utf-8"))
adb = str(Path.home() / "AppData/Local/Android/Sdk/platform-tools/adb.exe")
prefix = [adb, "-s", sys.argv[1]]
base = "/data/local/tmp/front2-google-system-test"
report = {"started_at": dt.datetime.now().astimezone().isoformat(), "scope": "Temporary system_ext overlay, firmware blocks remain unchanged", "actions": [], "mounted": False}
def run(args, timeout=30):
    p = subprocess.run([*prefix, *args], capture_output=True, text=True, encoding="utf-8", errors="replace", timeout=timeout)
    report["actions"].append({"args": args, "code": p.returncode, "out": p.stdout.strip(), "err": p.stderr.strip()})
    report_path.write_text(json.dumps(report, ensure_ascii=False, indent=2), encoding="utf-8")
    assert p.returncode == 0, p.stdout + p.stderr
    return p.stdout.strip()
assert run(["shell", "getprop", "ro.serialno"]) == "FIRST_DEVICE_SERIAL_REDACTED"
assert "uid=0" in run(["shell", "id"])
assert source["complete"]
run(["shell", "mkdir", "-p", base + "/upper/priv-app", base + "/work"])
for item, name in zip(source["items"], ["GoogleServicesFramework", "GooglePlayServices", "GooglePlayStore"]):
    v = item["verification"]
    assert v["certificates_sha256"] == ["7ce83c1b71f3d572fed04c8d40c5cb10ff75e6d87d9df6fbd53f0468c2905053"]
    destination = base + "/upper/priv-app/" + name
    run(["shell", "mkdir", "-p", destination])
    print("Staging " + v["package"], flush=True)
    run(["push", v["apk"], destination + "/" + name + ".apk"], timeout=240)
    actual = run(["shell", "sha256sum", destination + "/" + name + ".apk"]).split()[0]
    assert actual == v["sha256"]
run(["shell", "chmod", "-R", "u=rwX,go=rX", base + "/upper"])
run(["shell", "chown", "-R", "root:root", base + "/upper"])
run(["shell", "chcon", "-R", "u:object_r:system_file:s0", base + "/upper"])
run(["shell", "mount", "-t", "overlay", "overlay", "-o", "lowerdir=/system_ext,upperdir=" + base + "/upper,workdir=" + base + "/work", "/system_ext"])
mounts = run(["shell", "cat", "/proc/mounts"])
assert any(line.startswith("overlay /system_ext overlay ") for line in mounts.splitlines())
report["mounted"] = True
report["staged_at"] = dt.datetime.now().astimezone().isoformat()
report_path.write_text(json.dumps(report, ensure_ascii=False, indent=2), encoding="utf-8")
print("Temporary overlay mounted. Full power reboot removes it.", flush=True)
