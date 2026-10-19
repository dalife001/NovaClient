@echo off
setlocal
cd /d "%~dp0"
title Nova - build everything

where javac >nul 2>nul
if errorlevel 1 goto :nojdk
where jpackage >nul 2>nul
if errorlevel 1 goto :nojdk

echo.
echo ===== [1/4] Building the Nova Client mod =====
call "NovaClient\build-mod.bat" quiet
if errorlevel 1 ( echo. & echo The mod build failed - send the red text above to Claude. & pause & exit /b 1 )
cd /d "%~dp0"

set MODJAR=
for %%F in (NovaClient\build\libs\novaclient-*.jar) do (
  echo %%~nxF | findstr /i /c:"-sources" /c:"-dev" >nul
  if errorlevel 1 set MODJAR=%%F
)
if "%MODJAR%"=="" ( echo Could not find the built mod jar in NovaClient\build\libs & pause & exit /b 1 )
for %%F in ("%MODJAR%") do set MODNAME=%%~nxF
set VER=%MODNAME:novaclient-=%
set VER=%VER:.jar=%
set MC=1.21.4
for /f "tokens=2 delims==" %%A in ('findstr /b "minecraft_version=" NovaClient\gradle.properties') do set MC=%%A
echo Mod: %MODNAME%  (version %VER%, Minecraft %MC%)

echo.
echo ===== [2/4] Packing the mod INSIDE the launcher =====
if exist "NovaLauncher\res" rmdir /s /q "NovaLauncher\res"
mkdir "NovaLauncher\res\embedded"
copy /Y "%MODJAR%" "NovaLauncher\res\embedded\novaclient.jar" >nul
(
  echo version=%VER%
  echo minecraft=%MC%
) > "NovaLauncher\res\embedded\embedded.properties"

echo.
echo ===== [3/4] Building the launcher (one app, mod included) =====
if not exist dist mkdir dist
pushd NovaLauncher
if exist out rmdir /s /q out
mkdir out
dir /s /b src\*.java > sources.txt
javac --release 21 -d out @sources.txt
if errorlevel 1 ( popd & echo Launcher compile failed. & pause & exit /b 1 )
del sources.txt
echo Main-Class: com.nova.launcher.LauncherApp> out\manifest.txt
jar cfm NovaLauncher.jar out\manifest.txt -C out com -C res embedded
if exist app-input rmdir /s /q app-input
mkdir app-input
copy /Y NovaLauncher.jar app-input\ >nul
popd
copy /Y "NovaLauncher\NovaLauncher.jar" "dist\" >nul

echo.
echo ===== [4/4] Making NovaLauncher.exe (standalone, no Java needed to run it) =====
if exist "dist\NovaLauncher" rmdir /s /q "dist\NovaLauncher"
jpackage --type app-image --name NovaLauncher --input "NovaLauncher\app-input" --main-jar NovaLauncher.jar --main-class com.nova.launcher.LauncherApp --dest dist --add-modules ALL-MODULE-PATH
if errorlevel 1 ( echo. & echo Making the .exe failed - you can still run: java -jar dist\NovaLauncher.jar & pause & exit /b 1 )
powershell -NoProfile -ExecutionPolicy Bypass -Command "Compress-Archive -Force -Path 'dist\NovaLauncher' -DestinationPath 'dist\NovaLauncher-windows.zip'"
powershell -NoProfile -ExecutionPolicy Bypass -File "tools\set-launcher-config.ps1"

echo.
echo ================================================================
echo  ALL DONE - one app, Nova Client is inside it.
echo  Folder: %~dp0dist
echo    NovaLauncher\NovaLauncher.exe   double-click it:
echo        PLAY                       = installs Nova + starts Minecraft
echo        Add to Minecraft Launcher  = adds a "Nova Client" profile to the official launcher
echo    NovaLauncher-windows.zip        share this with friends (unzip, run the .exe)
echo    NovaLauncher.jar                the same app as a plain jar
echo ================================================================
start "" explorer "%~dp0dist\NovaLauncher"
pause
exit /b 0

:nojdk
echo.
echo You need the full JDK 21 (it contains javac and jpackage).
echo Install it with:    winget install EclipseAdoptium.Temurin.21.JDK
echo Then CLOSE and REOPEN this window and run build-everything.bat again.
echo.
pause
exit /b 1
