from pathlib import Path
import datetime as dt
import json

root = Path(__file__).resolve().parent.parent
out = root / "outputs/toss-front2-prepared"
result_path = out / "toss-removal-result.json"
result = json.loads(result_path.read_text(encoding="utf-8"))
verified = json.loads((out / "toss-removal-verification-after-final-reboot.json").read_text(encoding="utf-8"))
before = json.loads((out / "toss-removal-verification-before-final-reboot.json").read_text(encoding="utf-8"))
assert all(verified["checks"].values())
assert verified["queries"]["boot_id"]["out"] != before["queries"]["boot_id"]["out"]
result["final_verification_pending"] = False
result["final_verification"] = {"time": verified["time"], "checks": verified["checks"],
                                 "boot_id": verified["queries"]["boot_id"]["out"], "new_boot_confirmed": True}
result["vendor_utilities_final_state"] = "disabled-user; verified after a new boot"
result_path.write_text(json.dumps(result, ensure_ascii=False, indent=2), encoding="utf-8")
plan_path = root / "work/official-youtube-plan.json"
plan = json.loads(plan_path.read_text(encoding="utf-8"))
plan.update({"apk_downloaded": True, "official_app_installed": True,
             "official_app_working": False, "status": "waiting_for_user_downloaded_google_components",
             "download_permission_status": "User approved downloads and supplied the YouTube APK manually after browser policy rejection.",
             "google_components_required_by_launch_log": ["com.google.android.gms", "com.android.vending"],
             "google_components_planned": ["com.google.android.gsf", "com.google.android.gms", "com.android.vending"],
             "updated_at": dt.datetime.now().astimezone().isoformat()})
plan_path.write_text(json.dumps(plan, ensure_ascii=False, indent=2), encoding="utf-8")
(out / "전환결과.md").write_text("""# 토스 프론트 2 전환 및 토스 기능 제거

확인일: 2026-10-08

기존 Android 13에서 기본 홈을 Launcher3로 전환했습니다. 토스 본앱, 결제, 단말, 관리, 설치, 업데이트, 기기 설정, 감시 및 Welcome을 포함한 22개 패키지를 현재 사용자(user 0)에서 제거했습니다. 제조사 USB 화면 및 공장 시험 유틸리티 4개는 사용 중지했습니다. 모두 새 부팅 이후에도 유지되었고, 대상 앱 프로세스가 없음을 확인했습니다.

`hide_toss_wartermark=1`로 상단 DEBUG MODE 문구를 숨겼고, `minicat_launcher_enabled=0`으로 토스 자동 복귀 경로를 껐습니다. 새 부팅에서도 일반 Android 홈, 두 설정, 제조사 유틸리티 사용 중지, Wi-Fi 인터넷 연결, Bluetooth ON 및 A2DP 서비스가 유지되었습니다. USB 디버깅 알림은 `persist.adb.notify=0`입니다. Wi-Fi 디버깅 연결 알림은 PC가 연결 중일 때 표시되므로 최종 작업 후 연결을 종료합니다.

## 사용과 작업 범위

- 홈 아래쪽을 위로 쓸면 앱 목록을 엽니다.
- 하단 삼각형은 뒤로, 원은 홈, 사각형은 최근 앱입니다.
- Bluetooth 설정은 설정 → 연결된 기기에서 접근할 수 있습니다.
- 공식 YouTube 설치와 재생 확인 상태는 `유튜브와블루투스확인.md`를 참고합니다.

현재 사용자에서 토스 기능을 제거한 상태이며, 제조사 기본 펌웨어 안의 APK 원본 및 하드웨어 프레임워크는 남아 있습니다. 다른 ROM을 플래시하거나 사용자 데이터를 초기화한 작업은 아닙니다. USB 연결은 Windows 장치 설명자 오류로 불안정하여, 최종 변경과 검증은 동일 기기의 인증된 Wi-Fi ADB 연결로 수행했습니다.

## 검증과 복구

- `toss-removal-result.json`: 제거된 22개와 사용 중지한 4개, 변경 기록, 복구 명령.
- `toss-removal-verification-after-final-reboot.json`: 새로운 부팅 ID, 앱 부재 및 프로세스·설정·Bluetooth 확인.
- `toss-removal-after-final-reboot.png`: 재부팅 후 화면. 작업 중 PC 디버깅 연결 아이콘이 포함될 수 있습니다.
- 원본 백업: `../toss-front2-backup/`의 6개 UFS 영역, 총 31,977,373,696바이트. 원본 파일은 보존했습니다.

제거된 시스템 앱은 `cmd package install-existing --user 0 패키지명`, 사용 중지된 유틸리티는 `pm enable --user 0 패키지명`으로 복구할 수 있습니다. 앞서 개별 차단한 구성요소를 복원해야 할 때는 `component-block-plan.json`의 이전 상태를 함께 적용해야 합니다. 원본 UFS 백업을 실제로 다시 쓰는 복구 시험은 하지 않았습니다.
""", encoding="utf-8")
(out / "유튜브와블루투스확인.md").write_text("""# YouTube 및 Bluetooth 확인

확인일: 2026-10-08

## 공식 YouTube

사용자가 직접 저장한 공식 YouTube APK 21.39.524를 Google 인증서와 APK 서명 검증 도구로 확인하고 설치했습니다. 패키지는 `com.google.android.youtube`, 최소 SDK는 29이며 기기의 Android 13(SDK 33)과 호환됩니다. 파일 SHA-256은 `f47bf195f52c78bbb922ae6c40b882c27436f635eea346be9ad944afaf063d81`로 출처의 게시 값과 일치했습니다.

현재 실행 로그에서 Google Play 서비스 및 Play 스토어 부재가 확인되었고, 앱이 홈으로 돌아옵니다. 공식 앱 설치는 완료됐으나 재생 검증은 완료되지 않았습니다. 사용자가 필요한 Google 구성요소 설치를 허용했으며, 브라우저 자동 다운로드가 다시 거부되어 Google 서비스 프레임워크, Play 서비스 및 Play 스토어 APK의 직접 다운로드를 기다리고 있습니다.

기존 임시 `YouTube 웹` 앱(`local.tossfront.youtubeweb`)은 공식 앱의 정상 재생이 확인되면 제거합니다. 임시 웹 앱에서는 영상 재생, 전체화면과 Android 오디오 재생 상태를 앞서 확인했지만, 공식 앱의 검증 결과와 구분해야 합니다.

## Bluetooth

기기에서 Bluetooth와 Bluetooth LE 기능 선언, A2DP 송신 설정, Bluetooth ON, A2dpService 및 AVRCP 서비스를 확인했습니다. 토스 기능 제거 후 새 부팅에서도 Bluetooth ON 및 A2DP 서비스 실행 상태가 유지되었습니다.

일반 Bluetooth 스피커에 소리를 보내는 기능은 활성화돼 있습니다. 이번 작업에서는 검색·페어링을 하지 않았습니다. 실제 스피커 연결 및 소리 출력은 스피커를 제공받으면 확인할 수 있습니다.

근거: `toss-removal-verification-after-final-reboot.json`, `official-youtube-second-launch.json`.
""", encoding="utf-8")
print("New boot verified; reports updated accurately.")
