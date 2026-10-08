from pathlib import Path
from datetime import datetime
import json
import re
import subprocess

root = Path(__file__).resolve().parent.parent
adb = r"C:\Users\YOUR_USER\AppData\Local\Android\Sdk\platform-tools\adb.exe"
disable = ["com.tossplace.app.release", "com.tossplace.app.debug", "com.tossplace.app.stage",
           "com.tossplace.device.management.release", "com.tossplace.device.management.debug",
           "com.tossplace.duo.installer.release", "com.tossplace.duo.installer.debug",
           "com.tossplace.device.ota"]
enable = ["com.android.launcher3", "org.chromium.webview_shell", "com.android.documentsui",
          "com.android.gallery3d", "org.codeaurora.snapcam"]
home = "com.android.launcher3/com.android.launcher3.uioverrides.QuickstepLauncher"
def shell(*args):
    result = subprocess.run([adb, "shell", *args], capture_output=True, text=True, timeout=15)
    assert result.returncode == 0, result.stderr
    return result.stdout.strip()

assert shell("getprop", "sys.boot_completed") == "1"
assert shell("am", "get-current-user") == "0"
identity = shell("id")
assert "uid=0(root)" in identity
original_home = shell("cmd", "package", "resolve-activity", "--brief", "-a",
                      "android.intent.action.MAIN", "-c", "android.intent.category.HOME").splitlines()[-1]
original = {}
for package in [*disable, *enable]:
    state = shell("dumpsys", "package", package)
    user = re.search(r"User 0: .*?enabled=(\d+)", state)
    assert user, f"Missing package/user: {package}"
    original[package] = {"enabled": int(user.group(1))}
record = {"started_at": datetime.now().astimezone().isoformat(), "user": 0,
          "original_home": original_home, "new_home": home,
          "original_package_states": original, "disabled_packages": [], "enabled_packages": [],
          "apps_uninstalled": False, "userdata_erased": False, "complete": False}
path = root / "outputs" / "toss-front2-prepared" / "stock-home-change.json"
def save():
    path.write_text(json.dumps(record, indent=2), encoding="utf-8")
save()
result = shell("cmd", "package", "set-home-activity", "--user", "0", home)
assert "Success" in result, result
record["home_preference_updated"] = True
save()
for package in disable:
    result = shell("pm", "disable-user", "--user", "0", package)
    assert "new state: disabled-user" in result, result
    record["disabled_packages"].append(package)
    save()
for package in enable:
    result = shell("pm", "enable", "--user", "0", package)
    assert "new state: enabled" in result, result
    record["enabled_packages"].append(package)
    save()
record["home_start_result"] = shell("am", "start", "-a", "android.intent.action.MAIN", "-c",
                                    "android.intent.category.HOME", "-n", home)
resolved = shell("cmd", "package", "resolve-activity", "--brief", "-a", "android.intent.action.MAIN",
                 "-c", "android.intent.category.HOME").splitlines()[-1]
assert resolved.split("/")[0] == "com.android.launcher3", resolved
record.update({"complete": True, "resolved_home": resolved,
               "completed_at": datetime.now().astimezone().isoformat()})
save()
print(json.dumps({"home": resolved, "disabled_count": len(disable), "enabled_count": len(enable),
                  "userdata_erased": False, "apps_uninstalled": False}, indent=2))
