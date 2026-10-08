from pathlib import Path
import datetime as dt
import json
import subprocess
import sys

sys.stdout.reconfigure(encoding="utf-8")
root = Path(__file__).resolve().parent.parent
path = root / "outputs/toss-front2-prepared/google-system-test.json"
report = json.loads(path.read_text(encoding="utf-8"))
adb = str(Path.home() / "AppData/Local/Android/Sdk/platform-tools/adb.exe")
prefix = [adb, "-s", sys.argv[1]]
base = "/mnt/front2-google-system-test"
stage = "/data/local/tmp/front2-google-system-test/upper/priv-app"
def run(args, timeout=30):
    p = subprocess.run([*prefix, *args], capture_output=True, text=True, encoding="utf-8", errors="replace", timeout=timeout)
    report["actions"].append({"args": args, "code": p.returncode, "out": p.stdout.strip(), "err": p.stderr.strip()})
    path.write_text(json.dumps(report, ensure_ascii=False, indent=2), encoding="utf-8")
    assert p.returncode == 0, p.stdout + p.stderr
    return p.stdout.strip()
assert run(["shell", "getprop", "ro.serialno"]) == "FIRST_DEVICE_SERIAL_REDACTED"
mem = run(["shell", "cat", "/proc/meminfo"])
available = int(next(x.split()[1] for x in mem.splitlines() if x.startswith("MemAvailable:")))
assert available > 1300000, "Insufficient free RAM for temporary test"
run(["shell", "mkdir", "-p", base])
run(["shell", "mount", "-t", "tmpfs", "-o", "size=512m,nodev,nosuid,mode=0755,fscontext=u:object_r:tmpfs:s0,context=u:object_r:system_file:s0", "tmpfs", base])
run(["shell", "mkdir", "-p", base + "/upper/priv-app", base + "/work"])
for name in ["GoogleServicesFramework", "GooglePlayServices", "GooglePlayStore"]:
    dest = base + "/upper/priv-app/" + name
    run(["shell", "mkdir", "-p", dest])
    print("Memory test: " + name, flush=True)
    run(["shell", "cp", "-p", stage + "/" + name + "/" + name + ".apk", dest + "/" + name + ".apk"], timeout=90)
run(["shell", "mount", "-t", "overlay", "overlay", "-o", "lowerdir=/system_ext,upperdir=" + base + "/upper,workdir=" + base + "/work", "/system_ext"])
mounts = run(["shell", "cat", "/proc/mounts"])
assert any(line.startswith("overlay /system_ext overlay ") for line in mounts.splitlines())
report["mounted"] = True
report["overlay_storage"] = "RAM-backed tmpfs; removed by full reboot"
report["staged_at"] = dt.datetime.now().astimezone().isoformat()
path.write_text(json.dumps(report, ensure_ascii=False, indent=2), encoding="utf-8")
print("Temporary memory overlay mounted.", flush=True)
