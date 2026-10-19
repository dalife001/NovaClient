# Nova (client + launcher)

```
Nova/
  NovaClient/     the Fabric mod (menu, freecam, accounts, skins, friends, modules...)
  NovaLauncher/   the desktop app that installs Minecraft + Fabric + Nova and starts it
  NovaLauncher.jar   ready-to-run launcher (needs Java 21+)
  .github/        auto-release pipeline
```

## One app: launcher + client together (Windows)
1. Install the JDK once: `winget install EclipseAdoptium.Temurin.21.JDK` (then reopen the window).
2. Double-click **build-everything.bat**. It builds the Nova Client mod and packs it INSIDE the launcher,
   then makes a standalone **NovaLauncher.exe**. Everything ends up in the `dist` folder.
3. Run `dist\NovaLauncher\NovaLauncher.exe`. Two ways to play:
   - **PLAY**: installs Nova Client + Fabric API + Fabric + Minecraft and starts the game (Microsoft login
     needs your Azure client ID in Settings; otherwise it starts offline).
   - **Add to Minecraft Launcher**: adds a "Nova Client" profile to the official launcher in one click.
     The official launcher handles your Microsoft login, so no Azure app is needed.
   Updates: a new build of the launcher carries the new mod; with an update URL set, the launcher can also
   fetch a newer mod by itself (it only downloads when the online version is newer than the built-in one).

## What is in the client (0.4)
- **Animated hub**: shimmering logo, parallax background with floating particles, big Play buttons,
  icon tiles, quick-join for your saved servers, tips, and a profile pill (top right).
- **Profile**: your skin on a glowing platform, Nova level + XP bar, playtime, sessions, friends, modules.
- **Module menu** (Right Shift): search, tabs, animated cards, per-module settings (sliders).
- **HUD editor**: drag elements anywhere (edges and centre snap), scroll over one to resize.
- **HUD**: FPS, Ping, CPS, Coordinates, Direction, Clock, Keystrokes (animated), Armor, Potion Status.
- **Camera**: Zoom (hold C, adjustable level) and Freecam (F6, adjustable speed).
- **Toasts** slide in for toggles, logins, updates. Custom wallpaper with slow zoom + mouse parallax.

## Quick start (no GitHub needed)
1. Build the mod: open `NovaClient/` in IntelliJ (or `gradle build`) -> `build/libs/novaclient-0.3.0.jar`
2. Run the launcher: `java -jar NovaLauncher.jar`
3. Settings -> "Local Nova jar" -> pick that jar -> **PLAY**.
   The launcher downloads Minecraft, Fabric, Fabric API (newest), copies your jar in, and starts the game.
   Not logged in = offline mode (singleplayer / offline servers only).

## Automatic updates (the "always newest" part)
1. Put this folder in a GitHub repo.
2. In the launcher Settings set **Update manifest URL** to
   `https://github.com/YOU/REPO/releases/latest/download/latest.json`
3. To ship a new version: `git tag v0.3.1 && git push --tags`.
   GitHub Actions builds everything and publishes `latest.json`.
4. Every player's launcher sees it on the next PLAY: it downloads the new Nova jar (checksum verified),
   removes the old one, refreshes Fabric API, and starts. The in-game menu also shows an "Update available" card.

What updates by itself: Nova, Fabric API, Fabric Loader, Minecraft files, even the Java runtime.
What needs you once per Minecraft release: bump `minecraft_version`, `yarn_mappings`, `fabric_version`,
`sodium_version`, `iris_version` in `NovaClient/gradle.properties`, fix any code that Mojang renamed, then tag.
When `latest.json` says a new `minecraft` version, every launcher moves to it automatically.

## Login
Create a free Azure app (personal accounts, "Allow public client flows"), paste its client ID in
launcher Settings (and `config/novaclient.json` for in-game login). Mojang must approve new apps
before Minecraft login works. Tokens are stored in plain text in `launcher.json`: keep it private.

## Package as a real app (.exe / .app / .deb)
```
cd NovaLauncher && ./build.sh
jpackage --type exe --name "Nova Launcher" --input . --main-jar NovaLauncher.jar \
         --main-class com.nova.launcher.LauncherApp --win-shortcut --win-menu
```
Use `--type dmg` on macOS, `--type deb` on Linux (jpackage must run on the target OS).

## Licenses
Sodium: PolyForm Shield 1.0.0. Iris: LGPL-3.0. Keep their notices when distributing.
