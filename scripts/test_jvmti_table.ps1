param(
    [Parameter(Mandatory=$true)][string]$Jar,
    [string]$Jdk = $env:JAVA_HOME,
    [string]$VsDevCmd = 'C:\Program Files\Microsoft Visual Studio\18\Community\Common7\Tools\VsDevCmd.bat',
    [string]$AsmJar = '',
    [string]$AsmTreeJar = '',
    [string]$RuntimeJava = ''
)
Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
$repo = Split-Path -Parent $PSScriptRoot
$dependencyDir = Join-Path $repo 'dependencies\compile'
if (-not $Jdk) { $Jdk = Split-Path -Parent (Split-Path -Parent (Get-Command javac.exe -ErrorAction Stop).Source) }
if (-not $AsmJar) { $AsmJar = Join-Path $dependencyDir 'asm-9.9.jar' }
if (-not $AsmTreeJar) { $AsmTreeJar = Join-Path $dependencyDir 'asm-tree-9.9.jar' }
$out = Join-Path $repo 'build\tests\jvmti-table'
if (-not $RuntimeJava) { $RuntimeJava = "$Jdk\bin\java.exe" }
foreach ($required in @($VsDevCmd, $AsmJar, $AsmTreeJar, $RuntimeJava, "$Jdk\bin\javac.exe")) {
    if (-not (Test-Path -LiteralPath $required -PathType Leaf)) { throw "Missing test dependency: $required" }
}
New-Item -ItemType Directory -Force -Path $out | Out-Null
Push-Location $repo
try {
    $runtimeClasses = Join-Path $out 'runtime-classes'
    $ErrorActionPreference = 'Continue'
    try {
        & powershell -NoProfile -ExecutionPolicy Bypass -File "$PSScriptRoot\build_runtime.ps1" -Jdk $Jdk -OutDir $runtimeClasses *> "$out\runtime-build.log"
    } finally { $ErrorActionPreference = 'Stop' }
    if ($LASTEXITCODE -ne 0) { throw "Runtime build failed; see $out\runtime-build.log" }
    & $env:COMSPEC /d /c ('call "{0}" -arch=x64 -host_arch=x64 >nul && cmake --build "{1}\build\generator" --config Release' -f $VsDevCmd, $repo) *> "$out\generator-build.log"
    if ($LASTEXITCODE -ne 0) { throw "Generator build failed; see $out\generator-build.log" }
    & "$repo\build\generator\Release\jar-to-dll.exe" --jar $Jar --output $out --jdk $Jdk --runtime-class-dir $runtimeClasses *> "$out\generation.log"
    if ($LASTEXITCODE -ne 0) { throw "Test payload generation failed; see $out\generation.log" }
    & $env:COMSPEC /d /c ('call "{0}" -arch=x64 -host_arch=x64 >nul && cl /nologo /std:c++20 /EHsc /MD /LD /utf-8 /I"{1}\include" /I"{1}\include\win32" "{2}\tests\JvmtiMethodTableNative.cpp" /Fo"{3}\test.obj" /Fe"{3}\test.dll" /link user32.lib' -f $VsDevCmd, $Jdk, $repo, $out) *> "$out\native-build.log"
    if ($LASTEXITCODE -ne 0) { throw "Native test build failed; see $out\native-build.log" }
    $testClasspath = "$out\classes;$AsmJar;$AsmTreeJar;$runtimeClasses"
    $mixinJar = Join-Path $dependencyDir 'sponge-mixin-0.17.4+mixin.0.8.7.jar'
    $testClasspath += ";$mixinJar"
    foreach ($module in @('asm-commons', 'asm-util', 'asm-analysis')) {
        $dependency = Join-Path $dependencyDir "$module-9.9.jar"
        if (-not (Test-Path -LiteralPath $dependency)) { throw "Missing test dependency: $dependency" }
        $testClasspath += ";$dependency"
    }
    & "$Jdk\bin\javac.exe" -proc:none -cp $testClasspath -d "$out\classes" "$repo\tests\JvmtiMethodTableRegression.java" "$repo\tests\LateMixinSequenceRegression.java"
    if ($LASTEXITCODE -ne 0) { throw 'Java test compilation failed' }
    foreach ($mode in @('regression', 'vmdeath', 'sequence', 'lookup-gc')) {
        $info = New-Object System.Diagnostics.ProcessStartInfo
        $info.FileName = $RuntimeJava
        $info.Arguments = '-Xverify:all -Xcheck:jni -cp "{0}" mod.runtime.JvmtiMethodTableRegression "{1}\test.dll" {2}' -f $testClasspath, $out, $mode
        if ($mode -eq 'sequence') {
            $info.Arguments = '-Xverify:all -Xcheck:jni -cp "{0}" mod.runtime.LateMixinSequenceRegression "{1}\test.dll"' -f $testClasspath, $out
        }
        $info.WorkingDirectory = $out
        $info.UseShellExecute = $false
        $info.CreateNoWindow = $true
        $info.RedirectStandardOutput = $true
        $info.RedirectStandardError = $true
        $info.EnvironmentVariables['TMP'] = $out
        $info.EnvironmentVariables['TEMP'] = $out
        $process = New-Object System.Diagnostics.Process
        $process.StartInfo = $info
        try {
            if (-not $process.Start()) { throw 'Failed to start test JVM' }
            $stdout = $process.StandardOutput.ReadToEndAsync()
            $stderr = $process.StandardError.ReadToEndAsync()
            if (-not $process.WaitForExit(30000)) {
                $process.Kill()
                $process.WaitForExit()
                throw "Isolated $mode JVM exceeded 30 seconds and was stopped"
            }
            $output = $stdout.GetAwaiter().GetResult() + $stderr.GetAwaiter().GetResult()
            $output | Out-File -LiteralPath "$out\$mode-output.log" -Encoding utf8
            Write-Output $output.Trim()
            if ($process.ExitCode -ne 0) { throw "$mode JVM failed with exit code $($process.ExitCode)" }
            if ($output -match 'WARNING in native method|FATAL ERROR|AssertionError') { throw "$mode reported JNI or assertion failures" }
            Copy-Item -LiteralPath "$out\mod_payload_log.txt" -Destination "$out\$mode-native.log" -Force
            if ($mode -eq 'vmdeath' -and (Get-Content -LiteralPath "$out\$mode-native.log" -Raw) -notmatch 'Original table restored') {
                throw 'VMDeath did not restore the function table'
            }
        } finally { $process.Dispose() }
    }
    Write-Output "All JVMTI table tests passed. Logs: $out"
} finally { Pop-Location }
