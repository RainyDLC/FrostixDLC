param(
    [Parameter(Mandatory=$true)][string]$PayloadDll,
    [Parameter(Mandatory=$true)][string]$CarrierDll,
    [Parameter(Mandatory=$true)][string]$AbiPath,
    [Parameter(Mandatory=$true)][string]$OutJar,
    [Parameter(Mandatory=$true)][string]$Jdk
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
$javac = Join-Path $Jdk 'bin\javac.exe'
$jar = Join-Path $Jdk 'bin\jar.exe'
$jna = Join-Path $root 'injector\lib\jna-5.14.0.jar'
$jnaPlatform = Join-Path $root 'injector\lib\jna-platform-5.14.0.jar'
foreach ($path in @($PayloadDll, $CarrierDll, $AbiPath, $javac, $jar, $jna, $jnaPlatform)) {
    if (-not (Test-Path -LiteralPath $path -PathType Leaf)) { throw "Missing build input: $path" }
}
$locked = Get-Content -LiteralPath (Join-Path $root 'dependencies\lock.json') -Raw | ConvertFrom-Json
foreach ($name in @('jna-5.14.0.jar', 'jna-platform-5.14.0.jar')) {
    $relative = "injector/lib/$name"
    $expected = $locked.PSObject.Properties[$relative].Value
    $actual = (Get-FileHash -LiteralPath (Join-Path $root "injector\lib\$name") -Algorithm SHA256).Hash.ToLowerInvariant()
    if (-not $expected -or $actual -ne $expected) { throw "JNA dependency hash mismatch: $name" }
}
$abi = Get-Content -LiteralPath $AbiPath -Raw | ConvertFrom-Json
$payloadHash = (Get-FileHash -LiteralPath $PayloadDll -Algorithm SHA256).Hash.ToLowerInvariant()
$carrierHash = (Get-FileHash -LiteralPath $CarrierDll -Algorithm SHA256).Hash.ToLowerInvariant()
if ($abi.payloadSha256 -ne $payloadHash -or $abi.carrierSha256 -ne $carrierHash) {
    throw 'Payload or carrier does not match loader-abi.json'
}
$outPath = [IO.Path]::GetFullPath($OutJar)
New-Item -ItemType Directory -Path (Split-Path -Parent $outPath) -Force | Out-Null
$stageRoot = Join-Path (Split-Path -Parent $outPath) ('jar-stage-' + [guid]::NewGuid().ToString('N'))
New-Item -ItemType Directory -Path $stageRoot | Out-Null
$stageRoot = [IO.Path]::GetFullPath($stageRoot)
try {
    $classes = Join-Path $stageRoot 'classes'
    $stage = Join-Path $stageRoot 'stage'
    New-Item -ItemType Directory -Path $classes, $stage | Out-Null
    & $javac --release 21 -encoding UTF-8 -cp "$jna;$jnaPlatform" -d $classes (Join-Path $root 'injector\src\LegitBuilder.java')
    if ($LASTEXITCODE -ne 0) { throw 'Injector compilation failed' }
    Copy-Item -Path (Join-Path $classes '*') -Destination $stage -Recurse
    Push-Location $stage
    try {
        & $jar xf $jna
        if ($LASTEXITCODE -ne 0) { throw 'Cannot unpack JNA' }
        & $jar xf $jnaPlatform
        if ($LASTEXITCODE -ne 0) { throw 'Cannot unpack JNA Platform' }
    } finally { Pop-Location }
    $metadata = Join-Path $stage 'META-INF'
    if (Test-Path -LiteralPath $metadata) { Remove-Item -LiteralPath $metadata -Recurse -Force }
    Copy-Item -LiteralPath $PayloadDll -Destination (Join-Path $stage 'mod-payload.dll')
    Copy-Item -LiteralPath $CarrierDll -Destination (Join-Path $stage 'vcruntime_aux_carrier.dll')
    Copy-Item -LiteralPath $AbiPath -Destination (Join-Path $stage 'loader-abi.json')
    $assets = Join-Path $root 'offline-assets'
    if (Test-Path -LiteralPath $assets -PathType Container) {
        Copy-Item -LiteralPath $assets -Destination (Join-Path $stage 'offline-assets') -Recurse
    }
    New-Item -ItemType Directory -Path $metadata | Out-Null
    [IO.File]::WriteAllText((Join-Path $metadata 'MANIFEST.MF'), "Manifest-Version: 1.0`r`nMain-Class: LegitBuilder`r`nCreated-By: LegitBuilder`r`n`r`n", [Text.Encoding]::ASCII)
    Push-Location $stage
    try {
        & $jar cfm $outPath (Join-Path $metadata 'MANIFEST.MF') .
        if ($LASTEXITCODE -ne 0) { throw 'JAR packaging failed' }
    } finally { Pop-Location }
    Write-Host "Built $outPath"
} finally {
    $parent = [IO.Path]::GetFullPath((Split-Path -Parent $outPath)).TrimEnd('\') + '\'
    if (-not $stageRoot.StartsWith($parent, [StringComparison]::OrdinalIgnoreCase) -or
        (Split-Path -Leaf $stageRoot) -notmatch '^jar-stage-[0-9a-f]{32}$') {
        throw "Unexpected staging path: $stageRoot"
    }
    Remove-Item -LiteralPath $stageRoot -Recurse -Force -ErrorAction SilentlyContinue
}
