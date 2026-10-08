from pathlib import Path
import datetime as dt
import hashlib
import json
import re
import subprocess
import sys

sys.stdout.reconfigure(encoding="utf-8")
assert len(sys.argv) == 3 and sys.argv[1] == "--serial"
root = Path(__file__).resolve().parent.parent
adb = str(Path.home() / "AppData/Local/Android/Sdk/platform-tools/adb.exe")
prefix = [adb, "-s", sys.argv[2]]
out = root / "outputs/toss-front2-prepared"
report_path = out / "toss-removal-result.json"
report = {"started_at": dt.datetime.now().astimezone().isoformat(), "actions": [], "complete": False,
          "scope": "Uninstall Toss functional packages for Android user 0; keep recovery data and base firmware."}

def save():
    report_path.write_text(json.dumps(report, ensure_ascii=False, indent=2), encoding="utf-8")

def run(args, timeout=20, check=True):
    p = subprocess.run([*prefix, "shell", *args], capture_output=True, text=True,
                       encoding="utf-8", errors="replace", timeout=timeout)
    result = {"args": args, "code": p.returncode, "out": p.stdout.strip(), "err": p.stderr.strip()}
    if check and p.returncode != 0:
        raise RuntimeError(json.dumps(result, ensure_ascii=False))
    return result

identity = run(["id"])["out"]
assert "uid=0(root)" in identity
assert run(["getprop", "ro.product.model"])["out"] == "toss_front2"
assert run(["getprop", "ro.serialno"])["out"] == "FIRST_DEVICE_SERIAL_REDACTED"
assert run(["am", "get-current-user"])["out"] == "0"
assert run(["getprop", "sys.boot_completed"])["out"] == "1"
manifest = json.loads((root / "outputs/toss-front2-backup/backup-manifest.json").read_text(encoding="utf-8"))
assert manifest["complete"] and manifest["total_bytes"] == 31977373696
assert all((root / "outputs/toss-front2-backup" / i["filename"]).stat().st_size == i["bytes"] for i in manifest["images"])

packages = run(["pm", "list", "packages", "--user", "0"])["out"].splitlines()
installed = {x.removeprefix("package:") for x in packages}
system = {x.removeprefix("package:") for x in run(["pm", "list", "packages", "-s", "--user", "0"])["out"].splitlines()}
selected = sorted(x for x in installed if x.startswith("com.tossplace.") or x.startswith("im.toss.") or x in {"com.sunmi.welcome", "com.sunmi.monitor", "com.sunmi.targetpos.toss"})
assert selected and set(selected).issubset(system), "Every selected package must have a system copy for install-existing recovery."
assert all(re.fullmatch(r"[A-Za-z][A-Za-z0-9_.]+", x) for x in selected)
report["selected_packages"] = selected
report["recovery_commands"] = ["cmd package install-existing --user 0 " + x for x in selected]
inspection = root / "work/toss-removal-inspection.json"
report["before_inspection"] = json.loads(inspection.read_text(encoding="utf-8"))
save()

settings = [("global", "hide_toss_wartermark", "1"), ("system", "minicat_launcher_enabled", "0")]
report["settings_before"] = [{"namespace": ns, "key": key, "value": run(["settings", "get", ns, key])["out"]} for ns, key, _ in settings]
save()
for ns, key, value in settings:
    result = run(["settings", "put", ns, key, value])
    result["readback"] = run(["settings", "get", ns, key])["out"]
    assert result["readback"] == value
    report["actions"].append(result)
    save()

priority = ["com.tossplace.device.management.release", "com.tossplace.device.management.debug",
            "com.tossplace.duo.installer.release", "com.tossplace.duo.installer.debug", "com.tossplace.appcenter.release",
            "com.sunmi.monitor", "com.tossplace.device.minicatmanager", "com.sunmi.welcome", "com.tossplace.device.ota"]
ordered = [x for x in priority if x in selected] + [x for x in selected if x not in priority]
for package in ordered:
    run(["am", "force-stop", "--user", "0", package])
    result = run(["pm", "uninstall", "-k", "--user", "0", package], check=False)
    report["actions"].append(result)
    save()
    if result["code"] != 0 or result["out"] != "Success":
        raise RuntimeError("Uninstall failed for " + package + ": " + json.dumps(result))
    print(json.dumps({"package": package, "result": "uninstalled_user_0"}), flush=True)

for args in [["pm", "enable", "--user", "0", "com.android.launcher3"],
             ["cmd", "package", "set-home-activity", "--user", "0", "com.android.launcher3/.uioverrides.QuickstepLauncher"],
             ["am", "start", "--user", "0", "-n", "com.android.launcher3/.uioverrides.QuickstepLauncher"]]:
    report["actions"].append(run(args))
    save()
remaining = {x.removeprefix("package:") for x in run(["pm", "list", "packages", "--user", "0"])["out"].splitlines()}
report["remaining_selected"] = sorted(remaining.intersection(selected))
assert not report["remaining_selected"]
pid = run(["pidof", "com.android.systemui"])["out"]
assert re.fullmatch(r"[0-9]+", pid)
report["actions"].append(run(["kill", pid]))
report["complete"] = True
report["completed_at"] = dt.datetime.now().astimezone().isoformat()
save()
print(json.dumps({"complete": True, "removed_packages": len(selected), "report": str(report_path)}, ensure_ascii=False), flush=True)
