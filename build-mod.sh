#!/usr/bin/env sh
# Builds the mod without installing Gradle (downloads it next to this script).
cd "$(dirname "$0")"
command -v javac >/dev/null || { echo "Install a JDK 21 first."; exit 1; }
V=8.12.1
if [ ! -x ".gradle-dist/gradle-$V/bin/gradle" ]; then
  mkdir -p .gradle-dist
  curl -L -o gradle.zip "https://services.gradle.org/distributions/gradle-$V-bin.zip" && unzip -q gradle.zip -d .gradle-dist && rm gradle.zip
fi
".gradle-dist/gradle-$V/bin/gradle" build && echo "Done: $(pwd)/build/libs/novaclient-0.4.0.jar -> Launcher Settings > Local Nova jar"
