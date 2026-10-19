#!/usr/bin/env sh
# Builds NovaLauncher.jar (needs a JDK 21+). If res/embedded exists, the mod is packed inside.
set -e
cd "$(dirname "$0")"
rm -rf out && mkdir -p out
javac --release 21 -d out $(find src -name '*.java')
printf 'Main-Class: com.nova.launcher.LauncherApp\n' > out/manifest.txt
if [ -d res/embedded ]; then jar cfm NovaLauncher.jar out/manifest.txt -C out com -C res embedded
else jar cfm NovaLauncher.jar out/manifest.txt -C out com; fi
echo "Built NovaLauncher.jar  (run: java -jar NovaLauncher.jar)"
