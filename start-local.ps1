$ErrorActionPreference = 'Stop'
$root = $PSScriptRoot
$local = Join-Path $root '.local'
$config = Get-Content (Join-Path $local 'runtime-config.json') -Raw | ConvertFrom-Json
$pgBin = Join-Path $local 'tools\pgsql\bin'
$pgData = Join-Path $local 'postgres-data'

& (Join-Path $pgBin 'pg_ctl.exe') -D $pgData status *> $null
if ($LASTEXITCODE -ne 0) {
    if (Get-NetTCPConnection -State Listen -LocalPort 5543 -ErrorAction SilentlyContinue) { throw 'Port 5543 is occupied.' }
    $pg = Start-Process -FilePath (Join-Path $pgBin 'pg_ctl.exe') -ArgumentList @('-D',('"'+$pgData+'"'),'-l',('"'+(Join-Path $local 'postgres.log')+'"'),'-w','-t','45','start') -WindowStyle Hidden -PassThru -RedirectStandardOutput (Join-Path $local 'pg-start.stdout.log') -RedirectStandardError (Join-Path $local 'pg-start.stderr.log')
    $pg.WaitForExit()
    & (Join-Path $pgBin 'pg_ctl.exe') -D $pgData status *> $null
    if ($LASTEXITCODE -ne 0) { throw 'PostgreSQL did not start. Check .local/pg-start.stderr.log.' }
}

$apiRecord = Join-Path $local 'api-process.json'
$apiRunning = $false
if (Test-Path $apiRecord) {
    $record = Get-Content $apiRecord -Raw | ConvertFrom-Json
    $proc = Get-Process -Id $record.processId -ErrorAction SilentlyContinue
    $cim = Get-CimInstance Win32_Process -Filter "ProcessId=$($record.processId)" -ErrorAction SilentlyContinue
    $apiRunning = $proc -and $cim -and $cim.CommandLine.Contains($record.jarPath) -and ($proc.StartTime.ToUniversalTime() -eq ([datetime]$record.processStartedAt).ToUniversalTime())
}
if (!$apiRunning) {
    if (Get-NetTCPConnection -State Listen -LocalPort 8103 -ErrorAction SilentlyContinue) { throw 'Port 8103 is occupied.' }
    $env:DB_URL = 'jdbc:postgresql://127.0.0.1:5543/orion_key_local'
    $env:DB_USERNAME = 'orion_app'
    $env:DB_PASSWORD = $config.dbPassword
    $env:JWT_SECRET = $config.jwtSecret
    $env:PASSWORD_PLAIN = 'false'
    $env:MAIL_ENABLED = 'false'
    $env:SERVER_ADDRESS = '127.0.0.1'
    $env:SERVER_PORT = '8103'
    $env:CORS_ALLOWED_ORIGINS = 'http://127.0.0.1:3103,http://localhost:3103'
    $env:UPLOAD_PATH = Join-Path $local 'uploads'
    $env:LOGGING_LEVEL_COM_ORIONKEY = 'INFO'
    $jar = Join-Path $root 'apps\api\target\orion-key-1.0.0-SNAPSHOT.jar'
    $java = Join-Path $local 'tools\jdk-22.0.2+9\bin\java.exe'
    if (!(Test-Path $jar)) { throw 'Backend JAR is missing. See .local/maven-package.log.' }
    $api = Start-Process -FilePath $java -ArgumentList @('-Xms128m','-Xmx512m','-Dfile.encoding=UTF-8','-jar',('"'+$jar+'"')) -WorkingDirectory $root -WindowStyle Hidden -PassThru -RedirectStandardOutput (Join-Path $local 'api.stdout.log') -RedirectStandardError (Join-Path $local 'api.stderr.log')
    @{processId=$api.Id;processStartedAt=$api.StartTime.ToUniversalTime().ToString('o');jarPath=$jar;port=8103} | ConvertTo-Json | Set-Content $apiRecord -Encoding UTF8
    Remove-Item Env:DB_PASSWORD,Env:JWT_SECRET
}

$apiReady = $false
for ($i=0; $i -lt 90; $i++) {
    try { $check = Invoke-RestMethod 'http://127.0.0.1:8103/api/products' -TimeoutSec 2; if ($check.code -eq 0) {$apiReady=$true; break} } catch {}
    Start-Sleep -Seconds 1
}
if (!$apiReady) { throw 'Backend did not become ready. Check .local/api.stdout.log and api.stderr.log.' }
& (Join-Path $local 'web-run.ps1') -HostName '127.0.0.1' -Port 3103 -BackendUrl 'http://127.0.0.1:8103'
Write-Host 'Store: http://127.0.0.1:3103'
Write-Host 'Admin: http://127.0.0.1:3103/admin'
Write-Host 'Credentials: .local/login-info.txt'
