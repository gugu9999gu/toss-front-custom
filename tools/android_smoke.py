"""Check native WebView -> PC dry-run on an explicitly selected emulator only.

Requires an installed, paired APK and FrontDeck --dry-run on port 38765.
No Windows key presses are sent. Never accepts a physical device.
"""
import argparse
import json
from pathlib import Path
import re
import time
from urllib.request import urlopen
import xml.etree.ElementTree as ET
from device import Device

def main():
    parser = argparse.ArgumentParser(); parser.add_argument("--serial", required=True); parser.add_argument("--adb")
    parser.add_argument("--screenshot", type=Path)
    args = parser.parse_args(); device = Device(args.serial, args.adb)
    if not args.serial.startswith("emulator-") or device.run("shell", "getprop", "ro.kernel.qemu") != "1":
        raise RuntimeError("이 시험은 Android 가상 기기에서만 실행할 수 있습니다.")
    with urlopen("http://127.0.0.1:38765/api/health", timeout=3) as response: health = json.load(response)
    if health.get("app") != "FrontDeck" or health.get("dry_run") is not True:
        raise RuntimeError("PC 버튼 시험은 반드시 --dry-run 모드여야 합니다.")
    def snapshot():
        device.run("shell", "uiautomator", "dump", "/sdcard/frontdeck-smoke.xml")
        return ET.fromstring(device.run("exec-out", "cat", "/sdcard/frontdeck-smoke.xml"))
    def wait_text(text, timeout=20):
        deadline = time.monotonic() + timeout
        while time.monotonic() < deadline:
            tree = snapshot()
            if any(n.get("text") == text for n in tree.iter("node")): return tree
            time.sleep(.5)
        raise RuntimeError("표시 확인 실패: " + text)
    def tap(tree, text):
        nodes = [n for n in tree.iter("node") if n.get("text") == text and n.get("clickable") == "true"]
        if len(nodes) != 1 or nodes[0].get("enabled") != "true": raise RuntimeError("버튼 확인 실패: " + text)
        values = re.fullmatch(r"\[(\d+),(\d+)\]\[(\d+),(\d+)\]", nodes[0].get("bounds"))
        x1, y1, x2, y2 = map(int, values.groups())
        device.run("shell", "input", "tap", str((x1 + x2) // 2), str((y1 + y2) // 2))
    def tiles(tree):
        region = next(n for n in tree.iter("node") if n.get("resource-id") == "buttons")
        return [n for n in region.iter("node") if n.get("class") == "android.widget.Button"]

    device.run("shell", "input", "keyevent", "KEYCODE_WAKEUP")
    device.run("shell", "wm", "dismiss-keyguard")
    device.run("shell", "am", "start", "-n", "dev.tossfront.deck/.DeckActivity")
    tree = wait_text("PC 연결됨")
    if not any(n.get("text") == "메모장" for n in tree.iter("node")):
        tap(tree, "작업"); tree = wait_text("메모장")
    if len(tiles(tree)) != 12: raise RuntimeError("작업 버튼 수가 다릅니다.")
    tap(tree, "메모장"); tree = wait_text("메모장 · 시험 모드")
    tap(tree, "미디어"); tree = wait_text("재생 / 일시 정지")
    if len(tiles(tree)) != 12: raise RuntimeError("미디어 버튼 수가 다릅니다.")
    tap(tree, "재생 / 일시 정지"); wait_text("재생 / 일시 정지 · 시험 모드")
    try:
        device.run("reverse", "--remove", "tcp:38765")
        tree = wait_text("PC 연결 확인 필요")
        if not all(n.get("enabled") == "false" for n in tiles(tree)): raise RuntimeError("연결 해제 후 버튼이 활성 상태입니다.")
    finally:
        device.run("reverse", "tcp:38765", "tcp:38765")
    tree = wait_text("PC 연결됨")
    if not all(n.get("enabled") == "true" for n in tiles(tree)): raise RuntimeError("재연결 후 버튼이 비활성 상태입니다.")
    device.run("shell", "cmd", "package", "set-home-activity", "--user", "0", "dev.tossfront.deck/.DeckActivity")
    device.run("shell", "am", "force-stop", "dev.tossfront.deck")
    device.run("shell", "input", "keyevent", "KEYCODE_HOME")
    tree = wait_text("PC 연결됨")
    home = device.run("shell", "cmd", "package", "resolve-activity", "--brief", "--user", "0", "-a", "android.intent.action.MAIN", "-c", "android.intent.category.HOME")
    if "dev.tossfront.deck" not in home: raise RuntimeError("기본 홈이 다릅니다.")
    if args.screenshot:
        device.run("shell", "screencap", "-p", "/sdcard/frontdeck-smoke.png")
        args.screenshot.parent.mkdir(parents=True, exist_ok=True)
        device.run("pull", "/sdcard/frontdeck-smoke.png", str(args.screenshot))
    print(json.dumps({"android_sdk": device.run("shell", "getprop", "ro.build.version.sdk"),
        "native_pairing": True, "work_buttons": 12, "media_buttons": 12, "native_actions_dry_run": True,
        "disconnect_disables_buttons": True, "reconnect_enables_buttons": True,
        "home_and_credentials_after_cold_launch": True, "physical_device_test": False}, indent=2))

if __name__ == "__main__": main()
