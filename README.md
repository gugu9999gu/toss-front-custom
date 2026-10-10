# Toss Front Custom · FrontDeck

토스 프론트 2를 일반 Android 기기 또는 PC용 터치 버튼 패널로 사용하는 작업 기록과 도구입니다.

## 현재 상태

| 대상 | 확인 결과 |
| --- | --- |
| 첫 번째 기기 | 2026-10-10 사용자 승인으로 LineageOS 23.2 / Android 16 설치. 기존 커널·부트로더 유지. FrontDeck 1.2.0 기본 홈·PC 연결·실물 메모장 실행·PC 음량 버튼·재부팅 후 자동 표시와 연결 복원 확인. 음악 앱 설정·최근 기록 4개·즐겨찾기 1개 복원. |
| 화면 표시·홈 | 두 기기 모두 토스 앱이 없는 LineageOS에서 FrontDeck을 기본 홈으로 사용. 전체 화면 패널과 상단 음표 버튼의 음악 앱 실행 지원. Android 설정에서 일반 홈 선택 가능. |
| 공식 YouTube의 이전 결과 | Android 13에서 Google 서명 확인 후 설치했으나 영상이 약 1~2분 후 실패. 현재 두 기기는 Google 서비스 없이 YouTube 웹 앱을 사용함. |
| Bluetooth의 이전 결과 | 첫 번째 기기의 Android 13에서 A2DP 스피커와 내장 스피커 간 실제 출력 전환을 확인했음. LineageOS 전환 후에는 다시 검증해야 함. |
| FrontAudio | 이전 Android 13용 루트 제어 앱. 새 LineageOS에는 설치하지 않았으며 기존 검증 결과는 기록으로 보존함. |
| FrontRecord 1.9.8 | 한 손 핀치 상하 볼륨, 좌우 끝에서 핀치 해제로 이전/다음 곡, 볼륨 제스처 0%에서 일시정지·증가 시 재생. 양손 핀치 상하 재생 제어, 3초 이동선 잔상, 손 주변 파형·입자 변형. 한 손 유지·두 손 탐색 분리, 모델 좌표만 사용하고 위치 급변 재확인. 화면 전체 손 위치·잔상 표시와 시간 기반 보간. 1.9.3에서 위치 튐·깜빡임 감소, 1.9.4 전체 영역, 1.9.5 버튼 표시를 사용자 확인. GPU 렌더링·빠른 손 분석 모드 추가, 체감 버벅임 재확인 중. 설치 해시·데이터 보존 확인. 기존 18종 시각화·테마·예약·색상·기록 유지. YouTube 웹 2.3 연결. 설치·실물 확인 상태는 검증 문서에 기록. |
| 두 번째 기기 | 2026-10-10 독립 UFS 전체 백업 31,977,373,696바이트 검증 후 사용자 승인으로 LineageOS 23.2 / Android 16 설치. 원본 vendor의 지문인식 서비스 대기와 USB 설정을 수정해 실제 초기 설정·홈·Wi-Fi·USB·재부팅 확인. FrontDeck 1.2.0 설치·기본 홈·PC 연결과 실물 버튼의 메모장 실행·PC 음량 제어 확인. 재부팅 후 별도 화면 조작 없이 패널 표시·PC 연결 복원 확인. |
| FrontDeck 1.3.1 | Wi-Fi HTTPS 연결·PC 인증서 확인·재접속과 기존 USB 지원. 작업표시줄·프로그램 버튼에 실제 Windows 앱 아이콘 표시. 첫 번째 기기 업데이트·USB reverse 없는 Wi-Fi 제어·PC/기기 재시작 후 자동 연결 확인. 사용자가 USB 분리 후 볼륨 동작 확인. 이후 두 번째 기기도 이 기능을 포함한 1.4.0으로 업데이트함. 커스텀 버튼·음악 앱 유지. [무선 연결](docs/frontdeck-wireless.md) · [실제 앱 아이콘](docs/frontdeck-app-icons.md). |
| FrontDeck 1.4.0 | Bluetooth 직접 연결·터치패드·키보드·한글 전송 추가. 첫 번째 기기에 설치하고 Wi-Fi를 끈 상태에서 Bluetooth 커서 이동·좌우 클릭·화면 키 입력 확인. PC 재시작 후 재접속과 Wi-Fi 전환 확인. 두 번째 기기도 설치 APK 해시·Wi-Fi 연결·기본 홈·음악 앱·사용자 버튼 보존 확인. 두 번째 기기의 Bluetooth 페어링은 미검증. [Bluetooth·PC 입력](docs/frontdeck-bluetooth-input.md). |
| 두 번째 기기 뮤직플레이어 | FrontRecord 1.9.8·YouTube 웹 2.3 설치·APK 해시 확인. 실제 제목·재생 시간·일시정지·재생·구간 이동, 점 구체의 실제 오디오 반응과 Three.js WebGL·오디오 카메라 동작 확인. FrontDeck 상단 음표 버튼으로 실행. 재부팅 후 설치·권한 유지 확인. |

두 기기는 LineageOS GSI와 각 기기의 기존 커널·하드웨어용 vendor 영역을 사용합니다. 첫 번째 기기의 음악 앱 테마·점 구체·기록 화면과 카메라 연결은 확인했으나 기기 소리 출력·Bluetooth·손 제스처 품질은 OS 전환 후 다시 검증하지 않았습니다. 두 번째 기기의 기기 자체 오디오·카메라·Bluetooth도 미검증이며 WFD/HDCP 서비스 오류가 남아 있습니다.

## 구성

- `docs/lineage-first-device.md`: 첫 번째 기기의 현재 OS·앱 설치와 데이터 복원 결과.
- `docs/first-device.md`: 첫 번째 기기의 이전 Android 13 변경과 검증 결과.
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

두 번째 기기의 복구 원본은 두 번째 기기에서 직접 읽은 별도 백업입니다. 식별값·설정·사용자 데이터를 기기 사이에 복사하지 않습니다. 이전 서명 ABL만 같은 모델 첫 번째 기기의 원본 백업에서 추출했으며, 서명·인증서 체인·서명 메타데이터·XBL을 비교했습니다. 사용자 승인 후 두 번째 기기의 ABL A에 적용했고 전체 읽기 대조와 stock recovery fastbootd 부팅을 확인했습니다. 첫 번째 기기의 ABL은 변경하지 않았습니다.

첫 번째 기기의 이번 최신 전체 원시 백업 시도는 USB 연결 오류로 중단됐습니다. 원본 전체 복구 백업의 해시를 다시 검증했고 현재 펌웨어·super와 별도 ADB 앱 데이터·공유 저장소를 확보했습니다. 최신 userdata 전체 원시 백업이 완료된 것은 아닙니다.

[첫 번째 기기 LineageOS·음악 앱 데이터 복원·FrontDeck 검증](docs/lineage-first-device.md)

[두 번째 기기 LineageOS 이미지·설치 검증 기록](docs/lineage-second-device.md)

## FrontDeck 사용

Google 서비스 없이 동작하는 전체 화면 터치 패널입니다. 상단 **＋**에서 PC 앱 목록·실행 파일·단축키·미디어·웹 버튼을 등록하고, 버튼을 길게 눌러 이름·아이콘·색상과 동작을 수정하거나 삭제합니다. 작업·미디어 기본 버튼 외에 **내 버튼** 모음을 지원합니다. 모음마다 최대 48개를 12개씩 페이지로 표시합니다. 하단 작업표시줄에서 열린 PC 창을 선택하면 최소화를 복원하고 앞으로 가져옵니다. PC 연결이 끊기면 제어 버튼을 비활성화합니다.

[설치·실행·버튼 수정 방법](docs/frontdeck.md) · [Wi-Fi 무선 연결](docs/frontdeck-wireless.md) · [두 번째 기기 진행 상태](docs/second-device.md)

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
