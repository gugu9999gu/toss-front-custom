$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
$stateDir = Join-Path $env:LOCALAPPDATA 'FrontDeck'
$watchPath = Join-Path $projectRoot 'tools\watch_deck.py'
$targets = @(@{state='bootstrap.json';script=(Join-Path $projectRoot 'frontdeck\server.py')})
if (Test-Path -LiteralPath $stateDir) {
    foreach ($connectionState in Get-ChildItem -LiteralPath $stateDir -File -Filter 'connection*.json') {
        if ($connectionState.Name -match '^connection(?:-[a-f0-9]{16})?\.json$') {
            $targets += @{state=$connectionState.Name;script=$watchPath}
        }
    }
}
foreach ($target in $targets) {
    $statePath = Join-Path $stateDir $target.state
    if (Test-Path -LiteralPath $statePath) {
        $saved = Get-Content -LiteralPath $statePath -Raw | ConvertFrom-Json
        $processInfo = Get-CimInstance Win32_Process -Filter ('ProcessId=' + [int]$saved.pid)
        if ($processInfo -and $processInfo.CommandLine -match [regex]::Escape($target.script)) {
            Stop-Process -Id ([int]$saved.pid)
            Write-Host ('종료 완료: ' + (Split-Path -Leaf $target.script))
        } elseif ($processInfo) {
            Write-Host '이 도구로 시작한 프로세스가 아니므로 종료하지 않았습니다. 해당 콘솔에서 Ctrl+C로 종료하세요.'
        }
    }
}
