@echo off
setlocal

rem OpenSockets 1.12.2 build helper. ForgeGradle is run with Java 8.
set "SCRIPT_DIR=%~dp0"
set "JAVA8_HOME=C:\Program Files\Eclipse Adoptium\jdk-8.0.422.5-hotspot"

if exist "%JAVA8_HOME%\bin\java.exe" set "JAVA_HOME=%JAVA8_HOME%"
if not defined JAVA_HOME set "JAVA_HOME=%JAVA8_HOME%"

if not exist "%JAVA_HOME%\bin\java.exe" (
    echo Java 8 was not found at "%JAVA_HOME%".
    echo Install Java 8 or set JAVA_HOME before running this script.
    exit /b 1
)

call "%SCRIPT_DIR%gradlew.bat" --no-daemon clean build %*
exit /b %ERRORLEVEL%
