param([string]$Python = 'python', [string]$Serial = '', [string]$Adb = '', [switch]$Wireless, [string]$LanAddress = '', [switch]$UsbOnly)
$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
$stateDir = Join-Path $env:LOCALAPPDATA 'FrontDeck'
$configPath = Join-Path $projectRoot 'frontdeck\config.local.json'
$serverPath = Join-Path $projectRoot 'frontdeck\server.py'
New-Item -ItemType Directory -Path $stateDir -Force | Out-Null
$wirelessSettingsPath = Join-Path $stateDir 'wireless-settings.json'
if ($Wireless -and $UsbOnly) { throw '-Wireless와 -UsbOnly를 함께 지정할 수 없습니다.' }
$savedWireless = $null
if (Test-Path -LiteralPath $wirelessSettingsPath) { $savedWireless = Get-Content -LiteralPath $wirelessSettingsPath -Raw | ConvertFrom-Json }
$useWireless = $Wireless -or ($savedWireless -and $savedWireless.enabled -and -not $UsbOnly)
if ($useWireless) {
    if (-not $LanAddress) {
        $networkConfig = Get-NetIPConfiguration | Where-Object { $_.IPv4DefaultGateway -and $_.IPv4Address } | Select-Object -First 1
        if (-not $networkConfig) { throw 'PC의 LAN 주소를 -LanAddress로 지정하세요.' }
        $LanAddress = @($networkConfig.IPv4Address.IPAddress)[0]
    }
    & (Join-Path $PSScriptRoot 'Enable-FrontDeckWireless.ps1') -Python $Python -LanAddress $LanAddress
}
if (-not (Test-Path -LiteralPath $configPath)) {
    Copy-Item -LiteralPath (Join-Path $projectRoot 'frontdeck\config.example.json') -Destination $configPath
}
$running = $null
try { $running = Invoke-RestMethod -Uri 'http://127.0.0.1:38765/api/health' -TimeoutSec 2 } catch {}
if ($running -and $running.app -ne 'FrontDeck') { throw '38765 포트를 다른 프로그램이 사용 중입니다.' }
if ($running -and $running.dry_run) { throw '38765 포트에 시험 모드가 켜져 있습니다. 종료 후 다시 실행하세요.' }
if ($running -and (($useWireless -and (-not $running.wireless_enabled -or $running.wireless.host -ne $LanAddress)) -or ($UsbOnly -and $running.wireless_enabled))) { throw '실행 중인 PC 프로그램의 연결 방식이 다릅니다. Stop-FrontDeck.ps1로 종료한 뒤 다시 시작하세요.' }
if (-not $running) {
    $arguments = '"{0}" --config "{1}" --state-dir "{2}"' -f $serverPath, $configPath, $stateDir
    if ($useWireless) { $arguments += ' --lan-host "{0}"' -f $LanAddress }
    $process = Start-Process -FilePath $Python -ArgumentList $arguments -WorkingDirectory $projectRoot -WindowStyle Hidden -PassThru `
        -RedirectStandardOutput (Join-Path $stateDir 'server.log') -RedirectStandardError (Join-Path $stateDir 'server-error.log')
    for ($attempt = 0; $attempt -lt 20; $attempt++) {
        Start-Sleep -Milliseconds 250
        if ($process.HasExited) { throw ('PC 프로그램 실행 실패. 로그: ' + (Join-Path $stateDir 'server-error.log')) }
        try { $running = Invoke-RestMethod -Uri 'http://127.0.0.1:38765/api/health' -TimeoutSec 1; break } catch {}
    }
    if (-not $running) { throw 'PC 프로그램 응답을 확인하지 못했습니다.' }
}
if ($Wireless -or $UsbOnly) { @{enabled=[bool]$useWireless} | ConvertTo-Json | Set-Content -LiteralPath $wirelessSettingsPath -Encoding UTF8 }
$bootstrap = Get-Content -LiteralPath (Join-Path $stateDir 'bootstrap.json') -Raw | ConvertFrom-Json
if ($bootstrap.expires_at -gt [DateTimeOffset]::UtcNow.ToUnixTimeSeconds()) {
    Write-Host ('FrontDeck 실행 중 · 연결 코드: ' + $bootstrap.pin + ' (최대 5분)')
} elseif ($running.wireless_enabled) { Write-Host 'FrontDeck 실행 중 · 무선 연결 설정 창에서 새 연결 코드를 발급하세요.' }
else { Write-Host 'FrontDeck 실행 중 · 새 기기 연결 코드는 프로그램 재시작 후 발급됩니다.' }
if ($running.wireless_enabled) { Write-Host 'Wi-Fi 연결 설정: http://127.0.0.1:38765/connect/' }
if ($Serial) {
    if ($Serial -notmatch '^[A-Za-z0-9_.:\-]+$' -or $Adb.Contains('"')) { throw '기기 식별값 또는 ADB 경로가 올바르지 않습니다.' }
    $watchPath = Join-Path $projectRoot 'tools\watch_deck.py'
    $serialHasher = [System.Security.Cryptography.SHA256]::Create()
    try { $serialDigest = $serialHasher.ComputeHash([System.Text.Encoding]::UTF8.GetBytes($Serial)) }
    finally { $serialHasher.Dispose() }
    $watchId = -join ($serialDigest[0..7] | ForEach-Object { $_.ToString('x2') })
    $watchStatePath = Join-Path $stateDir ('connection-' + $watchId + '.json')
    # Reuse an older single-device watcher too. Starting another panel must not stop it.
    foreach ($existingStatePath in @($watchStatePath, (Join-Path $stateDir 'connection.json'))) {
        if (Test-Path -LiteralPath $existingStatePath) {
            $oldWatch = Get-Content -LiteralPath $existingStatePath -Raw | ConvertFrom-Json
            if ($oldWatch.serial -ne $Serial) { continue }
            $oldProcess = Get-CimInstance Win32_Process -Filter ('ProcessId=' + [int]$oldWatch.pid)
            if ($oldProcess -and $oldProcess.CommandLine -match [regex]::Escape($watchPath)) {
                @{pid=[int]$oldWatch.pid;serial=$Serial} | ConvertTo-Json | Set-Content -LiteralPath $watchStatePath -Encoding UTF8
                Write-Host '선택한 기기의 연결 감시가 이미 실행 중입니다.'
                return
            }
        }
    }
    $watchArgs = '"{0}" --serial "{1}"' -f $watchPath, $Serial
    if ($Adb) { $watchArgs += ' --adb "{0}"' -f $Adb }
    $watchProcess = Start-Process -FilePath $Python -ArgumentList $watchArgs -WorkingDirectory $projectRoot -WindowStyle Hidden -PassThru `
        -RedirectStandardOutput (Join-Path $stateDir ('connection-' + $watchId + '.log')) -RedirectStandardError (Join-Path $stateDir ('connection-' + $watchId + '-error.log'))
    @{pid=$watchProcess.Id;serial=$Serial} | ConvertTo-Json | Set-Content -LiteralPath $watchStatePath -Encoding UTF8
    Write-Host '선택한 기기의 연결 감시를 시작했습니다.'
}
