"""Install/pair FrontDeck on an explicitly selected, already converted Front 2."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import time
from urllib.request import urlopen
from device import Device

ROOT = Path(__file__).resolve().parents[1]
PACKAGE = "dev.tossfront.deck"

def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--serial", required=True); parser.add_argument("--adb")
    parser.add_argument("--install", action="store_true")
    parser.add_argument("--home", action="store_true", help="FrontDeck을 기본 홈으로 설정합니다.")
    parser.add_argument("--state-dir", type=Path, default=Path(os.environ.get("LOCALAPPDATA", Path.home() / ".local/share")) / "FrontDeck")
    args = parser.parse_args()
    device = Device(args.serial, args.adb); device.inspect()
    with urlopen("http://127.0.0.1:38765/api/health", timeout=3) as response: health = json.load(response)
    if health.get("app") != "FrontDeck" or health.get("dry_run") is not False:
        raise RuntimeError("실제 동작 모드의 FrontDeck PC 프로그램을 먼저 실행하세요.")
    bootstrap = json.loads((args.state_dir / "bootstrap.json").read_text(encoding="utf-8"))
    if bootstrap["port"] != 38765 or bootstrap["expires_at"] < time.time() or not str(bootstrap["pin"]).isdigit() or len(str(bootstrap["pin"])) != 8:
        raise RuntimeError("연결 코드가 만료됐습니다. PC 프로그램을 재시작하세요.")
    if args.home:
        old = json.loads((ROOT / "docs/first-device-results.json").read_text(encoding="utf-8"))["removed_packages"]
        installed = {line.removeprefix("package:") for line in device.run("shell", "pm", "list", "packages", "--user", "0").splitlines()}
        if installed.intersection(old):
            raise RuntimeError("토스 앱이 남아 있습니다. 기기별 백업과 토스 자동 복귀 제거를 먼저 완료하세요.")
    if args.install:
        apk = ROOT / "dist/FrontDeck-1.0.0.apk"
        report = json.loads((ROOT / "build/android/verification.json").read_text(encoding="utf-8"))
        if report["package"] != PACKAGE or hashlib.sha256(apk.read_bytes()).hexdigest() != report["sha256"]:
            raise RuntimeError("빌드 검증 결과와 APK가 다릅니다. 다시 빌드하세요.")
        output = device.run("install", "-r", str(apk), timeout=90)
        if "Success" not in output: raise RuntimeError("APK 설치를 확인하지 못했습니다.")
    if not device.run("shell", "pm", "path", "--user", "0", PACKAGE).startswith("package:"):
        raise RuntimeError("FrontDeck APK가 설치돼 있지 않습니다. --install을 사용하세요.")
    device.run("reverse", "tcp:38765", "tcp:38765")
    if "tcp:38765 tcp:38765" not in device.run("reverse", "--list"):
        raise RuntimeError("PC 통신 포트를 확인하지 못했습니다.")
    device.run("shell", "am", "start", "-n", PACKAGE + "/.DeckActivity", "--es", "pairing_code", str(bootstrap["pin"]))
    if args.home:
        device.run("shell", "cmd", "package", "set-home-activity", "--user", "0", PACKAGE + "/.DeckActivity")
        home = device.run("shell", "cmd", "package", "resolve-activity", "--brief", "--user", "0", "-a", "android.intent.action.MAIN", "-c", "android.intent.category.HOME")
        if PACKAGE not in home: raise RuntimeError("기본 홈 설정을 확인하지 못했습니다.")
    print("설치·연결 명령 완료. 기기 화면에 ‘PC 연결됨’이 표시되는지 확인하세요.")
    if args.home: print("FrontDeck 기본 홈 확인 완료. 재부팅 후 유지 여부는 기기에서 추가 확인해야 합니다.")

if __name__ == "__main__":
    try: main()
    except Exception as error: raise SystemExit(str(error))
