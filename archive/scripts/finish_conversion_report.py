from pathlib import Path
from datetime import datetime
import json
import re
import subprocess

root = Path(__file__).resolve().parent.parent
prepared = root / "outputs" / "toss-front2-prepared"
path = prepared / "android-verification.json"
record = json.loads(path.read_text(encoding="utf-8"))
changes = json.loads((prepared / "stock-home-change.json").read_text(encoding="utf-8"))
adb = r"C:\Users\YOUR_USER\AppData\Local\Android\Sdk\platform-tools\adb.exe"
p = subprocess.run([adb, "shell", "pm", "list", "packages", "-e"], capture_output=True, text=True,
                   encoding="utf-8", errors="replace", timeout=10)
assert p.returncode == 0
enabled = {line.partition(":")[2] for line in p.stdout.splitlines()}
assert set(changes["enabled_packages"]).issubset(enabled)
network = json.loads((root / "work" / "connectivity-after.json").read_text(encoding="utf-8"))["dump"]
active = re.search(r"Active default network:\s*(\d+)", network)
agent = next((line for line in network.splitlines() if active and f"NetworkAgentInfo{{network{{{active.group(1)}}}" in line), "")
record.update({"standard_apps_enabled_after_reboot": changes["enabled_packages"],
               "wifi_connected": "WIFI CONNECTED" in agent,
               "internet_validated_by_android": "&VALIDATED&" in agent,
               "configuration_completed_at": datetime.now().astimezone().isoformat(),
               "debug_mode_banner_present": True})
path.write_text(json.dumps(record, indent=2), encoding="utf-8")
status_path = root / "outputs" / "toss-front2-preparation.json"
status = json.loads(status_path.read_text(encoding="utf-8"))
status.update({"updated_at": record["configuration_completed_at"],
               "stock_android_home_configuration_complete": True,
               "android_version": "13", "home_persisted_after_reboot": True,
               "root_adb_after_reboot": True, "toss_auto_start_blocked_after_reboot": True,
               "google_play_store_installed": False, "google_play_services_installed": False,
               "operating_system_replaced": False, "physical_touch_verified": False,
               "next_required_step": "User verifies physical touchscreen operation; software configuration checks passed"})
status_path.write_text(json.dumps(status, indent=2), encoding="utf-8")
text = """# 토스 프론트 2 안드로이드 전환 기록

기존 안드로이드 13 시스템을 활용해 일반 홈 화면으로 전환했습니다. 기본 홈은 Launcher3이며, 토스 본앱·관리·설치·업데이트·기기 설정 앱 9개의 실행 구성요소를 차단했습니다. 앱 전체의 사용 중지 상태가 부팅 때 풀리는 것을 확인했으므로, 설치된 앱에서 추출한 구성요소 317개를 개별 차단했습니다.

재부팅 후 기본 홈, 구성요소 차단, 상태 표시줄과 뒤로·홈·최근 앱 버튼이 유지되는 것을 확인했습니다. 차단 대상 앱의 프로세스는 실행되지 않았습니다. 설정 앱과 앱 목록을 실제 화면 캡처로 확인했고, 기본 파일·갤러리·카메라·WebView 브라우저 앱이 활성화돼 있습니다. 기존 Wi-Fi 연결에는 안드로이드의 인터넷 연결 검증도 통과한 상태가 표시됐습니다.

플레이스토어와 구글 플레이 서비스는 설치돼 있지 않습니다. 운영체제 교체, 앱 제거, 사용자 데이터 초기화는 수행하지 않았습니다. 별도의 APK 앱도 설치하지 않았습니다.

## 사용 방법

- 홈 화면 아래쪽을 위로 쓸어 앱 목록을 엽니다.
- 하단 삼각형은 뒤로, 원은 홈, 사각형은 최근 앱 버튼입니다.
- 네트워크 설정은 앱 목록의 **설정 → 네트워크 및 인터넷**에서 열 수 있습니다.
- USB ADB 연결은 재부팅 후에도 root 권한으로 유지됩니다. PC에서 원하는 APK를 설치할 수 있습니다.

상단의 `DEBUG MODE` 표시는 USB 디버깅을 유지하는 제조사 디버그 설정으로 인해 남아 있습니다. 실제 손가락 터치, 카메라 촬영, 소리 출력은 사용자 확인이 필요합니다. 화면 전환과 버튼 조작 검증은 ADB 입력으로 수행했습니다.

## 원본과 복구 기록

원본 UFS 영역 6개의 백업 합계는 31,977,373,696바이트입니다. 백업 파일과 검증 결과는 `../toss-front2-backup/`에 있으며, 그 폴더의 `백업안내.md`를 참고할 수 있습니다.

- `debug-apply-result.json`: smraw 첫 섹터 34바이트 변경 및 기기 읽기 대조 결과.
- `stock-home-change.json`: 이전 기본 홈과 앱 사용 상태.
- `component-block-plan.json`: 각 구성요소의 원래 사용 상태.
- `component-block-result.json`: 구성요소 차단 결과.
- `navigation-change.json`: 상태 표시줄과 탐색 버튼의 원래 설정.
- `android-verification.json`: 최종 재부팅 검증 결과.
- `android-apps-after-reboot.png`: 재부팅 후 앱 목록 화면.

원복할 때는 위 기록에 따라 홈, 앱과 구성요소의 사용 상태, 시스템 UI 설정을 되돌릴 수 있습니다. 디버그 설정을 원복하는 원본 첫 섹터 파일도 보관했습니다. 실제 복구 쓰기는 아직 시험하지 않았습니다.
"""
(prepared / "전환결과.md").write_text(text, encoding="utf-8")
print(json.dumps({"configuration_complete": True, "wifi_connected": record["wifi_connected"],
                  "internet_validated": record["internet_validated_by_android"],
                  "standard_apps_enabled": len(changes["enabled_packages"]),
                  "physical_touch_confirmed": record["physical_touch_verified"]}))
