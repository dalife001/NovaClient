@echo off
setlocal EnableExtensions EnableDelayedExpansion
cd /d "%~dp0"
title Nova - build everything

where javac >nul 2>nul
if errorlevel 1 goto :nojdk
where jpackage >nul 2>nul
if errorlevel 1 goto :nojdk

rem Prefer installed JDK 21 for the main Fabric client and launcher build.
for /d %%J in ("C:\Program Files\Java\jdk-21*") do if exist "%%~fJ\bin\javac.exe" set "JAVA_HOME=%%~fJ"
if defined JAVA_HOME set "PATH=%JAVA_HOME%\bin;%PATH%"

rem ---- [1/4] Build default Java 21 client ----
echo.
echo ===== [1/4] Building default Nova Client =====
call "NovaClient\build-mod.bat" quiet
if errorlevel 1 ( echo Default client build failed. & exit /b 1 )
set "MODJAR="
for %%F in (NovaClient\build\libs\novaclient-*.jar) do (
  echo %%~nxF | findstr /i /c:"-sources" /c:"-dev" >nul
  if errorlevel 1 set "MODJAR=%%F"
)
if not defined MODJAR ( echo Could not find the default client jar. & exit /b 1 )
for %%F in ("%MODJAR%") do set "MODNAME=%%~nxF"
set "VER=%MODNAME:novaclient-=%"
set "VER=%VER:.jar=%"
set "MC=1.21.4"
for /f "tokens=2 delims==" %%A in ('findstr /b "minecraft_version=" NovaClient\gradle.properties') do set "MC=%%A"

rem Build the main Yarn-mapped client matrix. Successful jars are saved under dist\mods.
echo.
echo Building the available 1.x Minecraft versions for the embedded catalog...
powershell -NoProfile -ExecutionPolicy Bypass -File tools\build-all-versions.ps1
if errorlevel 1 echo Some 1.x builds failed; successful jars will still be packaged.

rem ---- [2/4] Build 26.x client using Java 25 and Gradle 9 ----
echo.
echo ===== [2/4] Building Minecraft 26.x client =====
set "JDK25="
for /d %%J in ("C:\Program Files\Java\jdk-25*") do if exist "%%~fJ\bin\java.exe" set "JDK25=%%~fJ"
if not defined JDK25 (
  echo Java 25 was not found. Packaging the default client and any prebuilt 26.x jar available.
  goto :collect_jars
)
set "OLD_JAVA_HOME=%JAVA_HOME%"
set "OLD_PATH=%PATH%"
set "JAVA_HOME=%JDK25%"
set "PATH=%JAVA_HOME%\bin;%PATH%"
set "GRADLE_VER=9.4.0"
set "GRADLE_HOME=%CD%\NovaClient\.gradle-dist\gradle-%GRADLE_VER%"
if not exist "%GRADLE_HOME%\bin\gradle.bat" (
  echo Downloading Gradle %GRADLE_VER% for Minecraft 26.x...
  powershell -NoProfile -ExecutionPolicy Bypass -Command "$ProgressPreference='SilentlyContinue'; Invoke-WebRequest 'https://services.gradle.org/distributions/gradle-%GRADLE_VER%-bin.zip' -OutFile 'gradle-26.zip'; Expand-Archive -Force 'gradle-26.zip' 'NovaClient\.gradle-dist'; Remove-Item 'gradle-26.zip'"
  if errorlevel 1 ( echo Gradle download failed. & set "JAVA_HOME=%OLD_JAVA_HOME%" & set "PATH=%OLD_PATH%" & exit /b 1 )
)
if not exist "%GRADLE_HOME%\bin\gradle.bat" ( echo Gradle %GRADLE_VER% was not installed. & set "JAVA_HOME=%OLD_JAVA_HOME%" & set "PATH=%OLD_PATH%" & exit /b 1 )
pushd NovaClient26
call "%GRADLE_HOME%\bin\gradle.bat" build --no-daemon -Pjava_version=25
set "BUILD26=%ERRORLEVEL%"
popd
set "JAVA_HOME=%OLD_JAVA_HOME%"
set "PATH=%OLD_PATH%"
if not "%BUILD26%"=="0" ( echo The 26.x client build failed. & exit /b 1 )

:collect_jars
if not exist dist\mods mkdir dist\mods
copy /Y "%MODJAR%" "dist\mods\novaclient-%VER%-mc%MC%.jar" >nul
for %%F in (NovaClient26\build\libs\novaclient-*.jar) do if exist "%%F" (
  echo %%~nxF | findstr /i /c:"-sources" /c:"-dev" >nul
  if errorlevel 1 copy /Y "%%F" "dist\mods\novaclient-%VER%-mc26.1.jar" >nul
)

rem ---- Embed default plus all prebuilt versioned client jars ----
echo.
echo ===== Packing all available client versions inside the launcher =====
if exist "NovaLauncher\res" rmdir /s /q "NovaLauncher\res"
mkdir "NovaLauncher\res\embedded"
copy /Y "%MODJAR%" "NovaLauncher\res\embedded\novaclient.jar" >nul
(
  echo version=%VER%
  echo minecraft=%MC%
  echo jar.%MC%=/embedded/novaclient.jar
) > "NovaLauncher\res\embedded\embedded.properties"
for %%F in (dist\mods\novaclient-*-mc*.jar) do if exist "%%F" call :add_embedded "%%F"

rem ---- [3/4] Build launcher ----
echo.
echo ===== [3/4] Building Nova Launcher =====
pushd NovaLauncher
if exist out rmdir /s /q out
mkdir out
dir /s /b src\*.java > sources.txt
javac --release 21 -d out @sources.txt
if errorlevel 1 ( popd & echo Launcher compilation failed. & exit /b 1 )
del sources.txt
echo Main-Class: com.nova.launcher.LauncherApp> out\manifest.txt
jar cfm NovaLauncher.jar out\manifest.txt -C out com -C res embedded
if exist app-input rmdir /s /q app-input
mkdir app-input
copy /Y NovaLauncher.jar app-input\ >nul
popd
if not exist dist mkdir dist
copy /Y NovaLauncher\NovaLauncher.jar dist\NovaLauncher.jar >nul
copy /Y NovaLauncher\NovaLauncher.jar NovaLauncher.jar >nul

rem Run the same offline launcher self-tests as .github/workflows/test.yml.
echo.
echo ===== Verifying launcher and embedded client =====
java -cp NovaLauncher\NovaLauncher.jar com.nova.launcher.SelfTest
if errorlevel 1 ( echo Launcher self-tests failed. & exit /b 1 )
if not exist "dist\NovaLauncher.jar" ( echo Runnable launcher jar is missing from dist. & exit /b 1 )

rem ---- [4/4] Package Windows app ----
echo.
echo ===== [4/4] Making NovaLauncher.exe =====
if exist dist\NovaLauncher rmdir /s /q dist\NovaLauncher
jpackage --type app-image --name NovaLauncher --input NovaLauncher\app-input --main-jar NovaLauncher.jar --main-class com.nova.launcher.LauncherApp --dest dist --add-modules ALL-MODULE-PATH
if errorlevel 1 ( echo jpackage failed. See dist\NovaLauncher.jar for the runnable launcher. & exit /b 1 )
powershell -NoProfile -ExecutionPolicy Bypass -Command "Compress-Archive -Force -Path 'dist\NovaLauncher' -DestinationPath 'dist\NovaLauncher-windows.zip'"
powershell -NoProfile -ExecutionPolicy Bypass -File tools\set-launcher-config.ps1
echo Built launcher and embedded client catalog. Minecraft 26.x is bundled only if its Gradle build succeeded.
pause
exit /b 0

:add_embedded
set "EMBED_FILE=%~nx1"
set "EMBED_MC=!EMBED_FILE:*mc=!"
set "EMBED_MC=!EMBED_MC:.jar=!"
if not defined EMBED_MC exit /b 0
set "EMBED_RESOURCE=novaclient-!EMBED_MC!.jar"
if not exist "NovaLauncher\res\embedded\!EMBED_RESOURCE!" copy /Y "%~1" "NovaLauncher\res\embedded\!EMBED_RESOURCE!" >nul
>>"NovaLauncher\res\embedded\embedded.properties" echo jar.!EMBED_MC!=/embedded/!EMBED_RESOURCE!
exit /b 0

:nojdk
echo Install JDK 21 (with javac and jpackage) and try again.
echo winget install EclipseAdoptium.Temurin.21.JDK
exit /b 1
