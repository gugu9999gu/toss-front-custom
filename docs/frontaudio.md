# FrontAudio · 오디오 출력

2026-10-09, 첫 번째 Toss Front 2에 직접 제작한 `dev.tossfront.audio` 앱을 설치했습니다. 내장 스피커와 연결된 Bluetooth A2DP 스피커 사이의 실제 미디어 출력 변경을 확인했고, 사용자가 두 장치에서 테스트 소리를 들었다고 확인했습니다.

## 사용

앱 목록에서 **오디오 출력**을 실행하고 장치를 누릅니다. 현재 출력은 테두리와 ‘현재 사용 중’으로 표시합니다. 앱의 테스트 소리와 함께 음악·영상의 미디어 출력 경로가 바뀝니다. ‘자동 선택으로 되돌리기’는 직접 지정한 미디어 출력 설정을 지웁니다.

화면을 아래로 스크롤하면 ‘테스트 소리’, ‘Bluetooth 설정’, ‘장치 새로고침’이 있습니다. 테스트 소리는 0.5초이며 기존 볼륨 값을 바꾸지 않습니다. Android는 출력 장치별로 볼륨을 기억하므로 전환 시 표시되는 볼륨이 다를 수 있습니다. Bluetooth 설정 버튼은 Android의 연결된 기기 설정을 엽니다.

USB·유선·HDMI 출력도 시스템에 연결된 미디어 출력으로 보고되면 목록에 표시합니다. 이번 실물 시험은 내장 스피커와 Bluetooth A2DP 두 종류에서 진행했습니다. 통화용 SCO와 가상의 전화 출력은 목록에서 제외합니다. 이 앱은 공식 YouTube의 기존 재생 오류를 해결하는 기능은 아닙니다.

## PC 연결과 재부팅

PC에서 한 번 시작한 제어 프로그램은 기기 안에서 실행되므로 PC 케이블을 분리하거나 앱을 종료해도 유지됩니다. **기기 전원을 껐다 켜면 제어 프로그램이 종료되므로 USB로 PC에 연결해 다시 시작해야 합니다.** 재부팅 후 자동 시작은 구현하지 않았습니다. 앱 자체는 계속 설치된 상태이며, 제어가 꺼져 있을 때 필요한 연결 안내를 표시합니다.

저장소 루트에서 Python 3.11 이상과 Android SDK/JDK를 사용합니다. 실제 첫 번째 기기의 ADB 일련번호를 `FIRST_DEVICE_SERIAL` 자리에 넣습니다. 선택한 장치 모델과 root ADB를 확인하고 실행하며 다른 기기를 자동 선택하지 않습니다.

```powershell
python tools/build_android.py --app audio
python tools/start_audio.py --serial FIRST_DEVICE_SERIAL --install
```

재부팅 후에는 설치를 반복하지 않고 다음 명령만 실행합니다.

```powershell
python tools/start_audio.py --serial FIRST_DEVICE_SERIAL
```

## 구현과 검증

Google 서비스나 추가 APK 없이 동작하는 Java 앱입니다. 일반 앱 권한으로 다른 앱의 미디어 출력을 지정할 수 없으므로 root ADB로 앱 APK 안의 `AudioBridge`를 시작합니다. Android 13의 미디어용 AudioProductStrategy에 `setPreferredDeviceForStrategy`를 적용하고 `getDevicesForAttributes`로 실제 정책 경로를 확인합니다. [AOSP의 출력 전략 API 설명](https://source.android.com/docs/core/audio/combined-audio-routing)을 참고했습니다.

제어 프로그램은 기기의 `127.0.0.1:39261`에만 연결을 받으며 앱의 비공개 파일에 저장된 임의 토큰을 검증합니다. 장치 조회, 연결된 출력 선택, 자동 선택 복원만 처리합니다. 명령 실행 기능은 제공하지 않습니다. 시작 도구는 자신의 기존 관리자 프로세스만 확인해 교체합니다. 시스템 파티션 수정, 부팅 스크립트 추가, 네트워크 ADB 설정 변경은 하지 않습니다.

검증 결과는 [frontaudio-verification.json](frontaudio-verification.json)에 기록했습니다. 내장 스피커/BT의 AudioTrack 실제 경로, 사용자 청취 확인, 자동 선택 복원, 앱 재실행 후 경로 유지, 장치별 볼륨 유지, 잘못된 인증·장치·명령의 거절을 확인했습니다. 제어 프로세스는 부모 PID 1로 분리돼 있으며 토큰 파일 권한은 600입니다. 물리적인 PC 케이블 분리 및 기기 재부팅 시험은 이번 앱 검증에서 진행하지 않았습니다.

연결된 실물에서 짧게 출력 경로를 바꾸고 원래 설정으로 복원하는 재검증 도구도 있습니다. 소리를 재생하거나 볼륨을 바꾸지는 않습니다.

```powershell
python tools/audio_smoke.py --serial FIRST_DEVICE_SERIAL
```

서명된 APK는 로컬 `dist/FrontAudio-1.0.0.apk`에 있습니다. 소스와 사용 방법만 Git에 포함하고 앱 서명키, 인증 토큰, 장치 주소, 개인 일련번호와 원시 진단 자료는 제외했습니다.
