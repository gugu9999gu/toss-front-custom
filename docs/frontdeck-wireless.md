# FrontDeck 1.3.1 · Wi-Fi 연결

같은 공유기에 연결된 Android 기기에서 USB 데이터 케이블 없이 PC 앱·단축키·미디어·작업표시줄·커스텀 버튼을 사용합니다. PC는 유선 LAN이어도 됩니다. 기존 USB 모드도 선택할 수 있습니다. 1.4.0에서 추가한 [Bluetooth 직접 연결·터치패드·키보드](frontdeck-bluetooth-input.md)도 사용할 수 있습니다.

## PC에서 시작

저장소 루트에서 실행합니다. 처음 방화벽을 준비할 때는 관리자 PowerShell을 사용합니다. 이미 일치하는 규칙이 있으면 이후 실행에는 관리자 권한이 필요하지 않습니다.

```powershell
python -m pip install -r requirements-wireless.txt
powershell -NoProfile -File tools/Stop-FrontDeck.ps1
powershell -NoProfile -File tools/Start-FrontDeck.ps1 -Wireless
```

Python이 PATH에 없으면 시작 도구에 `-Python`으로 실행 파일 경로를 지정합니다. 네트워크가 여러 개라면 `-LanAddress 192.168.1.10`처럼 **이 PC에 실제 할당된 사설 IPv4 주소**를 지정합니다. 예제 주소를 그대로 사용하지 않습니다.

PC 브라우저에서 [무선 연결 설정](http://127.0.0.1:38765/connect/)을 엽니다. PC 주소·8자리 연결 코드·기기 확인 코드가 표시됩니다. 연결 코드는 5분 후 만료되며 **새 연결 코드**로 다시 발급합니다. 기존에 연결한 기기는 새 코드 발급으로 연결이 해제되지 않습니다.

처음 선택한 Wi-Fi 모드는 PC에 저장됩니다. 이후에는 `Start-FrontDeck.ps1`만 실행해도 무선 연결을 시작합니다. PC 프로그램을 종료하거나 PC를 끄면 제어가 중단됩니다. 자동 시작 등록은 하지 않았습니다.

## 기기에서 연결

1. Android 설정에서 같은 공유기의 Wi-Fi에 연결합니다. FrontDeck 오른쪽 위 설정 → **Wi-Fi 연결**을 선택합니다.
2. PC 설정 페이지의 주소와 8자리 연결 코드를 입력하고 **PC 확인**을 누릅니다. 기본 포트는 38766이며 다른 포트는 `192.168.1.10:38767`처럼 입력할 수 있습니다.
3. 기기와 PC에 표시된 네 묶음의 확인 코드가 같은지 비교하고 **코드가 같아요 · 연결**을 누릅니다.
4. 패널에 **PC 연결됨 · Wi-Fi**가 나타나면 사용합니다. C타입 전원은 유지하고 마이크로 USB 데이터 케이블은 분리해도 됩니다.

PC 주소·확인한 전체 인증서 지문·인증 토큰을 기기 내부에 저장합니다. 재부팅이나 잠깐의 네트워크 끊김 뒤에는 저장된 대상에 다시 접속합니다. PC IP가 바뀌면 새 주소로 위 과정을 다시 진행합니다. 자동 PC 검색과 여러 PC 전환 기능은 아직 없습니다. 공유기의 게스트 Wi-Fi·기기간 통신 차단 설정에서는 연결이 차단될 수 있습니다.

이미 개조한 기기를 USB로 처음 설치하면서 무선 정보를 설정할 수도 있습니다. PC 프로그램을 무선 모드로 시작한 뒤 유효한 연결 코드가 있는 상태에서 실행합니다.

```powershell
python tools/connect_deck.py --serial DEVICE_SERIAL --install --home --wireless
```

`DEVICE_SERIAL`은 실제 설치 대상 하나의 ADB 식별값입니다. 이 옵션은 ADB reverse를 만들지 않고 PC에서 읽은 전체 인증서 지문으로 직접 Wi-Fi 페어링합니다. 초기 설치가 끝나면 Wi-Fi 연결에 ADB가 필요하지 않습니다. 앱 데이터와 음악 앱은 유지합니다.

## 연결 범위와 인증

USB의 HTTP 연결은 루프백 38765에 유지합니다. Wi-Fi의 HTTPS 연결은 지정한 RFC 1918 사설 IPv4 주소의 TCP 38766에서 받습니다. 방화벽 규칙은 해당 Python 실행 파일·PC LAN 주소·네트워크 인터페이스·같은 서브넷으로 제한합니다. Windows 네트워크의 Public/Private 분류와 다른 프로그램의 방화벽 규칙은 바꾸지 않습니다.

서버는 TLS 1.2 이상을 사용합니다. Android 앱은 승인한 인증서의 전체 SHA-256 지문을 각 HTTPS 요청에서 확인하며, 인증서가 바뀌면 명령과 토큰을 보내기 전에 연결을 거절합니다. 최초 PC 확인 단계는 공개 인증서만 읽으며 PIN·토큰·HTTP 명령을 보내지 않습니다. 전역 인증서 검사·호스트 검사 설정을 변경하지 않습니다. [Python SSL](https://docs.python.org/3/library/ssl.html), [cryptography 인증서 생성](https://cryptography.io/en/latest/x509/tutorial/).

연결 코드의 만료·오입력 제한, 토큰 인증, Host/Origin 검사와 등록된 버튼 ID만 실행하는 규칙은 USB·Wi-Fi에 동일하게 적용합니다. 같은 요청을 두 연결로 재전송해도 한 번만 실행합니다. 미리보기·PC 연결 코드 발급 페이지는 PC 루프백에서만 열립니다. 공유기 외부 포트 개방·인터넷 원격 제어는 제공하지 않습니다.

인증서·개인키·연결 토큰·연결 코드는 `%LOCALAPPDATA%\FrontDeck`에 저장하고 Git에서 제외합니다. `wireless-cert.pem`과 `wireless-key.pem`은 함께 보존해야 기존 페어링이 유지됩니다. 한쪽이 누락돼도 다른 인증서로 자동 교체하지 않습니다.

USB 전용으로 되돌릴 때는 먼저 종료하고 `Start-FrontDeck.ps1 -UsbOnly`를 실행합니다. 방화벽 규칙도 제거하려면 사용한 Python과 LAN 주소를 지정해 `Enable-FrontDeckWireless.ps1 -Python python -LanAddress 192.168.1.10 -Disable`을 관리자 PowerShell에서 실행합니다.

## 검증

2026-10-10 기존 기능 24개와 추가 무선 테스트 8개, 총 32개를 통과했습니다. 실제 TLS 페어링·인증 실패·Host/Origin 차단·PC 전용 설정 페이지·연결 코드 갱신·USB/Wi-Fi 중복 요청·신뢰하지 않은 인증서와 평문 접속 차단을 검사했습니다. Android에서 쓰는 Java TLS 구현도 실제 서버와 통신시켜 올바른 지문 허용·변경된 지문 거절·전역 TLS 설정 보존을 검증했습니다.

첫 번째 실물 LineageOS 기기에 1.3.0을 설치하고 설치 APK SHA-256을 대조했습니다. USB reverse 없이 기기 LAN 주소에서 인증된 요청을 수신했으며 실제 화면의 **PC 연결됨 · Wi-Fi**와 PC 볼륨 올리기·내리기·음소거를 확인했습니다. 테스트 뒤 PC 음량을 복원했습니다. 기기 설정의 주소·코드 입력 → 인증서 확인 → 연결 절차와 PC 페이지의 새 코드 발급도 실물에서 확인했습니다.

PC 프로그램을 종료·재시작한 뒤 Wi-Fi 설정·인증서·토큰·사용자 버튼 파일이 그대로였고 기기의 연결이 복원됐습니다. 첫 번째 기기의 정상 재부팅 뒤에는 USB 감시와 reverse 없이 기본 홈의 FrontDeck과 Wi-Fi 연결이 자동 복원됐습니다. 개인 PC 창 제목·IP·기기 식별값·PIN이 있는 실물 증거는 로컬에만 보관합니다.

사용자는 USB 분리 후 PC 볼륨 버튼이 동작한다고 확인했습니다. 이후 요청한 [실제 프로그램 아이콘](frontdeck-app-icons.md)을 포함한 1.3.1을 첫 번째 기기에 업데이트했고 저장된 Wi-Fi 연결과 음악 앱·사용자 버튼을 보존했습니다. 두 번째 기기도 이후 이 기능을 포함한 1.4.0으로 업데이트했고 USB reverse가 없는 Wi-Fi 연결과 기본 홈·음악 앱·사용자 버튼 보존을 확인했습니다. [최신 업데이트 기록](second-device.md).
