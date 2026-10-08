from pathlib import Path
import datetime as dt
import json

root = Path(__file__).resolve().parent.parent
out = root / "outputs/toss-front2-prepared"
def read(name):
    return json.loads((out / name).read_text(encoding="utf-8"))
def write(name, value):
    (out / name).write_text(json.dumps(value, ensure_ascii=False, indent=2), encoding="utf-8")

verified = read("toss-removal-verification-final-stable-reboot.json")
reboot = read("final-device-reboot.json")
disconnected = read("final-disconnected-verification.json")
assert all(verified["checks"].values())
assert reboot["before_boot_id"] != reboot["after_boot_id"]
assert reboot["google_disable_persisted"] and reboot["temporary_mounts_removed"]
assert disconnected["wireless_debug_notification_absent"] and disconnected["installed_youtube"]
assert all(n == 0 for n in disconnected["google_processes"].values())
assert verified["queries"]["boot_id"]["out"] == reboot["after_boot_id"] == disconnected["device_capture_state"][1]
now = dt.datetime.now().astimezone().isoformat()

removal = read("toss-removal-result.json")
removal["final_verification_pending"] = False
removal["final_verification"] = {"time": verified["time"], "checks": verified["checks"],
    "boot_id": reboot["after_boot_id"], "new_boot_confirmed": True,
    "file": "toss-removal-verification-final-stable-reboot.json"}
removal["final_screen_without_debug_indicators"] = disconnected["screen"]
removal["wireless_debugging_transport_disconnected"] = True
removal["watermark_reapplied_after_google_test"] = True
write("toss-removal-result.json", removal)

cleanup = read("google-system-test-cleanup.json")
cleanup["first_reboot_disabled_state_persisted"] = False
cleanup["disabled_state_reapplied_after_first_reboot"] = True
cleanup["disabled_state_persisted"] = True
cleanup["final_verification"] = reboot
cleanup["final_verified_at"] = now
write("google-system-test-cleanup.json", cleanup)
test = read("google-system-test.json")
test["overlay_was_mounted"] = True
test["mounted"] = False
test["cleaned_up"] = True
test["final_verification_file"] = "final-device-reboot.json"
write("google-system-test.json", test)

google = read("official-google-installation.json")
google["final_state"] = {"verified_at": now, "installed": True, "disabled_for_user_0": True,
    "reason": "Ordinary Play Store installation lacks privileged permissions; official YouTube also failed after temporary system integration.",
    "temporary_system_integration_removed": True, "official_youtube_playback_working": False,
    "final_verification_file": "final-disconnected-verification.json"}
write("official-google-installation.json", google)
plan_path = root / "work/official-youtube-plan.json"
plan = json.loads(plan_path.read_text(encoding="utf-8"))
plan.update({"status": "official_app_installed_playback_unresolved", "updated_at": now,
    "official_app_installed": True, "official_app_working": False,
    "google_components_downloaded": True, "google_components_signature_verified": True,
    "google_components_installed": True, "google_components_disabled": True,
    "temporary_system_test_removed": True, "speaker_pairing_tested": False,
    "bluetooth_audio_profile_verified": True,
    "download_permission_status": "All four APK files were manually supplied by the user and verified before installation.",
    "playback_findings": ["Big Buck Bunny failed at about 1:40 in the system test.",
        "Fresh Google app state did not resolve playback; Big Buck Bunny stalled at about 1:01.",
        "Sintel failed at about 1:53; 360p was selected, but sustained playback at 360p was not established."],
    "play_protect_status": "Could not verify certification; this is not proof of a specific YouTube error cause.",
    "remaining_work": "Identify and resolve the official YouTube playback failure, then verify a persistent working Google environment."})
plan_path.write_text(json.dumps(plan, ensure_ascii=False, indent=2), encoding="utf-8")

(out / "전환결과.md").write_text("""# 토스 프론트 2 작업 결과

최종 확인: 2026-10-08 22:57 KST

토스 앱, 결제, 단말, 관리, 설치, 업데이트, 감시 및 Welcome을 포함한 22개 패키지를 현재 사용자(user 0)에서 제거했습니다. 제조사 USB 화면·공장 시험 유틸리티 4개는 사용 중지했습니다. 마지막 정상 재부팅에서도 대상 앱이 실행되지 않고, 기본 홈이 Android Launcher3로 유지되는 것을 확인했습니다.

`minicat_launcher_enabled=0`으로 토스 자동 복귀를 차단했고 `hide_toss_wartermark=1`로 DEBUG MODE 문구를 숨겼습니다. USB 디버깅 알림 설정은 `persist.adb.notify=0`입니다. PC의 Wi-Fi ADB 연결을 종료한 상태에서 DEBUG MODE 문구와 디버깅 아이콘이 모두 없는 화면을 촬영했습니다. 개발자 옵션과 Wi-Fi 디버깅 설정 자체는 유지됩니다.

![PC 연결 종료 후 최종 화면](front2-final-no-debug.png)

## 공식 YouTube 및 Bluetooth

공식 YouTube와 필요한 Google APK 3개는 서명을 확인해 설치했습니다. 다만 공식 앱의 영상 재생이 약 1~2분 뒤 실패해 정상 사용 상태까지 완료하지 못했습니다. 시험용 시스템 구성을 제거하고 Google 구성요소 3개는 사용 중지했습니다. 상세 내용은 [YouTube 및 Bluetooth 결과](유튜브와블루투스확인.md)를 참고하세요.

Bluetooth ON, Bluetooth/LE 기능 선언과 A2DP 송신 서비스가 마지막 재부팅 후에도 유지됐습니다. 실제 스피커의 페어링·소리 출력 시험은 아직 하지 않았습니다.

## 사용과 변경 범위

- 홈 아래쪽을 위로 쓸면 앱 목록을 엽니다.
- 하단 삼각형은 뒤로, 원은 홈, 사각형은 최근 앱입니다.
- Bluetooth 설정은 설정 → 연결된 기기에서 접근할 수 있습니다.

토스의 실행 기능을 현재 사용자에서 제거한 상태입니다. 제조사 기본 펌웨어의 APK 원본과 하드웨어 프레임워크는 남아 있습니다. 다른 Android ROM이나 부팅 이미지를 플래시한 작업은 아닙니다. USB는 Windows 장치 설명자 오류로 불안정하여, 최종 변경과 확인은 인증된 Wi-Fi ADB 연결로 진행했습니다.

## 검증 및 복구 기록

- `toss-removal-result.json`: 제거한 22개, 사용 중지한 4개 및 복구 명령.
- `toss-removal-verification-final-stable-reboot.json`: 최종 부팅의 앱·설정·Bluetooth·인터넷 검증.
- `final-device-reboot.json`: 정상 종료 방식의 재부팅 및 Google 사용 중지 상태 유지 확인.
- `final-disconnected-verification.json`: PC 연결 종료 시 디버깅 알림 부재와 최종 화면.
- `google-system-test-cleanup.json`: 임시 마운트·스테이징 파일 제거 기록.
- 원본 백업: `../toss-front2-backup/`의 6개 UFS 영역, 총 31,977,373,696바이트. 보존했으며 실제 복원 쓰기는 시험하지 않았습니다.

제거한 시스템 앱은 `cmd package install-existing --user 0 패키지명`, 사용 중지한 제조사 유틸리티는 `pm enable --user 0 패키지명`으로 복구할 수 있습니다. 앞서 차단한 개별 구성요소는 `component-block-plan.json`의 이전 상태를 함께 확인해야 합니다.
""", encoding="utf-8")

(out / "유튜브와블루투스확인.md").write_text("""# 공식 YouTube 및 Bluetooth 결과

최종 확인: 2026-10-08 22:57 KST

## 공식 YouTube: 설치 완료, 정상 재생 미완료

사용자가 직접 다운로드한 공식 YouTube 21.39.524와 Google 서비스 프레임워크 13, Play 서비스 26.37.37, Play 스토어 53.4.34를 APK 서명·Google 인증서·파일 SHA-256으로 검증하고 설치했습니다. 패키지·SDK·기기 아키텍처도 확인했습니다. 설치 성공은 실제 서비스 동작을 보장하지 않으며, 아래 재생 검증에서 오류가 남았습니다.

일반 앱 설치 상태에서는 Google 서비스의 시스템 권한 부족으로 예외가 발생했습니다. 펌웨어 블록을 쓰지 않는 RAM 기반 임시 시스템 구성을 시험해 `MANAGE_USERS`, `READ_DEVICE_CONFIG` 등의 권한 오류를 해소했고, YouTube의 홈·검색·영상 재생 시작과 Android 오디오 재생 상태를 확인했습니다.

그러나 Big Buck Bunny 영상은 약 1분 40초 지점에서 재생 오류가 발생했습니다. 새로 설치한 Google 앱들의 데이터를 백업한 뒤 초기화해도 재생이 약 1분 1초에서 멈췄습니다. 다른 공개 영상 Sintel도 약 1분 53초에서 재생 오류가 발생했습니다. 360p를 선택하는 시험 역시 안정적인 재생을 확보하지 못했습니다. 낮은 화질의 영상 데이터가 실제로 계속 재생됐다는 근거는 없습니다.

Play 스토어의 기기 인증 화면은 ‘인증 상태를 확인할 수 없음’, 재확인 화면은 ‘기기 인증 문제를 해결할 수 없음’을 표시했습니다. 인증 여부 자체와 YouTube 재생 오류의 직접 원인은 확정하지 못했습니다. Google 계정은 등록하지 않았습니다. Google도 [인증 확인 및 문제 해결 안내](https://support.google.com/android/answer/7165974?hl=ko)에서 인증되지 않은 기기의 Google 앱 동작을 보장하지 않는다고 설명합니다.

## 최종 기기 상태

- 공식 YouTube는 설치된 상태입니다. 현재 정상 시청용으로 사용할 수 있다고 안내할 수 없습니다.
- Google 구성요소 3개는 반복 권한 오류를 막기 위해 사용 중지했습니다. 마지막 정상 재부팅에서도 유지됐고 실행 프로세스가 없었습니다.
- RAM 기반 임시 시스템 마운트와 시험용 스테이징 파일은 제거했습니다. 원본 시스템 파티션과 부팅 이미지는 변경하지 않았습니다.
- 직접 다운로드한 APK와 새 Google 앱 데이터 백업 `new-google-data-before-reset.tar`는 PC에 보존했습니다.
- 앞서 설치한 임시 `YouTube 웹` 앱은 유지했습니다. 공식 앱의 정상 재생이 확인되면 제거할 대상으로 기록했습니다. 웹 앱의 이전 재생 결과로 공식 앱의 성공을 대신 판단하지 않았습니다.

남은 작업은 공식 앱 재생 오류의 원인을 해결하고, 재부팅 후에도 정상 작동하는 Google 환경을 검증하는 것입니다. 원인이 확정되지 않은 상태에서 부팅 이미지나 ROM을 추가로 플래시하지 않았습니다.

근거: `official-google-installation.json`, `official-youtube-system-playback-progress.json`, `official-youtube-fresh-playback-progress.json`, `official-youtube-alternate-360p-start.json`, `official-youtube-play-protect-check-result.json`, `google-system-test-cleanup.json`, `final-device-reboot.json`.

## Bluetooth: 기능 확인, 스피커 연결 시험 전

Bluetooth와 Bluetooth LE 기능 선언, A2DP 송신 설정, Bluetooth ON, A2dpService 및 AVRCP 서비스를 확인했습니다. 토스 기능 제거 후 마지막 재부팅에서도 Bluetooth ON과 A2DP 서비스 실행 상태가 유지됐습니다.

일반 Bluetooth 스피커로 오디오를 보내는 기능은 활성화돼 있습니다. 사용자 요청대로 기기 검색·페어링은 하지 않았습니다. 실제 스피커와의 호환성과 소리 출력은 스피커를 제공받은 후 확인해야 합니다.

근거: `toss-removal-verification-final-stable-reboot.json`.
""", encoding="utf-8")
report_path = out / "전환결과.md"
contents = report_path.read_text(encoding="utf-8")
contents = contents.replace(
    "![PC 연결 종료 후 최종 화면](front2-final-no-debug.png)",
    "![PC 연결 종료 후 최종 화면](" + (out / "front2-final-no-debug.png").as_posix() + ")")
contents = contents.replace(
    "[YouTube 및 Bluetooth 결과](유튜브와블루투스확인.md)",
    "[YouTube 및 Bluetooth 결과](" + (out / "유튜브와블루투스확인.md").as_posix() + ")")
report_path.write_text(contents, encoding="utf-8")
print("Final reports updated: Toss removal verified, official YouTube playback unresolved, Google test cleaned up, Bluetooth capability verified.")
