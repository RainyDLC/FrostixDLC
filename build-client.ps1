# Сборка клиента RainyDLC: сначала Fabric JAR, затем LegitBuilder.jar.
# Запуск из корня репозитория:  .\build-client.ps1
param(
    [string]$Jdk = $env:JAVA_HOME
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
$root = $PSScriptRoot
$client = Join-Path $root 'RainyDLC'

Push-Location $client
try {
    & .\gradlew.bat build -x test
    if ($LASTEXITCODE -ne 0) { throw 'Fabric build failed' }
} finally {
    Pop-Location
}

$jar = Get-ChildItem -LiteralPath (Join-Path $client 'build\libs') -Filter '*.jar' |
    Where-Object { $_.Name -notmatch '-sources' } |
    Sort-Object LastWriteTime -Descending | Select-Object -First 1
if (-not $jar) { throw 'Fabric JAR not found in RainyDLC\build\libs' }
Write-Host "Fabric JAR: $($jar.FullName)"

$buildArgs = @{ Jar = $jar.FullName }
if ($Jdk) { $buildArgs.Jdk = $Jdk }
& (Join-Path $root 'build.ps1') @buildArgs
if ($LASTEXITCODE -ne 0) { throw 'LegitBuilder build failed' }
Write-Host "Done: $(Join-Path $root 'build\release\LegitBuilder.jar')"
