# Nova

> A quieter way to play.

Nova is a custom Minecraft client with a matching launcher, animated menus, modular HUD tools, account support, and automatic version-aware installation. It is built around Fabric and organized as a small ecosystem rather than a single mod jar.

## At a glance

| Area | Current state |
| --- | --- |
| Main client | Minecraft `1.21.4`, Yarn mappings, Java 21 |
| 26.x client | Minecraft `26.1`, Mojang names, Java 25 |
| Launcher | Java Swing app with embedded-client and download modes |
| Tested release | `1.21.4` is currently listed in `tools/tested-versions.txt` |
| Version builds | Discovered automatically from Fabric and Mojang metadata |
| Ownership | Nova-owned source and project materials are proprietary; see [Ownership and usage policy](#ownership-and-usage-policy) |

Start with [Quick start on Windows](#quick-start-on-windows), or jump to [Using Nova Launcher](#using-nova-launcher) and [Minecraft version support](#minecraft-version-support).

## Repository layout

| Path | Purpose |
| --- | --- |
| `NovaClient/` | Main Fabric client for Yarn-mapped Minecraft versions. |
| `NovaClient26/` | Separate Fabric port for Minecraft 26.x, using Mojang names and Java 25. |
| `NovaLauncher/` | Standalone Swing launcher that installs Minecraft, Fabric, Nova, optional mods, and Java runtimes. |
| `tools/` | Version discovery, multi-version builds, release manifest generation, and tested-version metadata. |
| `.github/workflows/` | Release, 26.x build, and Mojang-name porting workflows. |

## Features

The Nova client includes:

- A custom animated main menu and themed visual system.
- Singleplayer, multiplayer, account, skin, friend, resource-pack, and settings screens.
- Microsoft account authentication and offline accounts.
- A Right Shift module menu with categories and per-module settings.
- HUD modules such as FPS, ping, CPS, coordinates, direction, clock, keystrokes, armor, potion status, speed, memory, combo, and module list.
- A HUD editor for moving and resizing HUD elements.
- Zoom, toggle sprint, fullbright, custom crosshair, and low-fire modules.
- Five shared launcher/client palettes: Twilight, Lagoon, Dusk, Meadow, and Blossom.

Sodium, Iris, and Freecam are optional Modrinth downloads managed by the launcher. They are not bundled in either client jar.

## Prerequisites

For the main client and launcher:

- A full JDK 21, including `javac` and `jpackage` for the complete Windows build.
- Internet access for Gradle dependencies, Minecraft files, Fabric metadata, Modrinth downloads, and account authentication.
- Gradle installed, or let the build scripts download a project-local Gradle distribution.

For `NovaClient26/`, use JDK 25. Minecraft 26.x is unobfuscated and uses a separate Loom configuration.

On Windows, install JDK 21 with:

```powershell
winget install EclipseAdoptium.Temurin.21.JDK
```

Close and reopen the terminal after installation.

## Quick start on Windows

Build the default client jar:

```bat
cd NovaClient
build-mod.bat
```

The jar is written to `NovaClient/build/libs/`. Use the jar without `-sources` or `-dev` in its name.

Build and run the standalone launcher:

```bat
cd NovaLauncher
build.bat
java -jar NovaLauncher.jar
```

Build the complete Windows distribution, including the client embedded in the launcher:

```bat
build-everything.bat
```

This creates:

- `dist/NovaLauncher/NovaLauncher.exe`
- `dist/NovaLauncher-windows.zip`
- `dist/NovaLauncher.jar`

The app-image executable does not require Java on the player's machine. The jar requires Java 21 or newer.

## Building every Yarn-mapped version

The main client uses one source tree with `//#if`, `//#elseif`, and `//#else` markers. Gradle preprocesses those markers into `build/pp/` before compiling; files under `src/` are not modified.

Build every version discovered by the tooling:

```bat
build-all-versions.bat
```

Build only selected versions:

```bat
build-all-versions.bat 1.20.1 1.21.4
```

Results are written to `dist/mods/`. The script also writes `dist/build-report.txt` and preserves logs for failed versions. A version is included only when Fabric metadata, mappings, Fabric API, Java requirements, and compilation are available.

## Running the client in a development environment

From `NovaClient/`:

```sh
gradle runClient
gradle build
```

The repository does not include a Gradle wrapper, so `./gradlew` and `gradlew.bat` are not available unless you add one yourself. The supplied `build-mod.bat` and `build-mod.sh` scripts select or download Gradle for the project.

The default development properties are in `NovaClient/gradle.properties`:

```text
minecraft_version=1.21.4
yarn_mappings=1.21.4+build.8
loader_version=0.16.10
fabric_version=0.119.2+1.21.4
mod_version=0.7.0
```

## Minecraft version support

### Main client

The release workflow discovers stable Fabric and Yarn-supported versions starting at `1.16.5`. The actual release set is dynamic: a version can be omitted when its metadata is unavailable or the source does not compile for it.

- `1.14` and `1.15` are not supported by the main build.
- `1.21.4` is currently the only version listed in `tools/tested-versions.txt` and is marked tested in the release manifest.
- Other successful builds are experimental and use lenient mixins when configured that way.
- A version with no Nova jar can still run as plain Fabric through the launcher.
- Java requirements vary by Minecraft version; the build pipeline selects the required release automatically.

### Minecraft 26.x port

`NovaClient26/` targets Minecraft 26.x, where the game is unobfuscated and Yarn mappings are not used. It uses Mojang names, Java 25, and a separate version-number preprocessor.

Build the default local port with JDK 25:

```sh
cd NovaClient26
gradle build
```

The port is built in CI through `.github/workflows/build-26.yml` or as part of the 26.x matrix in `.github/workflows/release.yml`. It may not have complete feature parity with the main client yet.

## Using Nova Launcher

The launcher can:

- Install Minecraft, Fabric Loader, Fabric API, libraries, assets, natives, and logging configuration.
- Select any Minecraft release returned by Fabric's version metadata.
- Download the matching Nova jar from `latest.json` and verify its SHA-256 checksum.
- Run plain Fabric by disabling Nova in Settings.
- Manage optional Sodium, Iris, and Freecam downloads per Minecraft version.
- Import, enable, disable, and remove user mods, resource packs, and shader packs.
- Add a Nova profile to the official Minecraft Launcher.
- Download a suitable Adoptium Java runtime when the selected Minecraft version needs a newer Java release.
- Use a locally built Nova jar instead of downloading one.

The launcher stores its configuration and refresh token at:

- Windows: `%APPDATA%\\.novaclient\\launcher.json`
- macOS: `~/Library/Application Support/novaclient/launcher.json`
- Linux: `~/.novaclient/launcher.json`

Keep this file private because it can contain a refresh token. The game instances are stored under the platform-specific Nova home, in `instances/default/`.

## Microsoft login

Nova supports browser PKCE login, device-code login, and offline accounts. Direct Microsoft login requires:

1. A Microsoft Entra ID public client application.
2. Public client flows enabled.
3. `http://localhost` configured as a redirect URI for browser login.
4. Approval for Minecraft API access where required.
5. A Microsoft account that owns Minecraft: Java Edition.

The client ID can be entered in launcher Settings. Release builds can provide it automatically through the GitHub repository variable `AZURE_CLIENT_ID`. Adding a profile to the official Minecraft Launcher delegates authentication to that launcher and does not require Nova's client ID.

Offline mode is suitable for singleplayer and offline-compatible servers only.

## Release workflow

The `Release` workflow runs on `v*` tags, manual dispatch, and every Monday at 05:00 UTC.

Create a release from a tag:

```sh
git tag v0.7.0
git push --tags
```

The workflow:

1. Runs `tools/ci/discover.py` to create the main and 26.x build matrices.
2. Builds each version independently.
3. Uploads successful client jars and failed-version logs as artifacts.
4. Builds and self-tests `NovaLauncher.jar`.
5. Runs `tools/ci/make_manifest.py`.
6. Publishes client jars, `latest.json`, and the launcher jar to the GitHub release.

To limit a manual release run, use the `only_mc` input with comma-separated versions. `latest.json` records the release version, default Minecraft version, available builds, SHA-256 hashes, tested flags, and launcher metadata.

The `Build 26.x` workflow accepts a Minecraft 26.x version manually and uploads either the jar or a detailed failure log as an artifact. `port-mojang.yml` creates an intermediate Mojang-name source artifact; it does not publish a finished client.

## Packaging on macOS and Linux

Build the launcher jar on Unix-like systems:

```sh
cd NovaLauncher
./build.sh
java -jar NovaLauncher.jar
```

`jpackage` must run on the target operating system. Use `--type dmg` on macOS or `--type deb` on Linux. The repository currently provides a complete app-image and zip flow only through `build-everything.bat` on Windows.

## Known limitations

- A successful compilation does not mean that a Minecraft version has been tested in-game.
- Optional mods may not have a compatible Modrinth build for every Minecraft version.
- Worlds are kept in per-version instance directories and are not copied between versions.
- Initial installation, updates, version discovery, and optional-mod downloads require network access.
- The 26.x port is separate from the Yarn-based client and may lag behind it in feature coverage.

## Ownership and usage policy

**Nova Client is privately owned. All rights reserved.** Nova Client, Nova Launcher, and the Nova project as a whole are proprietary projects. Unless a file or dependency is explicitly identified as third-party material, the following are owned by the Nova project owners:

- Source code and compiled Nova binaries.
- The Nova name, branding, logos, artwork, interface designs, and visual themes.
- Documentation, screenshots, configuration, build scripts, CI workflows, release tooling, and project structure.
- Any other original materials created for or included with Nova.

No permission or license is granted to copy, fork, clone, mirror, modify, publish, redistribute, sell, sublicense, repackage, reverse engineer, incorporate, or create derivative works from Nova-owned material. This restriction applies whether the use is commercial or non-commercial and whether the material is used in whole or in part.

Use, distribution, or publication of Nova-owned material requires **prior written permission from the Nova project owners**. Unauthorized use may result in removal requests and enforcement of applicable copyright, trademark, contract, and other intellectual-property rights to the fullest extent permitted by law. Do not use or distribute this project beyond its permitted purpose without contacting the owners first.

This notice does not claim ownership of Minecraft, Fabric, Microsoft, Mojang, Modrinth, or any other third-party technology. Those technologies remain the property of their respective owners and are governed by their own terms and licenses.

Nova does not redistribute Sodium, Iris, or Freecam. The launcher downloads them from Modrinth on request, and each mod remains subject to its own license and its respective owners' rights.

---
This file was created by Nova team © 2026
