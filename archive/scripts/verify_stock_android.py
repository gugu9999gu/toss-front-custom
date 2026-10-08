from pathlib import Path
from datetime import datetime
import json
import subprocess
import xml.etree.ElementTree as ET

root = Path(__file__).resolve().parent.parent
adb = r"C:\Users\YOUR_USER\AppData\Local\Android\Sdk\platform-tools\adb.exe"
prepared = root / "outputs" / "toss-front2-prepared"
changes = json.loads((prepared / "stock-home-change.json").read_text(encoding="utf-8"))
def shell(*args):
    p = subprocess.run([adb, "shell", *args], capture_output=True, text=True,
                       encoding="utf-8", errors="replace", timeout=15)
    assert p.returncode == 0, p.stderr
    return p.stdout.strip()

assert shell("getprop", "sys.boot_completed") == "1"
assert shell("getprop", "ro.build.version.release") == "13"
identity = shell("id")
assert "uid=0(root)" in identity
home = shell("cmd", "package", "resolve-activity", "--brief", "-a", "android.intent.action.MAIN",
             "-c", "android.intent.category.HOME").splitlines()[-1]
assert home.split("/")[0] == "com.android.launcher3"
disabled = {line.partition(":")[2] for line in shell("pm", "list", "packages", "-d").splitlines()}
plan = json.loads((prepared / "component-block-plan.json").read_text(encoding="utf-8"))
component_state = subprocess.check_output([adb, "exec-out", "abx2xml", "/data/system/users/0/package-restrictions.xml", "-"], timeout=15)
state = {pkg.attrib["name"]: pkg for pkg in ET.fromstring(component_state).findall("pkg")}
verified_components = 0
for package in plan["packages"]:
    blocked = {node.attrib["name"] for node in state[package["package"]].findall("disabled-components/item")}
    expected = {item["class"] for item in package["components"]}
    assert expected.issubset(blocked), {"package": package["package"], "missing_components": sorted(expected - blocked)}
    verified_components += len(expected)
assert verified_components == plan["component_count"] == 317
keys = ["systemui_navigationbar", "systemui_statusbar", "systemui_statusbar_panel_disabled"]
navigation = {key: shell("settings", "get", "system", key) for key in keys}
assert all(value == "0" for value in navigation.values()), navigation
activities = shell("dumpsys", "activity", "activities")
foreground = [line.strip() for line in activities.splitlines() if "topResumedActivity=" in line]
processes = shell("ps", "-A", "-o", "NAME").splitlines()
unexpected = [name for name in processes if any(name == pkg or name.startswith(pkg + ":")
                                               for pkg in changes["disabled_packages"])]
assert not unexpected, unexpected
connectivity = shell("dumpsys", "connectivity")
(root / "work" / "connectivity-after.json").write_text(json.dumps({"dump": connectivity}), encoding="utf-8")
all_packages = {line.partition(":")[2] for line in shell("pm", "list", "packages").splitlines()}
record = {"verified_at": datetime.now().astimezone().isoformat(), "android_version": "13",
          "home_persisted_after_reboot": True, "home": home, "root_adb_after_reboot": True,
          "toss_packages_with_components_blocked": sorted(changes["disabled_packages"]),
          "blocked_component_count_after_reboot": verified_components,
          "package_level_disable_states_after_reboot": sorted(set(changes["disabled_packages"]) & disabled),
          "disabled_toss_processes_absent": True, "navigation_settings_persisted": navigation,
          "foreground": foreground, "google_play_store_installed": "com.android.vending" in all_packages,
          "google_play_services_installed": "com.google.android.gms" in all_packages,
          "userdata_erased": False, "operating_system_replaced": False,
          "physical_touch_verified": False}
assert record["google_play_store_installed"] is False
assert record["google_play_services_installed"] is False
(prepared / "android-verification.json").write_text(json.dumps(record, indent=2), encoding="utf-8")
print(json.dumps(record, indent=2))
