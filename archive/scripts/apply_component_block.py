from pathlib import Path
from datetime import datetime
import json
import re
import shlex
import subprocess

root = Path(__file__).resolve().parent.parent
prepared = root / "outputs" / "toss-front2-prepared"
adb = r"C:\Users\YOUR_USER\AppData\Local\Android\Sdk\platform-tools\adb.exe"
plan = json.loads((prepared / "component-block-plan.json").read_text(encoding="utf-8"))
changes = json.loads((prepared / "stock-home-change.json").read_text(encoding="utf-8"))
assert {p["package"] for p in plan["packages"]} == set(changes["disabled_packages"])
assert plan["component_count"] == 317 and len(plan["packages"]) == 9
record = {"started_at": datetime.now().astimezone().isoformat(), "user": 0,
          "applied_components": [], "complete": False, "apps_uninstalled": False, "userdata_erased": False}
path = prepared / "component-block-result.json"
def save():
    path.write_text(json.dumps(record, indent=2), encoding="utf-8")
def shell(*args):
    result = subprocess.run([adb, "shell", shlex.join(args)], capture_output=True, text=True,
                            encoding="utf-8", errors="replace", timeout=15)
    assert result.returncode == 0, result.stderr
    return result.stdout.strip()

assert "uid=0(root)" in shell("id")
save()
for package in plan["packages"]:
    name = package["package"]
    assert name.startswith("com.tossplace.")
    for item in package["components"]:
        assert re.fullmatch(r"[A-Za-z0-9_.$]+", item["class"])
        component = name + "/" + item["class"]
        result = shell("pm", "disable", "--user", "0", component)
        assert "new state: disabled" in result, result
        record["applied_components"].append(component)
        save()
    shell("am", "force-stop", "--user", "0", name)
    print(json.dumps({"package": name, "components_disabled": len(package["components"]),
                      "total_applied": len(record["applied_components"])}), flush=True)
for package in changes["enabled_packages"]:
    assert "new state: enabled" in shell("pm", "enable", "--user", "0", package)
assert "Success" in shell("cmd", "package", "set-home-activity", "--user", "0", changes["new_home"])
shell("input", "keyevent", "KEYCODE_HOME")
record.update({"complete": True, "completed_at": datetime.now().astimezone().isoformat()})
save()
print(json.dumps({"all_complete": True, "components_disabled": len(record["applied_components"])}), flush=True)
