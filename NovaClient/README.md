# Nova Client 0.2 (Fabric, Minecraft 1.21.4, Java 21)

## Build / run
1. Install JDK 21; open the folder in IntelliJ IDEA (or run `gradle wrapper` once).
2. `./gradlew runClient` to test, `./gradlew build` for the jar in `build/libs/`.
   Sodium and Iris are embedded in the jar (jar-in-jar), so one jar is enough
   (you still need Fabric Loader + Fabric API in the profile).

## Features
- Custom main menu (front page) with animated background
- Sodium + Iris bundled; "Shaders" button opens Iris
- Freecam (F6): fly the camera around while your player stays put
- Microsoft login (device-code flow, no password typed in the game)
- Skin changer (uploads a 64x64 PNG to your profile)
- Resource/texture packs screen
- Friends list (verified against Mojang, shows who is in your current game)
- Module menu (Right Shift): FPS, Coordinates, Keystrokes, Auto Sprint, Freecam

## Microsoft login setup (required once)
1. Create an app at https://portal.azure.com -> App registrations
   (accounts: "Personal Microsoft accounts only"), enable "Allow public client flows".
2. Copy its Application (client) ID into `.minecraft/config/novaclient.json`
   as `"azureClientId"`.
3. Mojang requires new apps to be approved before the Minecraft API accepts them
   (help.minecraft.net -> "Minecraft Java Edition Game Server / API access" form).
   Until then login fails at the last step with an "Invalid app registration" error.
`config/novaclient.json` stores a refresh token. Keep it private.

## Licenses
Sodium: PolyForm Shield 1.0.0. Iris: LGPL-3.0. Keep their license notices
if you distribute the jar and do not present it as a replacement for them.
