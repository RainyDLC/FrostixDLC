@echo off
setlocal EnableExtensions
title LegitBuilder Injector

echo ========================================
echo         LegitBuilder Injector
echo ========================================
echo Minecraft must already be running.
echo.

set "BUILDER_JAR="
set /p "BUILDER_JAR=Path to LegitBuilder.jar: "
set "BUILDER_JAR=%BUILDER_JAR:"=%"

if exist "%BUILDER_JAR%\." set "BUILDER_JAR=%BUILDER_JAR%\LegitBuilder.jar"

if not defined BUILDER_JAR (
    echo No JAR path entered.
    goto :finish
)

if not exist "%BUILDER_JAR%" (
    echo File not found: "%BUILDER_JAR%"
    goto :finish
)

rem Prefer the JDK 21 used to build and run LegitBuilder on this machine.
set "JAVA_EXE=D:\.jdks\jdk21.0.12_9\bin\java.exe"

if not exist "%JAVA_EXE%" (
    if defined JAVA_HOME if exist "%JAVA_HOME%\bin\java.exe" set "JAVA_EXE=%JAVA_HOME%\bin\java.exe"
)

if not exist "%JAVA_EXE%" (
    echo JDK 21 was not found.
    echo Install it at D:\.jdks\jdk21.0.12_9 or set JAVA_HOME to JDK 21.
    goto :finish
)

echo.
echo Running LegitBuilder...
echo.
"%JAVA_EXE%" -jar "%BUILDER_JAR%"
set "RESULT=%ERRORLEVEL%"

if not "%RESULT%"=="0" (
    echo.
    echo Injection failed with exit code %RESULT%.
) else (
    echo.
    echo Injection finished successfully.
)

:finish
echo.
pause
endlocal
