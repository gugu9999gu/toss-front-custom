# FrontRecord 외부 코드

FrontRecord의 오프라인 3D 장면은 **Three.js 0.160.1**을 포함합니다. npm 공식 패키지의 배포 무결성(SHA-512)을 확인한 뒤 `build/three.module.min.js`와 `LICENSE`만 복사했습니다.

- 패키지 원본: [npm 0.160.1 메타데이터](https://registry.npmjs.org/three/0.160.1)
- 프로젝트: [Three.js](https://github.com/mrdoob/three.js)
- 포함 모듈: [three.module.min.js](../record-android/assets/visual/three.module.min.js)
- **MIT 라이선스 전문**: [THREE-LICENSE.txt](../record-android/assets/visual/THREE-LICENSE.txt)

버전을 고정하고 APK 자산으로 제공하므로 실행 중 CDN 접속은 필요하지 않습니다. 앱의 장면·관절 캐릭터·오디오 동작 코드는 이 프로젝트에서 작성했습니다. 외부 캐릭터 모델·음원·텍스처는 포함하지 않습니다.

## 카메라 · 손 추적

1.6에는 **MediaPipe Tasks Vision / Core 0.10.32**와 공식 **Hand Landmarker float16 모델 revision 1**을 추가했습니다. [공식 Android 안내](https://developers.google.com/edge/mediapipe/solutions/vision/hand_landmarker/android)에 따라 기기 안에서 최대 두 손의 관절 위치를 분석합니다. GPU 생성·분석은 같은 작업 스레드에서 실행하며, GPU를 사용할 수 없으면 CPU를 사용합니다. 분석 사이의 위치 추적과 시간 순서에 따른 제스처 판정 코드는 이 프로젝트에서 구현했습니다.

공식 Google Maven, Maven Central, Google 모델 저장소에서 내려받을 파일의 버전·URL·SHA-256을 [의존성 목록](../record-android/vision-dependencies.json)에 고정했습니다. `tools/record_vision.py`가 다운로드와 기존 캐시의 해시를 확인하고, 필요한 Java 클래스와 arm64-v8a JNI만 APK에 포함합니다. 모델과 SDK는 빌드할 때 받아 APK에 포함하므로 실행 중 다운로드하지 않습니다. Record 앱에는 INTERNET 권한이 없습니다.

- [MediaPipe Apache 2.0 라이선스](../record-android/assets/gesture/MEDIAPIPE-LICENSE.txt)
- [Protobuf BSD 라이선스](../record-android/assets/gesture/PROTOBUF-LICENSE.txt)
- [Checker Framework 원문 라이선스](../record-android/assets/gesture/CHECKER-FRAMEWORK-LICENSE.txt)
- [의존성·모델 출처 안내](../record-android/assets/gesture/THIRD_PARTY_NOTICES.txt)

Google AAR의 `third_party_licenses.json/txt`도 빌드 시 APK의 `gesture/licenses/`에 그대로 보존합니다. 입력 영상이나 손 관절 좌표를 파일·로그·서버에 보관하지 않습니다. 변환과 위치 추적에 쓰는 짧은 프레임 버퍼는 메모리에서만 사용하고, 카메라를 닫으면 해제합니다.
