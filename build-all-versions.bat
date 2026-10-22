@echo off
setlocal
cd /d "%~dp0"
title Nova - build for every Minecraft version
rem Optional: build-all-versions.bat 1.20.1 1.21.1   (only those versions)
set ONLY=
:args
if "%~1"=="" goto run
if defined ONLY (set ONLY=%ONLY%,%1) else set ONLY=%1
shift
goto args
:run
if defined ONLY (
  powershell -NoProfile -ExecutionPolicy Bypass -File "tools\build-all-versions.ps1" -Only %ONLY%
) else (
  powershell -NoProfile -ExecutionPolicy Bypass -File "tools\build-all-versions.ps1"
)
start "" explorer "%~dp0dist\mods"
pause
