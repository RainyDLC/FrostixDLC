param(
    [Parameter(Mandatory=$true)][string]$Jar,
    [string]$Jdk = $env:JAVA_HOME,
    [string]$VsDevCmd = 'C:\Program Files\Microsoft Visual Studio\18\Community\Common7\Tools\VsDevCmd.bat',
    [string]$Generator = 'Visual Studio 18 2026'
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
$Jar = (Resolve-Path -LiteralPath $Jar).Path
if (-not $Jdk) { $Jdk = Split-Path -Parent (Split-Path -Parent (Get-Command javac.exe -ErrorAction Stop).Source) }
$Jdk = (Resolve-Path -LiteralPath $Jdk).Path
if (-not (Test-Path -LiteralPath $VsDevCmd -PathType Leaf)) { throw "MSVC setup not found: $VsDevCmd" }
$out = Join-Path $root 'build\tests\all'
New-Item -ItemType Directory -Force -Path $out | Out-Null

& (Join-Path $root 'build.ps1') -Jar $Jar -Jdk $Jdk -Generator $Generator
if ($LASTEXITCODE -ne 0) { throw 'Build failed' }

$libraries = @(Get-ChildItem -LiteralPath (Join-Path $root 'dependencies\compile') -Filter '*.jar' -File | ForEach-Object FullName)
$classes = Join-Path $out 'classes'
New-Item -ItemType Directory -Force -Path $classes | Out-Null
$classpath = (@($classes, (Join-Path $root 'build\release\runtime-classes'), (Join-Path $root 'LegitBuilder.jar')) + $libraries) -join ';'
$sources = @(Get-ChildItem -LiteralPath (Join-Path $root 'tests') -Filter '*.java' -File | ForEach-Object FullName)
& (Join-Path $Jdk 'bin\javac.exe') -proc:none -encoding UTF-8 -cp $classpath -d $classes @sources
if ($LASTEXITCODE -ne 0) { throw 'Java regression compilation failed' }

function Invoke-JavaRegression([string]$TestClass, [string[]]$Arguments) {
    & (Join-Path $Jdk 'bin\java.exe') -Xverify:all -Xcheck:jni -cp $classpath $TestClass @Arguments
    if ($LASTEXITCODE -ne 0) { throw "$TestClass failed" }
}

Invoke-JavaRegression 'mod.runtime.LoadedAdapterInputRegression' @()
Invoke-JavaRegression 'mod.runtime.LoadedClassAdapterRegression' @()
Invoke-JavaRegression 'mod.runtime.LateMixinResourceRegression' @($Jar, (Join-Path $root 'build\release\mod_payload.cpp'))
Invoke-JavaRegression 'ClassInfoCacheRegression' @()
Invoke-JavaRegression 'TlsPayloadRegression' @((Join-Path $root 'build\release\mod-payload.dll'))

$nativeSource = Join-Path $root 'tests\LoadedCaptureNative.cpp'
$nativeDll = Join-Path $out 'capture.dll'
$compile = 'call "{0}" -arch=x64 -host_arch=x64 >nul && cl /nologo /std:c++20 /EHsc /MD /LD /utf-8 /I"{1}\include" /I"{1}\include\win32" "{2}" /Fo"{3}" /Fe"{4}" /link user32.lib' -f $VsDevCmd, $Jdk, $nativeSource, (Join-Path $out 'capture.obj'), $nativeDll
& $env:COMSPEC /d /c $compile
if ($LASTEXITCODE -ne 0) { throw 'Loaded capture native fixture build failed' }
Invoke-JavaRegression 'mod.runtime.LoadedCaptureRegression' @($nativeDll)

& (Join-Path $root 'scripts\test_jvmti_table.ps1') -Jar $Jar -Jdk $Jdk -VsDevCmd $VsDevCmd
if ($LASTEXITCODE -ne 0) { throw 'JVMTI table regression failed' }
Write-Output 'All LegitBuilder regressions passed.'
