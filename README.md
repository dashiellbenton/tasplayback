# TASPlayback

Frame-exact recording and playback of Minecraft TAS runs. TASPlayback records the player's client-side
experience (camera, GUI, mouse, HUD, first-person rendering, sound) against **game time**, and plays it
back or exports it to video at real-time speed.

TASPlayback is a recorder/player only. It never provides savestates, tickrate control or input playback;
it observes TAS tools such as TASmod, LoTAS and vanilla `/tick`.

> Status: early development. See [PLAN.md](PLAN.md) and [PROGRESS.md](PROGRESS.md).

## Building

```bash
./gradlew buildAll
```

Jars land in `dist/`. The Gradle daemon runs on Java 25 (provisioned automatically from
`gradle/gradle-daemon-jvm.properties`); per-node Java toolchains (8 → 25) are provisioned via foojay.

| Node | Loader | Toolchain |
|---|---|---|
| `1.12.2-forge` | Forge 14.23.5.2847 + MixinBooter | RetroFuturaGradle, MCP stable_39 |
| `1.12.2-legacyfabric` | LegacyFabric (TASmod's loader) | legacy-looming, MCP names on LegacyFabric intermediary |
| `1.14.4` … `1.21.11` | Fabric | Loom (remap), Mojang mappings |
| `26.x` | Fabric | Loom (no remap), unobfuscated |

## License

LGPL-3.0-or-later. See `COPYING` and `COPYING.LESSER`.
