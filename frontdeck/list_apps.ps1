$ErrorActionPreference = 'Stop'
[Console]::OutputEncoding = [System.Text.UTF8Encoding]::new($false)
$shell = New-Object -ComObject Shell.Application
$folder = $shell.Namespace('shell:AppsFolder')
$rows = @($folder.Items() | ForEach-Object {
    [PSCustomObject]@{
        name = $_.Name
        app_id = $_.Path
        target = $_.ExtendedProperty('System.Link.TargetParsingPath')
    }
})
ConvertTo-Json -InputObject $rows -Compress -Depth 3
