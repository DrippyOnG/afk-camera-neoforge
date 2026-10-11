# AFK Camera for NeoForge 26.2

A client-side recreation of the documented [AFK Cinematics](https://modrinth.com/mod/afk-cinematics)
experience by **Spunky Insaan**, for **Minecraft 26.2** and **NeoForge**.
This is an independent implementation; it uses no original mod code or assets.

## Install

1. Install NeoForge **26.2.0.88 or newer for Minecraft 26.2** and use **Java 25**.
2. [Download `afk-camera-neoforge-0.2.0.jar`](https://github.com/DrippyOnG/afk-camera-neoforge/raw/refs/heads/main/downloads/afk-camera-neoforge-0.2.0.jar) and put it in your client's `mods` folder. Replace any older AFK Camera JAR.
3. Join a world and remain idle for **25 seconds**, or run `/afkc start`.

Only the client needs the mod. It works on vanilla servers without server installation.
It does not prevent server AFK kicks, pause the world, or protect the player from damage.

## Features

- Automatic inactivity detection, plus manual start and stop.
- Randomly selected viewpoints mix character shots with views of the surrounding landscape and sky.
- Orbit, front, rear, side, close-up, wide, low-angle, overhead, and first-person viewpoints.
- Landscape, panorama, and skyward shots look away from the player from a nearby camera position.
- Smooth transitions and subtle changes to camera roll and field of view.
- Shorter camera ranges in caves and the Nether; distinct ranges in the Overworld and End.
- Minecraft's obstruction ray casts keep the camera out of solid blocks.
- Dimension-appropriate Minecraft music during cinematics, using your existing volume settings.
- Optional cinematic black bars and HUD hiding.
- Keyboard, mouse, player movement, menus, damage, death, and disconnect end cinematics.
- Restores your previous first-person or third-person view on exit.
- English and German configuration labels.

The camera only changes the local view. It does not rotate, move, or teleport your player.
Music uses Minecraft's existing tracks and resource-pack replacements; no soundtrack is bundled.
Disabling cinematic music silences music during cinematics; normal game music resumes on exit.
The motion presets change how quickly the camera moves through its shots.

## Controls and settings

Press **J** to open settings, or use **Mods → AFK Camera → Config**.
The manual-start, enable/disable, and **Preview next cinematic viewpoint** keys are unbound
by default; assign them in **Options → Controls**.
Configuration is saved locally to `config/afkcamera-client.toml`.

The default **AUTO** viewpoint randomly cycles through the twelve third-person presets,
including scenery shots, without consecutive repeats. Choose a fixed viewpoint in settings
or with `/afkc pov <name>`; it keeps its framing with gentle camera motion. First person is
available explicitly and is excluded from the automatic cycle.

| Viewpoint name | Framing |
| --- | --- |
| `auto` | Random mix of character, landscape, panorama, and skyward shots |
| `orbit` | Move around the player |
| `front`, `back`, `left_side`, `right_side` | View the player from the selected side |
| `close_up`, `wide` | Close framing or a wider view of the player and surroundings |
| `low_angle`, `overhead` | Look up at the player or down from above |
| `first_person` | Gentle motion from the player's viewpoint |
| `landscape` | Look outward across the nearby surroundings |
| `panorama` | Slowly sweep across the surroundings |
| `skyward` | Look upward toward the sky or ceiling |

| Command | Action |
| --- | --- |
| `/afkc start` | Start as soon as chat closes |
| `/afkc stop` | Stop and reset the idle timer |
| `/afkc time <seconds>` | Set the inactivity delay from 1 to 36,000 seconds |
| `/afkc time` | Show the current delay |
| `/afkc music` | Toggle cinematic music |
| `/afkc music on` / `off` | Enable or disable cinematic music |
| `/afkc motion low` / `default` / `medium` / `high` | Set camera motion |
| `/afkc pov` | Show the selected viewpoint and available names |
| `/afkc pov <name>` | Save a viewpoint and preview it when chat closes, if the mod is enabled |
| `/afkc pov next` | Select and preview the next viewpoint |
| `/afkc on` / `off` | Enable or disable automatic and manual cinematics |
| `/afkc settings` | Open the settings screen |
| `/afkc about` | Show attribution and the original mod link |

Menus suspend the AFK timer. For a world to keep running when Minecraft loses focus,
use your preferred Minecraft pause-on-lost-focus setting. Disable the game's AFK FPS
limit if you want smooth cinematics while away. This mod leaves those preferences alone.

## Development

The project pins **NeoForge 26.2.0.88**, **ModDevGradle 2.0.148**, and **Gradle 9.2.1**.
Install a Java 25 JDK, then run:

```sh
./gradlew build
./gradlew runClient
```

The installable JAR is in `build/libs/`; the `-sources.jar` is for development.
`./gradlew test` runs tests for AFK state transitions, randomized viewpoint selection,
camera continuity, motion, and dimension constraints. The first build downloads Minecraft and prepares NeoForge,
so it takes longer than subsequent builds.

In the Codex cloud machine, `./scripts/setup-cloud.sh` prepares Java and builds the mod.
`./scripts/cloud-gradle.sh build` uses the cloud proxy and system CA store without disabling TLS.
The existing repository checkout is used directly; no worktree is needed.
For a headless client smoke test, `./scripts/setup-cloud-display.sh` installs a local virtual
display without changing system packages. Start
`/workspace/.toolchains/display/usr/bin/Xvfb :99 -screen 0 1280x720x24 -nolisten tcp`, then run
`DISPLAY=:99 LIBGL_ALWAYS_SOFTWARE=1 ALSOFT_DRIVERS=null ./scripts/cloud-gradle.sh runClient`.
This uses software rendering and an audio sink; audible playback and shader compatibility
still require a desktop test.

Before release, also check in-game: automatic and manual start, mouse/key wake-up,
restoration of first-person and both third-person views, character and scenery viewpoints,
viewpoint selection and previews, collision in a small cave,
all three dimensions, configuration persistence, and music with resource packs/shaders.

## Credits and license

Original concept and documented gameplay: **AFK Cinematics by Spunky Insaan**,
[official download](https://modrinth.com/mod/afk-cinematics).
If you showcase the original mod, credit its name, author, and official download link.
This recreation's code is MIT licensed. NeoForge template files retain their notice in
`TEMPLATE_LICENSE.txt`.
