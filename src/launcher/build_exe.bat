@echo off
setlocal
set "CSC=C:\Windows\Microsoft.NET\Framework64\v4.0.30319\csc.exe"
if not exist "%CSC%" (
    echo [!] Error: csc.exe compiler not found at %CSC%
    exit /b 1
)
set "WPF_DIR=C:\Windows\Microsoft.NET\Framework64\v4.0.30319\WPF"
set "REFS=/r:"%WPF_DIR%\WindowsBase.dll","%WPF_DIR%\PresentationCore.dll","%WPF_DIR%\PresentationFramework.dll",System.Xaml.dll,System.dll,System.Core.dll,System.Windows.Forms.dll,System.Drawing.dll,System.Web.Extensions.dll"

cd /d "%~dp0..\..\"
echo [*] Building RainyDLC GUI Launcher...
"%CSC%" /nologo /target:winexe /optimize+ /platform:anycpu /win32icon:rainydlc.ico %REFS% /out:RainyDLC.exe src\launcher\Launcher.cs
if %ERRORLEVEL% equ 0 (
    copy /y RainyDLC.exe runClient.exe >nul
    if exist "%USERPROFILE%\Desktop\runClient.exe" (
        copy /y RainyDLC.exe "%USERPROFILE%\Desktop\runClient.exe" >nul
        echo [OK] Updated %USERPROFILE%\Desktop\runClient.exe
    )
    if exist "%USERPROFILE%\Desktop\RainyDLC.exe" (
        copy /y RainyDLC.exe "%USERPROFILE%\Desktop\RainyDLC.exe" >nul
        echo [OK] Updated %USERPROFILE%\Desktop\RainyDLC.exe
    )
    if exist "build\libs\rainydlc-protected.jar" (
        copy /y "build\libs\rainydlc-protected.jar" "rainydlc.jar" >nul
        echo [OK] Synced build\libs\rainydlc-protected.jar to rainydlc.jar
    ) else if exist "build\libs\rainydlc-1.0-SNAPSHOT.jar" (
        copy /y "build\libs\rainydlc-1.0-SNAPSHOT.jar" "rainydlc.jar" >nul
        echo [OK] Synced build\libs\rainydlc-1.0-SNAPSHOT.jar to rainydlc.jar
    )
    echo [OK] RainyDLC.exe and runClient.exe built successfully.
) else (
    echo [!] Build failed with code %ERRORLEVEL%
    exit /b %ERRORLEVEL%
)
