# Toss Front Custom · FrontDeck

토스 프론트 2를 일반 Android 기기 또는 PC용 터치 버튼 패널로 사용하는 작업 기록과 도구입니다.

## 현재 상태

| 대상 | 확인 결과 |
| --- | --- |
| 첫 번째 기기 | Android 13 기본 홈으로 전환. 현재 사용자에서 토스 기능 앱 22개 제거, 제조사 유틸리티 4개 사용 중지. 토스 자동 복귀 차단. 재부팅 후 유지 확인. |
| 화면 표시 | DEBUG MODE 문구 숨김. PC 연결을 끊은 화면에서 디버깅 아이콘 부재 확인. |
| 공식 YouTube | Google 서명 확인 후 설치했으나 영상이 약 1~2분 후 실패. 정상 시청은 미완료. Google 구성요소는 반복 오류를 막기 위해 사용 중지. |
| Bluetooth | Bluetooth/LE와 A2DP 송신 서비스 확인. 실제 스피커 페어링·출력 시험 전. |
| 두 번째 기기 | 연결·모델·복구 백업 확인 전. FrontDeck 앱과 Windows 연결 프로그램 준비 중. |

기본 펌웨어의 APK 원본과 하드웨어 프레임워크는 남아 있습니다. 완전한 AOSP/GSI 설치 결과로 보아서는 안 됩니다.

## 구성

- `docs/first-device.md`: 첫 번째 기기 변경과 검증 결과.
- `docs/recovery.md`: EDL 백업·디버그 설정 변경·복구 절차.
- `archive/scripts/`: 당시 사용한 조사 및 일회성 스크립트. 기기 식별 정보와 개인 경로를 치환한 참고 자료입니다.
- `tools/`: 기기 식별과 검증을 먼저 수행하는 재사용 도구.
- `frontdeck/`: Windows 앱 실행·단축키·미디어 제어용 연결 프로그램과 버튼 화면.
- `android/`: Google 서비스가 필요 없는 FrontDeck Android 앱.

## 공개 저장소에 포함하지 않는 자료

원본 UFS 전체 백업, 사용자 데이터, 펌웨어·Firehose 바이너리, 제조사 APK/역공학 산출물, Google APK, ADB 인증키, 앱 서명키와 PC 제어 토큰은 로컬에 보존합니다. 첫 번째 기기 전체 백업은 6개 UFS 영역, 총 31,977,373,696바이트이며 읽기·GPT·SHA-256 검증을 완료했습니다.

두 번째 기기는 첫 번째 기기의 백업을 재사용하지 않습니다. 연결된 기기의 식별 정보와 실제 GPT를 확인하고 해당 기기의 원본을 별도로 백업한 뒤 변경합니다.

## 참고 자료

- [사용자가 제공한 토스 프론트 2 개조 글](https://gall.dcinside.com/mgallery/board/view/?id=sff&no=1722612)
- [bkerler/edl](https://github.com/bkerler/edl): 이번 작업의 읽기 클라이언트 기준 커밋 `01f84bf99a21ba4d378e17444a1dba7f7edb0c49`.
- [Android Platform Tools](https://developer.android.com/tools/releases/platform-tools)

이 저장소는 Elgato의 공식 Stream Deck 소프트웨어나 플러그인과 연동을 보장하는 프로젝트가 아닙니다. 기기의 터치 화면으로 지정한 Windows 동작을 실행하는 자체 구현입니다.
