param(
    [Parameter(Mandatory=$true)][string]$Jdk,
    [Parameter(Mandatory=$true)][string]$OutDir
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
$javac = Join-Path $Jdk 'bin\javac.exe'
if (-not (Test-Path -LiteralPath $javac -PathType Leaf)) { throw "JDK compiler not found: $javac" }
$locked = Get-Content -LiteralPath (Join-Path $root 'dependencies\lock.json') -Raw | ConvertFrom-Json
$libraries = @(
    'sponge-mixin-0.17.4+mixin.0.8.7.jar',
    'fabric-loader-0.19.5.jar',
    'asm-9.9.jar',
    'asm-tree-9.9.jar',
    'asm-commons-9.9.jar',
    'asm-util-9.9.jar',
    'asm-analysis-9.9.jar'
)
$classpath = @()
foreach ($name in $libraries) {
    $relative = "dependencies/compile/$name"
    $path = Join-Path $root ("dependencies\compile\" + $name)
    if (-not (Test-Path -LiteralPath $path -PathType Leaf)) { throw "Missing compiler dependency: $path" }
    $expected = $locked.PSObject.Properties[$relative].Value
    $actual = (Get-FileHash -LiteralPath $path -Algorithm SHA256).Hash.ToLowerInvariant()
    if (-not $expected -or $actual -ne $expected) { throw "Compiler dependency hash mismatch: $name" }
    $classpath += $path
}
$sourceRoot = Join-Path $root 'runtime\src\mod\runtime'
$sourceNames = @(
    'IClassBytecodeProvider.java',
    'NativeBridge.java',
    'NativeScrubberBridge.java',
    'NativeScrubber.java',
    'DllMixinServiceWrapper.java',
    'DllRefmapRemapper.java',
    'AccessWidenerBridge.java',
    'MixinVersionAdapter.java',
    'LoadedClassAdapter.java'
)
$sources = foreach ($name in $sourceNames) {
    $path = Join-Path $sourceRoot $name
    if (-not (Test-Path -LiteralPath $path -PathType Leaf)) { throw "Missing runtime source: $path" }
    $path
}
$OutDir = [IO.Path]::GetFullPath($OutDir)
New-Item -ItemType Directory -Path $OutDir -Force | Out-Null
$ErrorActionPreference = 'Continue'
try {
    & $javac -source 21 -target 21 -proc:none -encoding UTF-8 -cp ($classpath -join ';') -d $OutDir @sources
} finally {
    $ErrorActionPreference = 'Stop'
}
if ($LASTEXITCODE -ne 0) { throw 'Runtime compilation failed' }
Write-Host "Compiled runtime classes in $OutDir"
