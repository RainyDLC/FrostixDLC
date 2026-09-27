@echo off
cd /d "%~dp0"

set "JAVA_HOME=C:\Program Files\Java\jdk-26.0.1"

call gradlew.bat runClient --console=plain
