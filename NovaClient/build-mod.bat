@echo off
setlocal
cd /d "%~dp0"
set GRADLE_VER=8.12.1

rem ---- 1. need a JDK (the compiler), not just Java ----
where javac >nul 2>nul
if errorlevel 1 (
  echo.
  echo No JDK found ^(you have Java to RUN things, but not the compiler^).
  echo Install it with this command, then CLOSE and REOPEN this window:
  echo     winget install EclipseAdoptium.Temurin.21.JDK
  echo.
  if /i not "%~1"=="quiet" pause
  exit /b 1
)

rem ---- 2. download Gradle automatically (no install needed) ----
if not exist ".gradle-dist\gradle-%GRADLE_VER%\bin\gradle.bat" (
  echo Downloading Gradle %GRADLE_VER% ^(one time, about 130 MB^)...
  powershell -NoProfile -ExecutionPolicy Bypass -Command "[Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12; $ProgressPreference='SilentlyContinue'; Invoke-WebRequest 'https://services.gradle.org/distributions/gradle-%GRADLE_VER%-bin.zip' -OutFile 'gradle.zip'"
  if errorlevel 1 ( echo Download failed - check your internet and try again. & if /i not "%~1"=="quiet" pause & exit /b 1 )
  powershell -NoProfile -ExecutionPolicy Bypass -Command "Expand-Archive -Force 'gradle.zip' '.gradle-dist'"
  del gradle.zip
)

rem ---- 3. build ----
echo Building Nova Client ^(first build downloads Minecraft + libraries, can take a few minutes^)...
call ".gradle-dist\gradle-%GRADLE_VER%\bin\gradle.bat" build
if errorlevel 1 (
  echo.
  echo BUILD FAILED - copy the red error text above and send it to Claude.
  if /i not "%~1"=="quiet" pause
  exit /b 1
)

echo.
echo ================================================================
echo  Done! Your mod jar is here:
echo  %cd%\build\libs\novaclient-*.jar (the one without -sources or -dev)
echo  ^(NOT the one ending in -sources or -dev^)
echo  In Nova Launcher: Settings ^> Local Nova jar ^> pick that file ^> Save ^> PLAY
echo ================================================================
if /i not "%~1"=="quiet" (
  start "" explorer "%cd%\build\libs"
  pause
)
