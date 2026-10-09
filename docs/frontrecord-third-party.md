# FrontRecord 외부 코드

FrontRecord 1.5의 오프라인 3D 장면은 **Three.js 0.160.1**을 포함합니다. npm 공식 패키지의 배포 무결성(SHA-512)을 확인한 뒤 `build/three.module.min.js`와 `LICENSE`만 복사했습니다.

- 패키지 원본: [npm 0.160.1 메타데이터](https://registry.npmjs.org/three/0.160.1)
- 프로젝트: [Three.js](https://github.com/mrdoob/three.js)
- 포함 모듈: [three.module.min.js](../record-android/assets/visual/three.module.min.js)
- **MIT 라이선스 전문**: [THREE-LICENSE.txt](../record-android/assets/visual/THREE-LICENSE.txt)

버전을 고정하고 APK 자산으로 제공하므로 실행 중 CDN 접속은 필요하지 않습니다. 앱의 장면·관절 캐릭터·오디오 동작 코드는 이 프로젝트에서 작성했습니다. 외부 캐릭터 모델·음원·텍스처는 포함하지 않습니다.
