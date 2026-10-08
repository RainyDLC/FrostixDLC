param(
    [string]$ModJdk = 'D:\.jdks\jdk-26.0.1',
    [string]$BuilderJdk = 'D:\.jdks\jdk21.0.12_9',
    [string]$Generator = 'Visual Studio 18 2026',
    [string]$CMake = ''
)

$ErrorActionPreference = 'Stop'
$projectRoot = $PSScriptRoot
$repoRoot = Split-Path -Parent $projectRoot
$modJar = Join-Path $projectRoot 'build\libs\rainy-fun-1.0.0.jar'
$previousJavaHome = $env:JAVA_HOME
if (-not $CMake) {
    $localCMake = 'D:\BuildTools\VS2026\Common7\IDE\CommonExtensions\Microsoft\CMake\CMake\bin\cmake.exe'
    if (Test-Path -LiteralPath $localCMake -PathType Leaf) { $CMake = $localCMake }
}

foreach ($jdk in @($ModJdk, $BuilderJdk)) {
    if (-not (Test-Path -LiteralPath (Join-Path $jdk 'bin\javac.exe'))) {
        throw "JDK not found: $jdk"
    }
}

try {
    $env:JAVA_HOME = $ModJdk
    Push-Location $projectRoot
    try {
        & (Join-Path $projectRoot 'gradlew.bat') build
        if ($LASTEXITCODE -ne 0) { throw 'Rainy.fun Fabric JAR build failed' }
    } finally {
        Pop-Location
    }

    if (-not (Test-Path -LiteralPath $modJar -PathType Leaf)) {
        throw "Fabric JAR was not created: $modJar"
    }

    $builderScript = Join-Path $repoRoot 'build.ps1'
    $builderArgs = @('-NoProfile', '-ExecutionPolicy', 'Bypass', '-File', $builderScript,
        '-Jar', $modJar, '-Jdk', $BuilderJdk, '-Generator', $Generator)
    if ($CMake) { $builderArgs += @('-CMake', $CMake) }
    $shellPath = (Get-Process -Id $PID).Path
    & $shellPath @builderArgs
    if ($LASTEXITCODE -ne 0) { throw 'LegitBuilder build failed' }

    Write-Host "Built $((Join-Path $repoRoot 'LegitBuilder.jar'))"
} finally {
    $env:JAVA_HOME = $previousJavaHome
}
