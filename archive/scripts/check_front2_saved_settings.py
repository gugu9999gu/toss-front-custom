from pathlib import Path
import json
import re
import subprocess
import sys
import xml.etree.ElementTree as ET

adb = str(Path.home() / "AppData/Local/Android/Sdk/platform-tools/adb.exe")
prefix = [adb, "-s", sys.argv[1]]
def read(*args):
    p = subprocess.run([*prefix, "shell", *args], capture_output=True, timeout=15)
    assert p.returncode == 0, p.stderr
    return p.stdout
assert read("getprop", "ro.serialno").strip() == b"FIRST_DEVICE_SERIAL_REDACTED"
packages = ET.fromstring(read("abx2xml", "/data/system/users/0/package-restrictions.xml", "-"))
target = ["com.google.android.gms", "com.google.android.gsf", "com.android.vending"]
states = {node.get("name"): {k: node.get(k) for k in ["enabled", "installed", "stopped"]} for node in packages.iter("pkg") if node.get("name") in target}
settings_raw = read("abx2xml", "/data/system/users/0/settings_global.xml", "-").decode("utf-8")
settings = ET.fromstring(re.search(r"<settings\b[\s\S]*?</settings>", settings_raw).group(0))
watermark = [node.get("value") for node in settings.iter("setting") if node.get("name") == "hide_toss_wartermark"]
print(json.dumps({"saved_google_states": states, "saved_watermark": watermark}))
assert all(states[p]["enabled"] == "3" for p in target), states
assert watermark == ["1"], watermark
