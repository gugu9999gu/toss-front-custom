# Toss Front Custom · FrontDeck

토스 프론트 2를 일반 Android 기기 또는 PC용 터치 버튼 패널로 사용하는 작업 기록과 도구입니다.

## 현재 상태

| 대상 | 확인 결과 |
| --- | --- |
| 첫 번째 기기 | Android 13 기본 홈으로 전환. 현재 사용자에서 토스 기능 앱 22개 제거, 제조사 유틸리티 4개 사용 중지. 토스 자동 복귀 차단. 재부팅 후 유지 확인. |
| 화면 표시 | DEBUG MODE 문구 숨김. PC 연결을 끊은 화면에서 디버깅 아이콘 부재 확인. |
| 홈 화면 | Android 기본 Launcher3 홈, 전체 앱 목록, 3버튼 내비게이션 사용 가능. 오디오 출력·설정·파일·계산기 홈 바로가기 배치. |
| 공식 YouTube | Google 서명 확인 후 설치했으나 영상이 약 1~2분 후 실패. 정상 시청은 미완료. Google 구성요소는 반복 오류를 막기 위해 사용 중지. |
| Bluetooth | 첫 번째 기기에 연결된 A2DP 스피커 확인. 내장 스피커·Bluetooth 출력 전환과 실제 소리 청취 확인. |
| FrontAudio | 첫 번째 기기에 ‘오디오 출력’ 앱 설치. 음악·영상 출력 선택, 자동 선택 복원, 테스트 소리 지원. 기기 재부팅 후 PC에서 제어 재시작 필요. |
| FrontRecord 1.9.8 | 한 손 핀치 상하 볼륨, 좌우 끝에서 핀치 해제로 이전/다음 곡, 볼륨 제스처 0%에서 일시정지·증가 시 재생. 양손 핀치 상하 재생 제어, 3초 이동선 잔상, 손 주변 파형·입자 변형. 한 손 유지·두 손 탐색 분리, 모델 좌표만 사용하고 위치 급변 재확인. 화면 전체 손 위치·잔상 표시와 시간 기반 보간. 1.9.3에서 위치 튐·깜빡임 감소, 1.9.4 전체 영역, 1.9.5 버튼 표시를 사용자 확인. GPU 렌더링·빠른 손 분석 모드 추가, 체감 버벅임 재확인 중. 설치 해시·데이터 보존 확인. 기존 18종 시각화·테마·예약·색상·기록 유지. YouTube 웹 2.3 연결. 설치·실물 확인 상태는 검증 문서에 기록. |
| 두 번째 기기 | 2026-10-10 독립 UFS 전체 백업 31,977,373,696바이트 검증 후 사용자 승인으로 LineageOS 23.2 / Android 16 설치. 원본 vendor의 지문인식 서비스 대기와 USB 설정을 수정해 실제 초기 설정·홈·Wi-Fi·USB·재부팅 확인. FrontDeck 1.2.0 설치·기본 홈·PC 연결과 실물 버튼의 메모장 실행·PC 음량 제어 확인. 재부팅 후 별도 화면 조작 없이 패널 표시·PC 연결 복원 확인. |
| FrontDeck 1.2.0 | 두 번째 기기에 설치. 기기에서 커스텀 앱·단축키·미디어·웹 버튼 추가·수정·삭제, 이름·아이콘·색상 설정과 모음당 48개/12개씩 페이지 표시 지원. 실행 중인 PC 창의 작업표시줄과 최소화 복원·창 전환을 실물에서 확인. PC 프로그램 재시작 후 설정 유지 확인. |
| 두 번째 기기 뮤직플레이어 | FrontRecord 1.9.8·YouTube 웹 2.3 설치·APK 해시 확인. 실제 제목·재생 시간·일시정지·재생·구간 이동, 점 구체의 실제 오디오 반응과 Three.js WebGL·오디오 카메라 동작 확인. FrontDeck 상단 음표 버튼으로 실행. 재부팅 후 설치·권한 유지 확인. |

첫 번째 기기에는 기본 펌웨어의 APK 원본과 하드웨어 프레임워크가 남아 있습니다. 두 번째 기기는 LineageOS GSI를 설치했으며 기존 커널과 하드웨어용 vendor 영역을 사용합니다. 기기 자체 오디오·카메라·Bluetooth는 아직 검증 전이고 WFD/HDCP 서비스 오류가 남아 있습니다.

## 구성

- `docs/first-device.md`: 첫 번째 기기 변경과 검증 결과.
- `docs/recovery.md`: EDL 백업·디버그 설정 변경·복구 절차.
- `archive/scripts/`: 당시 사용한 조사 및 일회성 스크립트. 기기 식별 정보와 개인 경로를 치환한 참고 자료입니다.
- `tools/`: APK 빌드, 명시적으로 선택한 기기의 ADB 확인·앱 설치·PC 연결 도구.
- `frontdeck/`: Windows 앱 실행·단축키·미디어 제어용 연결 프로그램과 버튼 화면.
- `android/`: Google 서비스가 필요 없는 FrontDeck Android 앱.
- `audio-android/`: 첫 번째 기기의 오디오 출력 선택 앱과 기기 내부 제어 프로그램.
- `record-android/`: YouTube 웹 미디어 세션과 연결하는 음악 시각화·기록 앱.
- `youtube-web-android/`: 재생 정보·종료 사건 전달과 실제 광고 건너뛰기 버튼 자동 누름을 지원하는 YouTube 웹 2.3 소스.

[FrontAudio 사용·설치·재부팅 후 제어 시작 방법](docs/frontaudio.md)

[FrontRecord 사용·빌드·실물 검증 결과](docs/frontrecord.md)

[Claude Opus 5.5 기본 설계](docs/frontrecord-design-opus.md) · [구체·기록·즐겨찾기 확장 설계](docs/frontrecord-expansion-opus.md)

[Claude Opus 5.5의 게임·무대·필터 직접 제작](docs/frontrecord-rebuild-opus.md)

## 공개 저장소에 포함하지 않는 자료

원본 UFS 전체 백업, 사용자 데이터, 펌웨어·Firehose 바이너리, 제조사 APK/역공학 산출물, Google APK, ADB 인증키, 앱 서명키와 PC 제어 토큰은 로컬에 보존합니다. 두 기기 각각 독립적으로 6개 UFS 영역, 총 31,977,373,696바이트의 전체 백업을 완료했고 읽기·GPT·SHA-256 검증을 마쳤습니다.

두 번째 기기의 복구 원본은 두 번째 기기에서 직접 읽은 별도 백업입니다. 식별값·설정·사용자 데이터를 기기 사이에 복사하지 않습니다. 이전 서명 ABL만 같은 모델 첫 번째 기기의 원본 백업에서 추출했으며, 서명·인증서 체인·서명 메타데이터·XBL을 비교했습니다. 사용자 승인 후 두 번째 기기의 ABL A에 적용했고 전체 읽기 대조와 stock recovery fastbootd 부팅을 확인했습니다. 첫 번째 기기는 종료 상태를 유지합니다.

[두 번째 기기 LineageOS 이미지·설치 검증 기록](docs/lineage-second-device.md)

## FrontDeck 사용

Google 서비스 없이 동작하는 전체 화면 터치 패널입니다. 상단 **＋**에서 PC 앱 목록·실행 파일·단축키·미디어·웹 버튼을 등록하고, 버튼을 길게 눌러 이름·아이콘·색상과 동작을 수정하거나 삭제합니다. 작업·미디어 기본 버튼 외에 **내 버튼** 모음을 지원합니다. 모음마다 최대 48개를 12개씩 페이지로 표시합니다. 하단 작업표시줄에서 열린 PC 창을 선택하면 최소화를 복원하고 앞으로 가져옵니다. PC 연결이 끊기면 제어 버튼을 비활성화합니다.

[설치·실행·버튼 수정 방법](docs/frontdeck.md) · [두 번째 기기 진행 상태](docs/second-device.md)

![두 번째 실물 기기의 LineageOS에서 재부팅 후 자동 실행·PC 연결된 FrontDeck 1.1.0.](docs/images/lineage-frontdeck-device.png)

위 사진은 1.1.0의 실물 재부팅 검증 화면입니다. 1.2.0의 화면과 새 기능 검증은 [사용법](docs/frontdeck.md)에 기록했습니다. 실제 PC 창 제목과 개인 앱 경로가 담긴 새 실물 사진·설정 파일은 공개하지 않습니다.

Windows PC에 Python 3.11 이상이 있으면 저장소 루트에서 다음 명령으로 실제 PC 프로그램을 실행할 수 있습니다.

```powershell
python -m frontdeck.server
```

화면만 확인하는 경우에는 `--dry-run`을 추가하고 `http://127.0.0.1:38765/preview/`를 엽니다. 브라우저 미리보기에서는 실제 PC 동작이 실행되지 않습니다. 실물 연결 절차는 기기별 백업·디버그 설정·토스 자동 복귀 제거를 마친 뒤 진행합니다.

## 참고 자료

- [사용자가 제공한 토스 프론트 2 개조 글](https://gall.dcinside.com/mgallery/board/view/?id=sff&no=1722612)
- [bkerler/edl](https://github.com/bkerler/edl): 이번 작업의 읽기 클라이언트 기준 커밋 `01f84bf99a21ba4d378e17444a1dba7f7edb0c49`.
- [Android Platform Tools](https://developer.android.com/tools/releases/platform-tools)

이 저장소는 Elgato의 공식 Stream Deck 소프트웨어나 플러그인과 연동을 보장하는 프로젝트가 아닙니다. 기기의 터치 화면으로 지정한 Windows 동작을 실행하는 자체 구현입니다.
