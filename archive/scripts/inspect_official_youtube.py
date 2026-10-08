from pathlib import Path
import datetime as dt
import json
import subprocess
import sys
import xml.etree.ElementTree as ET

sys.stdout.reconfigure(encoding="utf-8")
root = Path(__file__).resolve().parent.parent
out = root / "outputs/toss-front2-prepared"
adb = str(Path.home() / "AppData/Local/Android/Sdk/platform-tools/adb.exe")
prefix = [adb, "-s", sys.argv[1]]
phase = sys.argv[2]
def shell(*args, timeout=20):
    result = subprocess.run([*prefix, "shell", *args], capture_output=True,
                            text=True, encoding="utf-8", errors="replace", timeout=timeout)
    return {"code": result.returncode, "out": result.stdout.strip(), "err": result.stderr.strip()}
assert shell("getprop", "ro.serialno")["out"] == "FIRST_DEVICE_SERIAL_REDACTED"
report = {"time": dt.datetime.now().astimezone().isoformat(), "phase": phase}
report["package"] = shell("dumpsys", "package", "com.google.android.youtube")
report["google_packages"] = shell("pm", "list", "packages", "com.google")
report["activity"] = shell("cmd", "package", "resolve-activity", "--brief", "-a", "android.intent.action.MAIN", "-c", "android.intent.category.LAUNCHER", "com.google.android.youtube")
report["pid"] = shell("pidof", "com.google.android.youtube")
report["windows"] = shell("dumpsys", "window", "windows")
report["logs"] = shell("logcat", "-d", "-t", "1000")
report["audio"] = shell("dumpsys", "audio")
report["media_sessions"] = shell("dumpsys", "media_session")
screen = subprocess.run([*prefix, "exec-out", "screencap", "-p"], capture_output=True, timeout=20)
assert screen.returncode == 0 and screen.stdout.startswith(b"\x89PNG\r\n\x1a\n")
screen_path = out / ("official-youtube-" + phase + ".png")
screen_path.write_bytes(screen.stdout)
report["screen"] = str(screen_path)
ui = shell("uiautomator", "dump", "/data/local/tmp/youtube-ui.xml", timeout=25)
report["ui_dump"] = ui
if ui["code"] == 0:
    report["ui_xml"] = shell("cat", "/data/local/tmp/youtube-ui.xml")
    tree = ET.fromstring(report["ui_xml"]["out"])
    report["ui_nodes"] = [{k: node.get(k) for k in ["text", "content-desc", "resource-id", "bounds", "clickable"]}
                          for node in tree.iter("node") if node.get("text") or node.get("content-desc")]
(out / ("official-youtube-" + phase + ".json")).write_text(json.dumps(report, ensure_ascii=False, indent=2), encoding="utf-8")
print(json.dumps({"phase": phase, "google_packages": report["google_packages"]["out"], "pid": report["pid"]["out"], "screen": str(screen_path), "ui_nodes": report.get("ui_nodes", [])[:80]}, ensure_ascii=False))
