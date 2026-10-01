param(
    [Parameter(Mandatory=$true)][string]$Jar,
    [string]$Jdk = $env:JAVA_HOME,
    [string]$OutDir = 'build\release',
    [string]$Generator = 'Visual Studio 18 2026',
    [string]$CMake = ''
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
$root = $PSScriptRoot
$Jar = (Resolve-Path -LiteralPath $Jar).Path
if (-not $Jdk) {
    $javac = (Get-Command javac.exe -ErrorAction Stop).Source
    $Jdk = Split-Path -Parent (Split-Path -Parent $javac)
}
$Jdk = (Resolve-Path -LiteralPath $Jdk).Path
if (-not (Test-Path -LiteralPath (Join-Path $Jdk 'bin\javac.exe'))) { throw "JDK 21 not found: $Jdk" }
if (-not $CMake) {
    $command = Get-Command cmake.exe -ErrorAction SilentlyContinue
    if ($command) { $CMake = $command.Source }
    else {
        $CMake = Join-Path ${env:ProgramFiles} 'Microsoft Visual Studio\18\Community\Common7\IDE\CommonExtensions\Microsoft\CMake\CMake\bin\cmake.exe'
    }
}
if (-not (Test-Path -LiteralPath $CMake)) { throw "CMake not found: $CMake" }
$OutDir = [IO.Path]::GetFullPath((Join-Path $root $OutDir))
New-Item -ItemType Directory -Path $OutDir -Force | Out-Null
$generatorBuild = Join-Path $root 'build\generator'
& $CMake -S (Join-Path $root 'jar-to-dll') -B $generatorBuild -G $Generator -A x64
if ($LASTEXITCODE -ne 0) { throw 'Generator configuration failed' }
& $CMake --build $generatorBuild --config Release --parallel 4
if ($LASTEXITCODE -ne 0) { throw 'Generator build failed' }
$generatorExe = Join-Path $generatorBuild 'Release\jar-to-dll.exe'

$runtimeClasses = Join-Path $OutDir 'runtime-classes'
& (Join-Path $root 'scripts\build_runtime.ps1') -Jdk $Jdk -OutDir $runtimeClasses
if ($LASTEXITCODE -ne 0 -or -not (Test-Path -LiteralPath (Join-Path $runtimeClasses 'mod\runtime\NativeBridge.class'))) {
    throw 'Runtime class compilation failed'
}
& $generatorExe --jar $Jar --output $OutDir --jdk $Jdk --runtime-class-dir $runtimeClasses
if ($LASTEXITCODE -ne 0) { throw 'Payload generation failed' }

$payloadBuild = Join-Path $OutDir 'native-build'
& $CMake -S $OutDir -B $payloadBuild -G $Generator -A x64 "-DJDK_PATH=$Jdk"
if ($LASTEXITCODE -ne 0) { throw 'Payload configuration failed' }
& $CMake --build $payloadBuild --config Release --parallel 4
if ($LASTEXITCODE -ne 0) { throw 'Payload build failed' }
$payload = Join-Path $OutDir 'mod-payload.dll'
Copy-Item -LiteralPath (Join-Path $payloadBuild 'Release\mod-payload.dll') -Destination $payload -Force

$image = [IO.File]::ReadAllBytes($payload)
if ($image.Length -lt 0x100) { throw 'Payload PE header is truncated' }
$peOffset = [BitConverter]::ToInt32($image, 0x3c)
if ($peOffset -lt 0 -or $peOffset + 84 -gt $image.Length -or [Text.Encoding]::ASCII.GetString($image, $peOffset, 4) -ne "PE`0`0") {
    throw 'Payload PE header is invalid'
}
$imageSize = [BitConverter]::ToUInt32($image, $peOffset + 24 + 56)
$carrier = Join-Path $OutDir 'vcruntime_aux_carrier.dll'
& python (Join-Path $root 'carrier\src\generate_carrier.py') --payload-image-size $imageSize --output $carrier --manifest-out (Join-Path $OutDir 'carrier-manifest.json')
if ($LASTEXITCODE -ne 0) { throw 'Carrier generation failed' }

$abiPath = Join-Path $OutDir 'loader-abi.json'
$abi = Get-Content -LiteralPath $abiPath -Raw | ConvertFrom-Json
$abi.payloadSha256 = (Get-FileHash -LiteralPath $payload -Algorithm SHA256).Hash.ToLowerInvariant()
$abi.carrierSha256 = (Get-FileHash -LiteralPath $carrier -Algorithm SHA256).Hash.ToLowerInvariant()
[IO.File]::WriteAllText($abiPath, ($abi | ConvertTo-Json -Depth 20), [Text.UTF8Encoding]::new($false))
& (Join-Path $root 'scripts\build_injector.ps1') -PayloadDll $payload -CarrierDll $carrier -AbiPath $abiPath -OutJar (Join-Path $OutDir 'LegitBuilder.jar') -Jdk $Jdk
if ($LASTEXITCODE -ne 0 -or -not (Test-Path -LiteralPath (Join-Path $OutDir 'LegitBuilder.jar'))) { throw 'JAR build failed' }
Copy-Item -LiteralPath (Join-Path $OutDir 'LegitBuilder.jar') -Destination (Join-Path $root 'LegitBuilder.jar') -Force
