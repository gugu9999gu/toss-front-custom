from pathlib import Path
import subprocess
import sys
import xml.etree.ElementTree as ET

sys.stdout.reconfigure(encoding="utf-8")
root = Path(__file__).resolve().parent.parent
adb = str(Path.home() / "AppData/Local/Android/Sdk/platform-tools/adb.exe")
output = root / "outputs/toss-front2-prepared"
png = subprocess.run([adb, "exec-out", "screencap", "-p"], capture_output=True, timeout=20, check=True).stdout
assert png.startswith(b"\x89PNG\r\n\x1a\n")
(output / "youtube-web-screen.png").write_bytes(png)
if "--screen-only" in sys.argv:
    print(str(output / "youtube-web-screen.png"))
    sys.exit(0)
subprocess.run([adb, "shell", "uiautomator", "dump", "/data/local/tmp/youtube-inspection.xml"], capture_output=True, timeout=20, check=True)
xml = subprocess.run([adb, "exec-out", "cat", "/data/local/tmp/youtube-inspection.xml"], capture_output=True, timeout=20, check=True).stdout
(root / "work/youtube-ui.xml").write_bytes(xml)
for node in ET.fromstring(xml).iter("node"):
    label = node.attrib.get("text", "") or node.attrib.get("content-desc", "")
    if label:
        print(repr(label), node.attrib.get("bounds"), node.attrib.get("clickable"))
