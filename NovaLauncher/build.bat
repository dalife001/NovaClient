@echo off
cd /d %~dp0
if exist out rmdir /s /q out
mkdir out
dir /s /b src\*.java > sources.txt
javac --release 21 -d out @sources.txt || exit /b 1
echo Main-Class: com.nova.launcher.LauncherApp> out\manifest.txt
jar cfm NovaLauncher.jar out\manifest.txt -C out com
del sources.txt
echo Built NovaLauncher.jar  (run: java -jar NovaLauncher.jar)
