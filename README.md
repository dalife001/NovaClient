# Nova (client + launcher)

```
Nova/
  NovaClient/     the Fabric mod (menu, accounts, skins, friends, modules...)
  NovaLauncher/   the desktop app that installs Minecraft + Fabric + Nova and starts it
  NovaLauncher.jar   ready-to-run launcher (needs Java 21+)
  .github/        auto-release pipeline
```


## What is new in v6: every Minecraft version, built automatically

Click **minecraft 1.21.4 v** in the launcher (top left) to pick any release from **1.16.5 up to 1.21.11** (the range where the client can be built; see "Which versions" below).
Press Play and the launcher installs that Minecraft + Fabric + Fabric API and, if there is one, the Nova Client build made for that exact version.

- **Fabric only** next to a version = no Nova build for it yet. It still starts as plain Fabric, so the version is always playable.
- **Nova (experimental)** = the build compiled in CI but nobody has played it yet. Mixins that do not fit are skipped instead of crashing the game.
- The **cube icon** in the bottom dock opens **Mods**: switch any mod on/off, import jars (button or drag and drop), remove them.
  Mods that need a different Minecraft version are marked, and the launcher switches them off before starting so Fabric never shows "Incompatible mods found".
- Settings has a **Load Nova Client** switch (off = plain Fabric) if a version misbehaves.

**You never build the client by hand.** `.github/workflows/release.yml` does it on GitHub:

1. `tools/ci/discover.py` asks Fabric which stable versions exist (1.16.5 up to the newest with Yarn mappings), finds the Yarn mappings and Fabric API for each, and asks Mojang which Java each needs.
2. One build job per version compiles Nova with `gradle build -Pminecraft_version=... -Pyarn_mappings=... -Pfabric_version=... -Pjava_version=...`.
   A version whose code does not compile yet is skipped (listed in the run summary), the rest ship.
3. `tools/ci/make_manifest.py` writes `latest.json` with one entry per version (`builds`) and publishes the release.

It runs when you push a tag (`git tag v0.7.0 && git push --tags`), by hand (Actions > Release > Run workflow, optionally for a few versions), and **every Monday**,
which picks up newly released Minecraft versions without you doing anything. Put `tools/tested-versions.txt` to use: list a version there once you have played it and it loses the "experimental" label.

The code in `NovaClient/src` is written against 1.21.4. Where Mojang changed something between versions the build for that version fails and is skipped;
fixing it means adding a small version check in the code (or splitting the file) and the next run picks it up. Nothing else changes.

Where it lives: `VersionList` (picker data), `NovaManifest` (latest.json), `NovaUpdater.installNova` (right jar per version, removes the others),
`Installer` (now also unpacks old-style natives and uses Mojang's log4j config, needed for 1.14 - 1.18).
Sodium, Iris and Freecam are **not** part of the client any more. The launcher shows a **Choose your mods** window (first Play, and later under Mods > *Sodium / Iris / Freecam...*). Tick what you want; on Play the launcher downloads the matching build for the selected Minecraft version from Modrinth and removes the ones you switched off. Iris automatically brings Sodium. If a mod has no build for a version it says so and starts without it.

### Which versions get a client jar
One source tree builds for every version from **1.16.5 to 1.21.11**. Code that differs between versions sits between `//#if MC >= 2000 ... //#else ... //#endif` markers (MC = minor*100+patch, so 1.20 = 2000, 1.21.4 = 2104); Gradle's `preprocess` task keeps the matching branch before compiling. All the version-specific Minecraft calls are in `compat/Mc.java`, `gui/Gfx.java`, `gui/NovaScreen.java` and the mixins.
- 1.14 and 1.15 are not built: their Fabric API and rendering are too different (no MatrixStack / events).
- 26.x and newer have no Yarn mappings (Mojang stopped obfuscating), so they need a Loom port first.
- Custom background images need 1.20+; older versions use the built-in night sky.
- Version 1.21.4 is the only one marked *tested*. Every other version is built with lenient mixins so a changed Minecraft method is skipped instead of crashing. If a version fails to compile, the release run saves `FAILED-log-<version>` as an artifact with the Gradle log.


## What is new in v3 (Nova Serenity, client 0.6.0)

The in-game client has a new identity: calm, soft and a little out of this world. Nothing flashes, everything eases.

![Menu](docs/client-menu.png)

- **A living night sky behind every screen**: slow aurora curtains, twinkling stars, three layers of hills with mist,
  drifting fireflies and a shooting star every 17 seconds. Mouse parallax moves the layers gently.
- **The hub**: a quiet left column (logo, two big "doors" for Singleplayer / Multiplayer, six small pills, your saved
  servers under "Jump back in") in front of a slowly turning ringed planet with an orbiting moon. A time-aware
  greeting and soft tips ("Breathe in. Pick a world.") replace the old tile grid.
- **Truly rounded glass** everywhere (`Ui.rrect`, `Ui.panel`) instead of cut corners, with hairline rims and soft shadows.
- **Module menu**: a floating frosted panel, a pill that glides under the active tab, pill switches, rounded sliders.
- **HUD**: every panel is frosted glass with a thin line of light along the top edge (`module/Hud.java`).
- **Toasts** drift down and a thin line slowly drains instead of a bar.
- **5 calm palettes** (Twilight, Lagoon, Dusk, Meadow, Blossom). Same indexes 0-4 as before, so the launcher's
  `-Dnova.theme` still lines up. Change it in game: Right Shift > Visual > Theme.

![Palettes](docs/client-themes.png)

New building blocks if you want to extend it: `gui/Cosmos.java` (the sky and the planet), `Ui.rrect / rim / panel / glow /
sparkle / bar`, `AnimButton.Style.ROW` and `PILL`, and `Anim.breathe / smooth / easeInOutSine`.
Colours live in `gui/Theme.java` (accent, deep accent, warm secondary, two sky colours, three aurora colours).

Note: the scene is drawn with plain GUI fills (a few thousand rectangles per frame). It is fine on normal PCs; on a very weak
machine you can lower the aurora cost by raising `step` in `Cosmos.aurora`.

## What is new in v2 (Nova Aurora design)

**Launcher**: rebuilt UI with a sidebar, a hero card (your skin on an animated night scene), profile rows with a big Play button, a status column, a Console page and a dark Settings window with a memory slider. Login / install / update logic is unchanged.

**5 colour themes** (Nova, Ocean, Sunset, Emerald, Rose): click the palette button in the launcher sidebar. The choice is saved and passed to the game (`-Dnova.theme`), so the in-game menu, HUD panels and module list use the same colours. You can also change it in the game: Right Shift > Visual > Theme.

![Themes](docs/launcher-themes.png)

**New modules (Right Shift menu)**

| Module | Tab | What it does |
|---|---|---|
| Fullbright | Visual | Max brightness everywhere. Restores your old brightness when off or on exit. |
| Crosshair | Visual | Custom crosshair: size, gap, thickness, colour, centre dot. Replaces the vanilla one. |
| Low Fire | Visual | Fire overlay no longer covers your view (adjustable). |
| Theme | Visual | Palette 0-4 for the whole client. |
| Speed | HUD | Blocks per second. |
| Memory | HUD | RAM used / max. |
| Combo | HUD | Hits in a row, resets when you get hit or after 3 s. |
| Module List | HUD | Shows which features are on, with accent bars. |

HUD panels (Potion Status, Armor Status, Coordinates) use titled glass panels. Shared look lives in `module/Hud.java` (`Hud.titled(...)`). New HUD modules are off by default so your layout does not change; turn them on in the menu and place them with the HUD editor.

![Launcher](docs/launcher-home.png)

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
- **Camera**: Zoom (hold C, adjustable level). Freecam is available as an optional mod from the launcher.
- **Toasts** slide in for toggles, logins, updates. Custom wallpaper with slow zoom + mouse parallax.

## Quick start (no GitHub needed)
1. Build the mod: open `NovaClient/` in IntelliJ (or `gradle build`) -> `build/libs/novaclient-0.6.0.jar`
2. Run the launcher: `java -jar NovaLauncher.jar`
3. Settings -> "Local Nova jar" -> pick that jar -> **PLAY**.
   The launcher downloads Minecraft, Fabric, Fabric API (newest), copies your jar in, and starts the game.
   Not logged in = offline mode (singleplayer / offline servers only).

## Automatic updates (the "always newest" part)
1. Put this folder in a GitHub repo.
2. In the launcher Settings set **Update manifest URL** to
   `https://github.com/YOU/REPO/releases/latest/download/latest.json`
3. To ship a new version: `git tag v0.6.1 && git push --tags`.
   GitHub Actions builds everything and publishes `latest.json`.
4. Every player's launcher sees it on the next PLAY: it downloads the new Nova jar (checksum verified),
   removes the old one, refreshes Fabric API, and starts. The in-game menu also shows an "Update available" card.

What updates by itself: Nova, Fabric API, Fabric Loader, Minecraft files, even the Java runtime.
What needs you once per Minecraft release: nothing for the pipeline (it finds the new version by itself). If the new version does not
compile, fix the code that Mojang renamed and tag again. `latest.json` names the default Minecraft version, and every launcher set to "automatic" moves to it.

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
Sodium, Iris and Freecam are downloaded from Modrinth on request and keep their own licenses; Nova does not redistribute them.
