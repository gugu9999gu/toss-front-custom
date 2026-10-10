# FrontDeck 1.3.1 · 실제 프로그램 아이콘

하단 작업표시줄과 작업·내 버튼 모음의 PC 프로그램 버튼에 Windows에서 가져온 원래 앱 아이콘을 표시합니다. 실행 중인 창은 해당 창의 아이콘을 사용하고, Windows 패키지 앱은 AppsFolder에 등록된 앱 아이콘을 우선합니다. 직접 등록한 실행 파일도 Windows 파일 아이콘을 읽습니다.

기존 버튼 이름·배경 색상·프로그램 실행과 창 전환 기능을 유지합니다. 프로그램 아이콘이 없거나 읽기에 실패하면 기존 선택 아이콘을 표시합니다. 단축키·미디어·웹 버튼은 기존 아이콘을 유지합니다. PNG 자체를 다시 색칠하지 않으므로 프로그램 원래 색상과 투명도를 보존합니다.

아이콘은 48×48 PNG로 변환해 인증된 PC 연결의 설정·창 목록에 포함합니다. 개인 실행 파일 경로와 HWND를 아이콘 데이터로 전달하지 않으며 임의 파일 다운로드 API를 추가하지 않았습니다. 데이터 URI는 PNG와 제한된 길이만 허용하고 외부 이미지 URL·SVG는 사용하지 않습니다.

Windows Shell과 창 아이콘 조회는 별도 스레드에서 처리합니다. 첫 조회 때는 기존 아이콘을 표시하고 준비된 실제 아이콘은 다음 갱신에 반영됩니다. 작업 큐 256개·캐시 512개로 제한하며 중복 추출을 합치고 동일한 이미지의 DOM 재생성을 피합니다. 프로그램 아이콘은 5분, 조회 실패는 30초 후 재시도합니다. 응답이 없는 창의 아이콘 조회는 제한 시간을 사용합니다.

[SHGetFileInfoW](https://learn.microsoft.com/en-us/windows/win32/api/shellapi/nf-shellapi-shgetfileinfow)·[SHParseDisplayName](https://learn.microsoft.com/en-us/windows/win32/api/shlobj_core/nf-shlobj_core-shparsedisplayname)·[WM_GETICON](https://learn.microsoft.com/en-us/windows/win32/winmsg/wm-geticon)으로 읽고, [DrawIconEx](https://learn.microsoft.com/en-us/windows/win32/api/winuser/nf-winuser-drawiconex)와 DIB로 그립니다. 빌려온 창 아이콘을 삭제하지 않으며 소유한 Shell 아이콘과 GDI 객체는 해제합니다. 기존 USB 모드는 외부 Python 패키지가 필요 없습니다.

2026-10-10 첫 번째 실물 기기의 FrontDeck 1.3.1에서 작업 모음의 메모장·계산기·탐색기, 내 버튼의 메모장·Windows 패키지 계산기와 실행 중인 PC 창 아이콘을 확인했습니다. 당시 창 18개의 아이콘을 읽고 기기의 Wi-Fi 화면에서 표시했습니다. 실제 Windows 아이콘의 PNG·투명도·40회 반복 추출의 GDI 자원 유지, 비동기 중복 추출 방지와 공개 응답의 경로·앱 식별값 비노출 검사를 추가했습니다. 이전 USB·무선 테스트를 포함한 35개 검사를 통과했습니다. 개인 PC 창 제목과 기기 식별값을 포함한 화면은 로컬에만 보관합니다.
