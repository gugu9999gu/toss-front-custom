Claude Opus 5.5의 확장 설계 제안입니다. 성능·시험 수치는 목표이며 실제 구현·검증 범위는 [사용 문서](frontrecord.md)에 기록합니다.

# FRONT RECORD 시각화·재생 확장 설계안

## 1. 구조와 상태 경계

| 계층 | 책임 | 금지 |
|---|---|---|
| `YouTubeWebActivity` (웹 앱) | 고정 URL 로드, 고정 JS 프로브, MediaSession 발행 | 기록 저장, 목록 결정 |
| MediaSession 계약 | 검증된 ID, 광고 상태, custom action | 원본 URL·쿼리 전달 |
| Record 앱 | 시각화, 기록/즐겨찾기, 루프·순환 결정 | URL 생성, 웹 DOM 조작 |

**전환을 결정하는 주체는 Record 앱 하나뿐입니다.** 웹 앱은 사건(ended, 광고 시작·종료)만 보고합니다. 이렇게 해야 중복 전환이 구조적으로 생기지 않습니다.

## 2. 오디오 입력 (`AudioSpectrum` 확장)

- Visualizer는 세션 0, `getFft()`와 `getWaveForm()`을 쓰고 captureSize는 512로 합니다. FFT 결과를 로그 간격 32밴드로 묶고, 상승은 빠르게, 감쇠는 느리게 평활합니다.
- 상태는 `NO_PERMISSION`, `INIT_FAILED`, `CONNECTED_SILENT`, `ACTIVE`로 명시합니다.
- 연결에 실패하면 안내 문구와 재시도 버튼을 보여줍니다. 재시도는 1·2·4초 백오프 후 수동 대기로 넘어갑니다.
- 무음이거나 연결되지 않았을 때는 **평평한 정지 형태**를 그립니다. 랜덤 데이터나 가짜 애니메이션은 생성하지 않습니다.

## 3. 시각화 모드 (`VisualStage`)

1. **점 구체**: 30fps
2. **스펙트럼 바**: 60fps
3. **원형 스펙트럼**: 기존 Vinyl 계열, 60fps
4. **파형 오실로스코프**: waveform 사용, 60fps

### 점 구체 구현

- **미리 계산**: 위경도 격자(예: 위도 24 × 경도 44 = 1056점)의 단위벡터 `x,y,z`를 `float[]`에 둡니다. 각 점의 위도 인덱스로 FFT 밴드를 매핑해 `int[] bandOf`에 저장합니다. 프레임마다 객체를 할당하지 않습니다.
- **변형**: `r = R·(1 + a·band[bandOf[i]] + b·band·sin(경도·m + t))`. 진폭 `a`, `b`에는 상한을 둡니다.
- **투영**: 회전 sin/cos는 프레임당 한 번만 계산하고, 원근은 `s = f/(f+z)`로 처리합니다.
- **depth alpha**: 깊이를 4단계 버킷으로 나누고 버킷별 `float[]`에 좌표를 모읍니다. 버킷마다 `canvas.drawPoints()`를 한 번씩 호출하므로 Paint 변경은 프레임당 4회입니다. 저사양 GPU에서 가장 중요한 지점입니다.
- **색**: 검은 배경에 밝은 청록을 기본으로 하고, 5포인트색 테마에서는 해당 색으로 치환합니다.
- **프레임 제어**: `Choreographer`에서 구체 모드는 vsync를 하나 건너뛰고, 나머지 모드는 매 vsync마다 그립니다. 목표 fps는 설계값이며, 실측 전에는 달성했다고 주장하지 않습니다.

### 움직임 줄이기

다음 두 조건 중 하나면 적용합니다.
- 앱 설정에서 켠 경우
- 시스템 `ANIMATOR_DURATION_SCALE`이 0인 경우

적용하면 회전과 물결항을 끄고, 반경 변화만 절반 진폭으로 남깁니다. 페이드 효과는 즉시 전환으로 바꿉니다.

### 크기 슬라이더

`Appearance`에 `visualScale`(0.5~1.5)을 추가하고 `AppearanceSheet`에 SeekBar를 둡니다. 400×640dp 화면 안에서 짧은 변을 기준으로 클램프합니다.

## 4. 패널 UI (`RecordPanel`)

- "FRONT RECORD" 텍스트 뷰를 제거합니다.
- 다음 항목을 각각 boolean 설정으로 숨길 수 있게 합니다: 제목, 재생바, 이전/다음, 재생, 루프. 숨긴 요소는 `GONE` 처리해 레이아웃에서 빠지게 합니다.

### 상단 아이콘 유휴 페이드

대상은 히스토리, •••, × 세 아이콘뿐입니다.

- 상태는 `VISIBLE → (5초 유휴) → FADING → HIDDEN`으로 흐릅니다.
- `dispatchTouchEvent`에서 HIDDEN 상태의 `ACTION_DOWN`이 아이콘 영역에 들어오면, 아이콘만 복귀시키고 **해당 제스처 전체를 소비**합니다. 따라서 첫 탭은 클릭으로 처리되지 않습니다.
- 다른 영역을 터치하면 복귀와 동시에 원래 동작도 수행합니다.
- 오버레이는 NOT_FOCUSABLE을 유지합니다. NOT_TOUCHABLE은 쓰지 않으므로 터치는 그대로 전달됩니다.

## 5. 광고 처리 (웹 앱)

- **고정 JS 상수 1개**를 `evaluateJavascript`로 실행합니다. 재생 중에만 500ms 간격으로 돌립니다. 반환값은 `{ad:bool, skipClicked:bool}`뿐입니다.
- **광고 판정**: 플레이어 컨테이너의 `ad-showing` 클래스, 광고 오버레이 존재 여부.
- **skip 클릭 조건**: 공식 skip 버튼 셀렉터(구·신형 클래스)에 해당하고, 다음을 모두 만족할 때만 `click()` 합니다.
  - `getBoundingClientRect` 크기가 0보다 큼
  - `disabled`가 아님
  - 계산된 opacity와 visibility가 표시 상태
- 클릭은 광고 1건당 1회로 제한합니다.
- seek, 재생속도 변경, 요소 제거, 음소거는 하지 않습니다. 건너뛸 수 없는 광고는 끝날 때까지 그대로 둡니다.
- 자동 클릭은 설정 토글로 두고 기본값은 끄기를 권장합니다. YouTube 약관상 자동화 해석 여지가 있기 때문입니다.
- 셀렉터가 바뀌면 클릭은 실패하고 광고 상태 보고만 유지되는 쪽으로 열화합니다.

## 6. MediaSession 계약

**메타데이터 custom key**
- `dev.tossfront.VIDEO_ID`: `^[A-Za-z0-9_-]{11}$`을 통과한 ID만 넣습니다. 실패하면 키를 생략합니다.
- `dev.tossfront.AD_STATE`: long 0 또는 1.

ID는 `Uri.getQueryParameter("v")`에서만 추출합니다. 나머지 쿼리(`list`, `t`, `si` 등)는 읽은 즉시 버립니다.

**custom action** `dev.tossfront.PLAY_VIDEO` (extras: `videoId`)
- 웹 앱이 ID를 재검증한 뒤 `"https://m.youtube.com/watch?v=" + id`만 `loadUrl` 합니다.
- 다른 스킴이나 호스트를 받는 경로는 두지 않습니다.

**이전/다음**
- 즐겨찾기 순환 모드: Record 앱이 로컬 목록에서 다음 ID를 골라 PLAY_VIDEO를 보냅니다.
- 그 외: `skipToNext/Previous` → 웹 앱이 고정 셀렉터로 웹 목록의 다음/이전 버튼을 클릭합니다.

**로그 정책**
- URL, 제목, ID를 Log로 남기지 않습니다.
- 디버그 빌드에서도 상태 열거값만 기록합니다.

## 7. 전환 상태기 (Record 앱)

상태: `IDLE`, `PLAYING`, `AD`, `PENDING(targetId, deadline)`.

ended 사건은 `(videoId, seq)` 쌍으로 들어오며, 한 쌍은 한 번만 처리합니다. 다음 경우에는 무시합니다.
- 광고 상태이거나 광고 종료 후 1초 이내
- PAUSED 상태
- PENDING 상태

루프 모드별 동작은 다음과 같습니다.

| 모드 | ended 시 동작 |
|---|---|
| OFF | 정지 |
| ONE | 같은 ID로 PLAY_VIDEO |
| LIST | 즐겨찾기 다음 ID로 PLAY_VIDEO |

- PENDING은 새 ID가 메타데이터에 도착하거나 8초 타임아웃이 되면 해제됩니다. 타임아웃이면 재시도하지 않고 정지합니다.
- YouTube 자체 자동재생이 예상 밖의 ID로 이동하면, LIST 모드에서는 요청한 ID로 한 번만 되돌립니다.

## 8. 저장소 (`SessionRepository`)

기록과 즐겨찾기는 `AtomicFile` JSON 두 개로 **분리 저장**합니다.

| 파일 | 내용 | 보존 |
|---|---|---|
| `history.json` | id, 제목, lastPlayedAt | 30일 |
| `favorites.json` | id, 제목, addedAt | 사용자가 삭제할 때까지 |

- **기록 시점**: 광고가 아니고, ID가 유효하며, 같은 ID로 비광고 PLAYING 상태가 10초 이상 이어졌을 때. 같은 ID는 lastPlayedAt만 갱신합니다.
- **기록 삭제**: 앱 시작 시와 기록 추가 시 `now - t > 30일`인 항목을 제거합니다. 미래 시각(시계 역행)은 `now`로 보정합니다.
- **백업 차단**: `allowBackup=false`와 `dataExtractionRules`로 두 파일을 클라우드 백업과 기기 이전에서 제외합니다. 외부 저장소와 네트워크 전송은 없습니다.

## 9. 테스트 체크

**순수 Java 단위테스트** (`tests/RecordMotionTest` 방식)
- [ ] 구체 점 수가 800~1200 범위인지, 단위벡터 정규화, 투영 좌표가 화면 경계 안인지, 깊이에 따라 alpha가 단조인지
- [ ] 무음 입력 시 반경 변형이 0이고, 랜덤 경로가 없는지
- [ ] ID 검증: 10자·12자, `&`, `%`, `/`, 유니코드, `abc&list=` 주입이 모두 거부되는지
- [ ] 기록 삭제 경계: 29일 23시간은 유지, 30일+1ms는 삭제, 미래 시각 보정, 즐겨찾기는 영향 없음
- [ ] 전환 상태기: 중복 ended, 광고 ended, PAUSED 중 ended, ONE/LIST 각각, PENDING 타임아웃
- [ ] 페이드 상태: HIDDEN에서 첫 탭은 복귀만 하고 클릭은 0회

**기기 확인 (측정치를 기록할 뿐 목표 달성을 전제하지 않음)**
- [ ] 권한 거부 → 안내 표시 → 재시도 → 연결 흐름
- [ ] `dumpsys gfxinfo`로 모드별 프레임 시간 측정
- [ ] 실제 광고로 확인: skip 가능 광고는 클릭 1회, skip 불가 광고는 무조작
- [ ] 광고 중에는 기록 추가와 자동다음이 없는지
- [ ] `logcat`에서 `watch?v`, 제목 문자열이 0건인지
- [ ] `bmgr` 백업에 두 파일이 제외되는지
- [ ] 움직임 줄이기에서 회전 정지