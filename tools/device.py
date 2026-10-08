"""Explicit-target ADB inspection. No default-device commands or storage writes."""
import json
import os
from pathlib import Path
import shutil
import subprocess

def find_adb(value=None):
    options = [value, shutil.which("adb"),
               str(Path(os.environ.get("ANDROID_HOME", Path.home() / "AppData/Local/Android/Sdk")) / "platform-tools/adb.exe")]
    for option in options:
        if option and Path(option).is_file(): return str(Path(option).resolve())
    raise RuntimeError("Android Platform Tools의 adb 경로를 --adb로 지정하세요.")

class Device:
    def __init__(self, serial, adb=None):
        if not serial or serial.startswith("-") or any(c.isspace() for c in serial):
            raise ValueError("명시적인 ADB serial이 필요합니다.")
        self.serial, self.adb = serial, find_adb(adb)
    def run(self, *args, timeout=20):
        result = subprocess.run([self.adb, "-s", self.serial, *args], capture_output=True,
            text=True, encoding="utf-8", errors="replace", timeout=timeout)
        if result.returncode: raise RuntimeError((result.stderr or result.stdout).strip())
        return result.stdout.strip()
    def inspect(self):
        if self.run("get-state") != "device": raise RuntimeError("ADB 기기가 online 상태가 아닙니다.")
        prop = lambda name: self.run("shell", "getprop", name)
        info = {"model": prop("ro.product.model"), "sdk": prop("ro.build.version.sdk"),
                "android": prop("ro.build.version.release"), "build": prop("ro.build.display.id"),
                "hardware_serial": prop("ro.serialno"), "boot_completed": prop("sys.boot_completed"),
                "user": self.run("shell", "am", "get-current-user")}
        if info["model"] != "toss_front2" or info["user"] != "0" or info["boot_completed"] != "1":
            raise RuntimeError("부팅이 완료된 Toss Front 2의 user 0만 지원합니다.")
        return info

def main():
    import argparse
    parser = argparse.ArgumentParser(description="기기를 읽기 전용으로 확인합니다.")
    parser.add_argument("--serial", required=True); parser.add_argument("--adb")
    parser.add_argument("--output", type=Path)
    args = parser.parse_args()
    info = Device(args.serial, args.adb).inspect()
    if args.output:
        args.output.parent.mkdir(parents=True, exist_ok=True)
        args.output.write_text(json.dumps(info, ensure_ascii=False, indent=2), encoding="utf-8")
    print(json.dumps({k: v for k, v in info.items() if k != "hardware_serial"}, ensure_ascii=False, indent=2))

if __name__ == "__main__": main()
