# 첫 번째 Toss Front 2의 LineageOS 설치

2026-10-10, 사용자가 첫 번째 기기의 초기화를 포함한 설치를 승인한 뒤 LineageOS 23.2 / Android 16과 FrontDeck 1.2.0을 설치했습니다. 기존 음악 앱의 화면 설정·재생 기록·즐겨찾기를 별도로 보관하고 복원했습니다. 이전 Android 13 작업은 [기존 기록](first-device.md)에 남겨 두었습니다.

## 백업과 설치 준비

이 기기의 원본 6개 UFS 영역 전체 백업 31,977,373,696바이트를 확보했으며, 설치 전에 여섯 파일의 SHA-256을 다시 검증했습니다. 최신 상태의 장시간 EDL 읽기는 USB 장치 설명자 오류로 중단됐습니다. 최신 전체 userdata 원시 백업까지 완료했다고 판단하지 않습니다.

중단 전에 현재 부팅·하드웨어 펌웨어와 전체 super 영역은 확보했습니다. 정상 부팅한 Android 13의 ADB에서 현재 커널·vendor_boot·ABL·vbmeta·vendor의 해시가 백업과 일치하는 것을 확인했습니다. 별도로 직접 제작한 음악 앱·YouTube 웹·오디오 앱의 데이터를 TAR로 보관하고, 공유 저장소도 백업했습니다. TAR 내용 전체 읽기와 해시, 음악 앱 설정 XML의 일치를 검증했습니다. 복구 파일·앱 데이터·재생 목록은 공개하지 않습니다.

설치 모드는 정상 ADB에서 `reboot fastboot`로 진입했습니다. 실제 첫 번째 기기의 모델·슬롯 A·잠금 해제·userspace fastboot·업데이트 상태 `none`과 논리 파티션 크기를 확인한 뒤 설치했습니다. 두 번째 기기에서 사용한 부트로더 교체와 EDL BCB 변경은 이 기기에 수행하지 않았습니다.

## 이미지와 변경 범위

[두 번째 기기에서 실제 부팅을 검증한 비공식 이미지](lineage-second-device.md)를 사용했습니다. 배포 출처는 [MisterZtr의 LineageOS GSI 릴리스](https://github.com/MisterZtr/LineageOS_gsi/releases/tag/v2026.05.24-lineage23.2)입니다.

| 항목 | 값 |
| --- | --- |
| 이미지 | `LineageOS-23.2-20260524-VANILLA-EXT4-GSI.img` |
| OS | LineageOS 23.2 / Android 16 / SDK 36 |
| 형식 | ARM64 A/B, RAW EXT4, Google 서비스 없음 |
| 크기 | 2,733,260,800바이트 |
| SHA-256 | `7b9ba62e8b48b6e40432be949ae0858a8971175c79502550e74691222d3889ff` |

이미지 해시는 로컬 파일 검증값입니다. 설치 원칙은 [Android 공식 GSI 문서](https://source.android.com/docs/core/tests/vts/gsi)를 참고하고 실제 대상 기기의 상태를 별도로 검사했습니다.

5GiB super 안에 시스템을 넣기 위해 진행 중인 업데이트가 없는 상태의 잔여 `*-cow` 논리 항목과 OEM `product_a`·`system_ext_a`를 제거했습니다. GSI에는 product·system_ext가 포함돼 있으며 외부 OEM 확장 마운트를 제외합니다. `system_a`를 이미지 크기로 확장하고 20개 sparse 전송으로 설치했습니다. 이 기기의 원본 `vbmeta_a`에서 AVB 검증 비활성화 플래그만 변경했습니다. 사용자 승인대로 userdata·metadata를 초기화했습니다.

첫 번째 기기 자체 vendor 이미지에서 지문인식 HAL 대기와 USB 설정을 수정했습니다. 두 번째 기기의 vendor나 사용자 데이터를 복사하지 않았습니다.

| 파일 | 변경 |
| --- | --- |
| `/vendor/etc/vintf/manifest/qfp-daemon.xml` | 응답하지 않는 지문 HAL 선언 제거 |
| `/vendor/bin/init.qcom.usb.sh` | 초기 USB 조합을 ADB로 설정 |
| `/vendor/build.prop` | 기존 vendor init의 USB configfs 경로 사용 |

세 파일의 내용 441바이트만 바꾸고 파일 크기·권한·EXT4 메타데이터·SELinux 레이블과 나머지 바이트를 보존했습니다. 읽기 전용 `e2fsck -f -n` 검사를 통과했고 vendor의 6개 sparse 전송도 성공했습니다. 기존 ABL A/B·커널·vendor_boot·하드웨어 보정 영역은 변경하지 않았습니다. 화면 확인 중 준비했던 임시 진단 ramdisk는 적용하지 않았습니다.

## 부팅과 앱 데이터 복원

첫 부팅 로고 뒤 검은 화면이 보고됐으나 정상 전원 재연결 후 초기 설정 화면이 나타났습니다. 사용자가 한국어·Wi-Fi 초기 설정과 홈 진입·USB 허용을 완료했습니다. ADB 서버를 다시 시작한 뒤 승인된 연결을 확인했습니다.

실물에서 Android 16·SDK 36·LineageOS 23.2, `sys.boot_completed=1`, 초기 설정 완료와 user 0을 확인했습니다. `ro.adb.secure=1`, `ro.debuggable=0`, `ro.force.debuggable=0`, shell UID 2000이며 진단용 인증 해제 설정은 적용하지 않았습니다. 부트로더 잠금 해제와 AVB 검증 비활성화는 GSI 설치 상태로 유지됩니다.

재부팅 후 검증된 Wi-Fi 연결과 YouTube 도메인의 DNS·ICMP 응답도 확인했습니다. 영상 재생·기기 소리 출력의 새 OS 실물 시험은 별도로 수행하지 않았습니다.

음악 앱의 `appearance.xml`·`library.xml`을 이 기기의 백업에서 그대로 복원했습니다. 복원 시 같은 서명의 임시 앱으로 해당 앱의 설정 저장소에만 접근했으며, 원래 배포 APK를 다시 설치한 뒤 해시와 디버그 접근 종료를 검증했습니다. 화면 테마·점 구체·최근 기록 4개·즐겨찾기 1개를 보존했고 실제 기록 화면도 확인했습니다. 다른 기기의 인증키·계정·사용자 데이터는 복원하지 않았습니다.

FrontRecord 1.9.8·YouTube 웹 2.3·FrontDeck 1.2.0의 설치 파일 SHA-256을 빌드 파일과 대조했습니다. 음악 앱의 미디어 세션·오디오 분석·오버레이·예약 권한을 설정하고, 기존 사용자의 카메라 설정에 맞춰 카메라 권한도 복원했습니다. 카메라 서비스에서 앱의 카메라 연결과 프레임 수신은 확인했지만 손 제스처 정확도·부드러움은 다시 검증하지 않았습니다.

## FrontDeck 확인

FrontDeck을 PC와 인증 연결하고 기본 HOME으로 지정했습니다. 실물 화면의 ‘PC 연결됨’, 앱 실행·미디어·내 버튼 모음과 실행 중인 PC 창 목록을 확인했습니다. 상단 음표 버튼으로 복원된 음악 앱을 열고 Android 홈 동작으로 패널에 돌아왔습니다.

실물 터치 버튼으로 Windows 메모장 실행과 PC 음량 올리기·내리기·음소거를 확인했고 PC의 원래 음량·음소거 상태를 복원했습니다. PC 사용자 버튼 설정은 바꾸지 않았습니다. 첫 번째·두 번째 기기의 USB 재연결 감시를 각각 유지하며 같은 기기로 시작 도구를 다시 실행하면 기존 감시를 재사용합니다. 종료 도구가 이 저장소의 서버·두 감시만 종료하고 재시작 뒤 사용자 버튼 설정이 그대로인 것도 확인했습니다. PC 프로그램 테스트 24개와 PowerShell 구문 검사를 통과했습니다.

Android의 정상 재부팅 뒤 별도 화면 깨우기·홈 시작·포트 연결 명령 없이 부팅 완료, `mWakefulness=Awake`, 기본 HOME의 최상위 FrontDeck 화면과 ‘PC 연결됨’을 확인했습니다. PC의 해당 기기 감시가 ADB reverse를 자동 복원했습니다. 세 앱의 설치 해시·음악 앱 미디어 세션과 오버레이 권한·일반 배포 앱의 디버그 접근 종료도 재확인했습니다. PC에서 연결 프로그램과 감시가 실행 중이어야 자동 연결이 복원됩니다.

기기 자체 소리 출력·Bluetooth 페어링, 손 제스처 품질과 무선 화면 전송은 이번 OS 전환 뒤 다시 검증하지 않았습니다. Android 13에서 확인한 Bluetooth·FrontAudio 결과를 LineageOS의 검증 결과로 대신하지 않습니다. 기존 루트 기반 FrontAudio 제어 앱은 새 OS에 설치하지 않았습니다.
