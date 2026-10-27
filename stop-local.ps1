$ErrorActionPreference = 'Stop'
$local = Join-Path $PSScriptRoot '.local'
foreach ($entry in @(@{file='web-process.json';field='serverPath'},@{file='api-process.json';field='jarPath'})) {
    $path = Join-Path $local $entry.file
    if (!(Test-Path $path)) { continue }
    $record = Get-Content $path -Raw | ConvertFrom-Json
    $proc = Get-Process -Id $record.processId -ErrorAction SilentlyContinue
    $cim = Get-CimInstance Win32_Process -Filter "ProcessId=$($record.processId)" -ErrorAction SilentlyContinue
    if ($proc -and $cim -and $cim.CommandLine.Contains($record.($entry.field)) -and ($proc.StartTime.ToUniversalTime() -eq ([datetime]$record.processStartedAt).ToUniversalTime())) {
        Stop-Process -Id $proc.Id -Force
        $proc.WaitForExit(10000) | Out-Null
    }
}
$pgCtl = Join-Path $local 'tools\pgsql\bin\pg_ctl.exe'
$pgData = Join-Path $local 'postgres-data'
& $pgCtl -D $pgData status *> $null
if ($LASTEXITCODE -eq 0) {
    $pg = Start-Process -FilePath $pgCtl -ArgumentList @('-D',('"'+$pgData+'"'),'-m','fast','-w','-t','45','stop') -WindowStyle Hidden -PassThru -RedirectStandardOutput (Join-Path $local 'pg-stop.stdout.log') -RedirectStandardError (Join-Path $local 'pg-stop.stderr.log')
    $pg.WaitForExit()
    & $pgCtl -D $pgData status *> $null
    if ($LASTEXITCODE -ne 3) { throw 'PostgreSQL did not stop. Check .local/pg-stop.stderr.log.' }
}
Write-Host 'Orion Key local services stopped.'
