param([Parameter(Mandatory=$true)][string]$Python, [Parameter(Mandatory=$true)][string]$LanAddress, [int]$Port=38766, [switch]$Disable)
$ErrorActionPreference = 'Stop'
$resolvedPython = (Get-Command $Python -CommandType Application).Source
$parsedAddress = $null
if (-not [System.Net.IPAddress]::TryParse($LanAddress, [ref]$parsedAddress) -or $parsedAddress.AddressFamily -ne [System.Net.Sockets.AddressFamily]::InterNetwork) { throw 'PC의 IPv4 LAN 주소를 지정하세요.' }
$addressBytes = $parsedAddress.GetAddressBytes()
if (-not ($addressBytes[0] -eq 10 -or ($addressBytes[0] -eq 172 -and $addressBytes[1] -ge 16 -and $addressBytes[1] -le 31) -or ($addressBytes[0] -eq 192 -and $addressBytes[1] -eq 168)) -or $Port -lt 1024 -or $Port -gt 65535) { throw '사설 LAN 주소와 유효한 포트만 사용할 수 있습니다.' }
$localInterface = @(Get-NetIPAddress -AddressFamily IPv4 -IPAddress $LanAddress -ErrorAction SilentlyContinue)
if ($localInterface.Count -ne 1) { throw '이 PC에 할당된 LAN 주소가 아닙니다.' }
$ruleHasher = [System.Security.Cryptography.SHA256]::Create()
try { $ruleDigest = $ruleHasher.ComputeHash([System.Text.Encoding]::UTF8.GetBytes((Split-Path -Parent $PSScriptRoot) + '|' + $resolvedPython)) }
finally { $ruleHasher.Dispose() }
$ruleName = 'FrontDeck-TLS-' + (-join ($ruleDigest[0..7] | ForEach-Object { $_.ToString('x2') }))
$existing = Get-NetFirewallRule -Name $ruleName -ErrorAction SilentlyContinue
if ($existing -and -not $Disable) {
    $applicationFilter = $existing | Get-NetFirewallApplicationFilter
    $portFilter = $existing | Get-NetFirewallPortFilter
    $addressFilter = $existing | Get-NetFirewallAddressFilter
    $interfaceFilter = $existing | Get-NetFirewallInterfaceFilter
    if ($existing.Enabled -eq 'True' -and $existing.Direction -eq 'Inbound' -and $existing.Action -eq 'Allow' -and $applicationFilter.Program -eq $resolvedPython -and $portFilter.Protocol -eq 'TCP' -and $portFilter.LocalPort -eq [string]$Port -and $addressFilter.LocalAddress -eq $LanAddress -and $addressFilter.RemoteAddress -eq 'LocalSubnet' -and $interfaceFilter.InterfaceAlias -eq $localInterface[0].InterfaceAlias) {
        Write-Host 'FrontDeck 무선 방화벽 규칙이 준비되어 있습니다.'
        return
    }
}
$principal = New-Object Security.Principal.WindowsPrincipal([Security.Principal.WindowsIdentity]::GetCurrent())
if (-not $principal.IsInRole([Security.Principal.WindowsBuiltInRole]::Administrator)) { throw '무선 연결의 최초 방화벽 설정은 관리자 PowerShell에서 실행하세요.' }
if ($existing) {
    if ($existing.DisplayName -ne 'FrontDeck wireless TLS') { throw '다른 프로그램의 방화벽 규칙은 변경하지 않습니다.' }
    Remove-NetFirewallRule -Name $ruleName
}
if ($Disable) { Write-Host 'FrontDeck 무선 방화벽 규칙을 제거했습니다.'; return }
New-NetFirewallRule -Name $ruleName -DisplayName 'FrontDeck wireless TLS' -Direction Inbound -Action Allow -Protocol TCP -LocalPort $Port -Program $resolvedPython -LocalAddress $LanAddress -RemoteAddress LocalSubnet -InterfaceAlias $localInterface[0].InterfaceAlias -Profile Any | Out-Null
Write-Host 'FrontDeck 무선 연결을 해당 LAN 인터페이스·같은 서브넷·TCP 포트로 제한했습니다.'
