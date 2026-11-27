# TASPlayback — Implementation Plan

Status: **APPROVED 2026-11-27.** Decisions recorded in §12.

**Scope guard:** TASPlayback is a *recording and playback* mod only. It never provides savestates, tickrate control, frame advance or input playback; it only observes the TAS tools that do (TASmod, LoTAS, vanilla `/tick`).

TASPlayback records what the player sees and hears on the client, keyed to game time (ticks + partial ticks), not wall-clock time. It then plays that back, or renders it to video, at real-time 20 TPS (or any other speed), with exact camera, GUI, mouse, HUD, first-person rendering and sample-accurate sound. Rewound savestate branches are dropped automatically.

---

## 0. Toolchain facts, re-checked 2026-11-27

I checked these against the live Maven and Gradle metadata, not the prompt. Some of them differ from the prompt.

| Item | Prompt said | Actual today | Plan |
|---|---|---|---|
| Stonecutter | 0.8.x | **0.10** (0.9.8 and 0.8.4 are older lines) | Pin `dev.kikugie.stonecutter` **0.10**. Use the v2 wiki docs only. |
| Fabric Loom | 1.17 | **1.19.4** stable (1.20 is in alpha) | Pin **1.19.4**. |
| Loom plugin split | `fabric-loom` (no remap) for 26.x, `fabric-loom-remap` before that | Same | Use **`dev.kikugie.loom-back-compat` 0.4.3**. It's Stonecutter's companion plugin, and it picks `net.fabricmc.fabric-loom` for ≥26 and `net.fabricmc.fabric-loom-remap` for <26. |
| Gradle | 9.6.0 | **9.9.2** current. Loom 1.19 is built with 9.8. CleanroomMC TemplateDevEnv (RFG) is on **9.7**. | **M0 result:** wrapper **9.9.1** (what the official Stonecutter template uses) drives Loom 1.19.4, RFG 2.0.6 and legacy-looming 1.16.1 in one build. |
| RetroFuturaGradle | — | **2.0.6** (RFG's own build uses Gradle 9.2; TemplateDevEnv pins 2.0.3 on Gradle 9.7) | Pin **2.0.6**, on a Java 21 Gradle JVM. Minecraft compiles and runs on a Java 8 toolchain. |
| MixinBooter | — | **12.5** | Use it for 1.12.2 mixins. |
| Forge 1.12.2 | 14.23.5.2847 | Same (final build) | Same. |
| Fabric Loader | — | **0.20.1** | Build against it. At runtime require `>=0.15` on older nodes, so TAS packs with older loaders still work. |
| Fabric API | — | Available on every node (for example `0.141.6+1.21.11` and `0.167.0+26.3`) | Optional. I'll use only a minimal set (keybinds, client lifecycle events) and fall back to mixins where an API module doesn't exist on an old node. |
| Minecraft | latest 26.3 | Release manifest: 26.3, 26.2, 26.1.2, 26.1.1, 26.1, 1.21.11 … | Same as the prompt. |
| Gradle JVM | Java 25 for 26.1+ | Same, and **Stonecutter 0.10 itself requires a Java 25 daemon** | **The Gradle daemon runs on Java 25.** That satisfies Loom on 26.x, and RFG/Loom on older nodes are happy on 25 too (M0 checks this). Toolchains provide 8, 16, 17, 21 and 25 for compiling and running each node. |

### Findings that need your decision (see §12 "Open questions")

1. **TASmod for 1.12.2 runs on LegacyFabric, not Forge.** *(Resolved: a `1.12.2-legacyfabric` node is in scope.)* Released TASmod (Beta2.x) is built with `legacy-looming` and `net.legacyfabric:yarn:1.12.2+build.mcp`, which gives MCP names on the LegacyFabric intermediary. Our node uses the same toolchain. TASmod's `develop` branch has moved to OrnitheMC `ploceus` with gen2 intermediary, which is incompatible with LegacyFabric's. If a TASmod release follows it, we add a `1.12.2-ornithe` node (buildscript only; the source is MCP-named either way).
2. **ReplayMod is GPL-3.0-or-later.** I will **not copy any of its code**. The design ideas (packet capture, a fake connection that feeds a client world) are general techniques, and I'll write my own implementation from the decompiled game. Copying ReplayMod code would force this project to be GPL. I need you to pick the project license; I propose **LGPL-3.0** (or MIT).

---

## 1. Repository layout

```
tasplayback/
├─ settings.gradle.kts          Stonecutter 0.10 + loom-back-compat, node registration
├─ stonecutter.gradle.kts       controller: active node, buildAll/collect tasks, constants
├─ build.fabric.gradle.kts      buildscript for all Fabric nodes (loom-back-compat)
├─ build.forge.gradle.kts       buildscript for 1.12.2-forge (RFG)
├─ gradle.properties            mod id/version, shared deps
├─ versions/<node>/gradle.properties    per-node MC version, java, deps, compat range
├─ core/                        plain Gradle subproject, Java 8, no Minecraft imports
│   └─ src/main/java/dev/dbenton/tasplayback/core/...
│       format/   .tasp container, stream codecs, chunked writer and recovery
│       timeline/ GameTime, frame index, sampling, interpolation
│       branch/   segments, rewind graph, canonical-timeline resolution
│       audio/    pure-Java Vorbis decode (vendored), mixer, attenuation/panning, WAV
│       export/   ffmpeg command builder, pipe writer, render queue model
│       settings/ settings model (JSON)
│       model/    version-neutral state records (camera, GUI, HUD, sound...)
├─ platform-api/                Java 8 interfaces the version code implements
│       CameraSource/CameraSink, GuiStateSource/ScreenReconstructor, HudSource,
│       SoundTap/SoundPlayer, PacketTap/ReplayConnection, FrameCapture,
│       InputSource, ResourceAccess (for OGG lookup), TasToolBridge
├─ src/main/java/...            Stonecutter-versioned Fabric implementation (1.14.4 → 26.3)
│   └─ dev/dbenton/tasplayback/fabric/{common, era/...}
├─ src/main/resources/          fabric.mod.json (templated), mixins json, assets
├─ legacy/                      1.12.2 implementation (MCP names, LWJGL2), shares core + platform-api
│   └─ src/main/java/dev/dbenton/tasplayback/legacy/...
└─ versions/                    created by Stonecutter
```

**How era-specific code is organized.** Small differences use `//? if >=1.20 {` comments. Large differences go in separate files under `fabric/era/<era>/` packages, for example `gui/v1_14`, `gui/v1_20` (GuiGraphics) and `gui/v1_21_6` (deferred GUI rendering). The Fabric buildscript excludes the packages that don't apply to the current node using `sc.eval(sc.current.version, ...)`, and a small interface in `common` selects the implementation. Nothing ends up with a giant inline conditional.

**How `core` ships.** `core` and `platform-api` are compiled once, at Java 8 bytecode, and bundled into every jar: Loom `include` (jar-in-jar) on Fabric, and shaded into the RFG jar on Forge. They use a unique package, so no relocation is needed.

---

## 2. Build design (Milestone 0 risk)

- **Stonecutter 0.10 flat setup with split buildscripts**, the approach the Stonecutter wiki recommends for multi-loader projects. Each node is registered with its own buildscript:
  `version("1.12.2-forge", "1.12.2").buildscript = "build.forge.gradle.kts"`, with the Fabric nodes using `build.fabric.gradle.kts`.
- The **1.12.2 code is not Stonecutter-preprocessed.** It lives in `legacy/`. The `1.12.2-forge` node's buildscript points its source set at `legacy/src` (plus `core` and `platform-api`). Stonecutter only provides the node and the unified tasks.
- **Plugin isolation.** Loom and RFG both bring ASM, Guava and mapping libraries. Neither plugin is put on the settings or root classpath. Each is applied with its version in its own buildscript, so Gradle gives each project its own plugin classloader. M0 proves this works.
- **One command:** `./gradlew buildAll`. It's a root task registered in `stonecutter.gradle.kts` that depends on every node's `collectJar` (via `stonecutter.tasks.names`) plus `:core:test`, and collects the jars into `dist/`. **M0 result: works; no fallback needed.** The 1.12.2 nodes live in a Stonecutter `legacy` branch with per-node buildscripts (`legacy/build.forge.gradle.kts`, `legacy/build.legacyfabric.gradle.kts`).
- **Fallbacks, in order, if Loom and RFG can't share a build:**
  (a) Run 1.12.2 as an **included build** (`includeBuild("legacy")`) that compiles `core` from shared source dirs; `buildAll` depends on the included build's `build` task. It's still one command and one wrapper, but has its own plugin classpath. (This was the prompt's option (b). I'd try it first because it's the cleanest isolation.)
  (b) **Unimined 2.0.2** for 1.12.2. It's a single toolchain-agnostic plugin and is known to cooperate with Stonecutter, but its MCP/Forge 1.12.2 support is less battle-tested than RFG's.
  (c) Per-node Gradle versions. This is the last resort, because it breaks "one command" unless wrapped in a script.
  I'll document the outcome in `PROGRESS.md`.
- **Mappings:** Mojang official mappings on every Fabric node, 1.14.4 → 1.21.11 (`loom.officialMojangMappings()`). 26.x is unobfuscated, so no mappings. 1.12.2 uses MCP `stable_39` through RFG.
- **Java per node**, through toolchains: 1.12.2 and 1.14–1.16 → 8; 1.17 → 16; 1.18–1.20.4 → 17; 1.20.5–1.21.11 → 21; 26.x → 25. `core` and `platform-api` target Java 8 (`--release 8`).
- **Testing old nodes.** Loom 1.19 with MC 1.14.4 on official mappings and Java 8 runs is an older combination I want to see work in M0, not assume. If it fails, the fallback is an older Loom (`fabric-loom-remap` with a pinned older version) for that node only, selected through `loomx.loom_version` in that node's properties.

---

## 3. Version nodes

One jar per API breakpoint. The `fabric.mod.json` `minecraft` range is templated per node. **Each range is a hypothesis until I've checked it against decompiled sources (`genSources`) of the range endpoints. I'll split a node wherever they differ.**

| Node | Java | Proposed range | Key breaks to verify |
|---|---|---|---|
| 1.12.2-forge | 8 | 1.12.2 | MCP names, LWJGL2, `EntityRenderer.orientCamera`, `SoundManager.playSound` (paulscode) |
| 1.14.4 | 8 | 1.14.4 | first Mojmap version, `Camera` class, fixed-function GL |
| 1.15.2 | 8 | 1.15–1.15.2 | `PoseStack` everywhere, `RenderSystem` |
| 1.16.5 | 8 | 1.16.2–1.16.5 (1.16.0/1.16.1 to check) | dimension and registry changes affect world reconstruction |
| 1.17.1 | 16 | 1.17–1.17.1 | core shaders, `ShaderInstance` |
| 1.18.2 | 17 | 1.18–1.18.2 | |
| 1.19.2 | 17 | 1.19–1.19.2 | chat signing changes the chat packets |
| 1.19.4 | 17 | 1.19.3–1.19.4 | widget/GUI refactor, `Component` tooltips |
| 1.20.1 | 17 | 1.20–1.20.1 | `GuiGraphics` |
| 1.20.4 | 17 | 1.20.2–1.20.4 | configuration network phase (1.20.2), `/tick` (1.20.3) |
| 1.20.6 | 21 | 1.20.5–1.20.6 | data components, `StreamCodec` (item serialization) |
| 1.21.1 | 21 | 1.21–1.21.1 | |
| 1.21.4 | 21 | 1.21.2–1.21.4 | render pipeline changes, recipe book rewrite (1.21.2: server-driven recipe display) |
| 1.21.5 | 21 | 1.21.5 | `RenderPipeline` / GPU device abstraction |
| 1.21.8 | 21 | 1.21.6–1.21.8 | deferred GUI rendering (`GuiRenderState`) |
| 1.21.10 | 21 | 1.21.9–1.21.10 | input event records (`KeyEvent`/`MouseButtonEvent`) |
| 1.21.11 | 21 | 1.21.11 | last obfuscated version |
| 26.1.x | 25 | 26.1–26.1.2 | unobfuscated, no-remap Loom |
| 26.2 | 25 | 26.2 | |
| 26.3 | 25 | 26.3 | SDL3: cursor/window/input path rewritten |
| 1.12.2-legacyfabric | 8 | 1.12.2 | TASmod compatibility (approved; built from M2 alongside Forge) |

The **1.12.2 implementation (§3.3)** is a separate code tree written against MCP names. If the LegacyFabric node is approved, the logic is written once against a thin internal adapter, and two small shims bind it: Forge/MCP and LegacyFabric/legacy-yarn. The LegacyFabric node would use LegacyFabric's Loom setup. That's a separate M0-style risk.

---

## 4. Timeline and recording format (`core`)

### 4.1 Game time
- `GameTime { long tick; float partial; }`, plus a global monotonically increasing `frameIndex` and a `segmentId`.
- The ordering key is `(segmentId, tick, partial, frameIndex)`. `frameIndex` breaks ties when several frames render at the same game time, which happens all the time at low tickrates and while frozen. Wall-clock nanoseconds are stored per frame **only as metadata**.
- **Frozen or paused game time.** When the tickrate is 0, or `/tick freeze` is on, many frames share one `GameTime`. Playback speed applies to game time, so these frames would collapse to zero duration. Playback offers a "frozen-span policy": *collapse* (the default for TAS output), *hold at recorded wall duration*, or *hold N frames*. GUI-only interaction while frozen (moving items in a paused inventory) would otherwise vanish, so that span uses a secondary "GUI clock" based on frame count.

### 4.2 `.tasp` container
A zip whose entries match the prompt: `meta.json`, `segments.json`, `world.bin`, `frames.bin`, `gui.bin`, `hud.bin`, `sounds.bin`, `markers.json`, and optionally `guicap.bin`.

**Crash safety.** During recording, data goes into a directory `<name>.tasp.part/`. Each stream is a sequence of **chunks**: a header with magic, stream id, version, chunk sequence number, first and last time key, raw and compressed length, and a CRC32, followed by a Deflate-compressed body (`java.util.zip`, so `core` has no dependencies). Chunks are flushed every N frames or 1 second of wall time. Finalizing packs the directory into the `.tasp` zip. On startup, a leftover `.part` directory is offered for recovery: every chunk that's complete and passes its CRC is kept.

**Writer thread.** The writer runs on a dedicated thread behind a **bounded** queue. Producers on the render and tick threads only copy primitive values into pooled record objects. If the queue fills up, the producer still never blocks: it drops the GUI-capture stream first, then shows a visible "recording overloaded" warning and counts dropped records. Core streams (frames, sound, world) get a large reserved budget and trigger the warning long before any loss. **Blocking the game thread is never an option**, per hard requirement 1.

**Encoding.**
- `frames.bin`: each field is delta-encoded against the previous frame using zig-zag varints. Floats are stored as raw IEEE bits, XOR'd with the previous value (lossless, so camera angles are bit-exact). There's a keyframe every K frames.
- `gui.bin` and `hud.bin`: an event stream with periodic full-state keyframes, every 1 s of game time and on every screen open, for seeking.
- Every stream has its own version number. `core` reads all streams without Minecraft present.

**Version-specific payloads.** Item stacks, text `Component`s and packets are **opaque version-tagged blobs** to `core` (`{codecId, mcDataVersion, bytes}`). The version layer encodes them with the game's own codec: NBT before 1.20.5, `StreamCodec`/registry-aware after. A recording made on node X plays back on node X. Cross-version playback is a non-goal, and the file says so in `meta.json`.

### 4.3 Per-frame record (`frames.bin`)
Time key; eye position (double ×3, already interpolated); yaw, pitch, roll; FOV (final value plus base, modifiers, dynamic-FOV inputs); bob inputs (walk dist, bob, prev values); hurt-tilt time and direction; portal and nausea intensity; perspective; detached-camera flag; hand state for both hands (swing progress, equip progress and the old values, use-item ticks, the item in hand as a reference); hit result (type, block pos and face, entity id); block-break progress; raw mouse position (double, window coords); GUI-scaled mouse position; mouse buttons held; keys held (for the input overlay); window and framebuffer size; GUI scale; and an optional framebuffer checksum or thumbnail when debug or verification capture is on.

### 4.4 Segments and branches
`segments.json` is a DAG. Each segment has a start `GameTime`, an optional `parent` (segment, GameTime) link point, a cause (`start`, `savestate-load`, `world-reload`, `tickrate-change`, `manual-rewind`, `tasmod-playback-restart`, ...) and its detection source.

**Canonical resolution.** Walk back from the final segment. For each segment, keep its parent's frames only up to the link point. Every frame that isn't on this path is a "discarded branch". The UI can promote a discarded branch, which re-roots the resolution, and this is persisted as an override in `segments.json`. Fully unit-tested, including nested rewinds and rewinds to a point inside an already discarded branch.

---

## 5. Recording rules: never change the game

Each hook is **read-only and side-effect-free**. The checklist for every mixin:

- No calls that consume `RandomSource`/`Random`. Sounds are captured *after* the game resolved the variant: hook `SoundEngine.play` after `instance.resolve(...)` / `getSound()` and read the chosen `Sound`.
- No calls that load chunks, resources or textures, query the server, or mutate any state. In particular: **don't call `getTooltipLines()` to "check" a tooltip.** Tooltip text is captured from the arguments of the render call the game was already making.
- No allocations or computation large enough to change frame pacing meaningfully. Recording does change wall-clock frame time, but never game logic. At very low tickrates, frame time doesn't affect logic. **On normal 20 TPS, frame time doesn't affect ticks either; the timer is wall-clock-driven, and that's unchanged.**
- No reads of `Minecraft#level` objects that trigger lazy initialization.
- Capture happens through `@Inject`/`@ModifyVariable` with `locals`/`@Local` where possible. No `@Redirect` or `@Overwrite` while recording (they could clash with TAS mods).
- **The determinism test (§9) is the gate:** the same scripted input with the recorder off and on must give identical final state hashes. This test runs per node in CI wherever a client can boot.

---

## 6. Playback design

### 6.1 World
A replay-owned `ClientPacketListener` (or the equivalent per era) is fed from `world.bin` through a fake `Connection`. This is my own implementation, not ReplayMod's. Packets are captured at the clientbound decode boundary along with their game-time key. 1.20.2+ also records the configuration-phase packets. The **local player** gets synthesized "self state" records every tick (position, rotation, pose, equipment, swing), because the player's own movement never arrives as clientbound packets. On playback the local player is a replay-controlled entity, and the camera is driven by `frames.bin`. Seeking restores the nearest world keyframe, by replaying from the segment start or from the periodic world snapshots stored as chunk-data bursts.

### 6.2 Camera and first person
Mixins at `Camera.setup` / `GameRenderer.renderLevel` / `bobView` / `bobHurt` / `getFov` / `ItemInHandRenderer` (and the equivalents per era) **override the inputs** with the sampled frame, so the vanilla renderer produces the identical image. Sampling at the target FPS works like this: find the two recorded frames around the target game time, interpolate angles along the shortest arc (yaw unwrapped) and interpolate positions linearly. Discrete fields (perspective, slot, screen, hit result) use step semantics. **Exact POV at the recording's own frame times is bit-exact. In-between frames are interpolated and documented as such.**

### 6.3 GUI reconstruction
As the prompt says, rebuild the real screen: create the recorded screen class on a replay-owned `AbstractContainerMenu` with the recorded slots and carried stack, restore widget state through per-screen "state adapters" (recipe book, creative tab and scroll, text fields with cursor and selection, the beacon, villager offers, ...), and call `render` with the recorded mouse position. Each Tier-1 screen gets an adapter with a recorder half and a restorer half. In debug mode, the hovered slot and tooltip text are cross-checked against the recording. Unknown screens fall back to GUI-layer capture (`guicap.bin`, off by default).

**Resolution.** Exporting at the recorded framebuffer size and GUI scale is exact, and integer multiples are exact. Other sizes re-anchor the mouse to slot or widget positions; that's documented as approximate.

### 6.4 Sound
- **Live playback:** recorded instances are rescheduled through the sound engine, built as replay `SoundInstance`s that carry the **exact resolved `Sound`**, so there's no re-roll. Pitch is preserved by default, with an optional pitch-shift for slow-motion. Tickable sounds replay their recorded per-tick position, volume and pitch updates.
- **Offline mix (export):** look up the OGG through the game's resource manager (same packs, and their hashes are checked against `meta.json`), decode it with a vendored pure-Java Vorbis decoder (license checked before vendoring; JOrbis is LGPL), and mix at 48 kHz using **the game's actual OpenAL configuration**: the distance model the game sets per era (linear distance clamped, with reference/max distance from the sound's attenuation distance), and listener orientation from the recorded per-frame pose. Event times map to samples as `round((t_game - t0) / speed / tickLength * 48000)`. Tested: a click on frame N lands at sample `N / fps * 48000`.
  - **Honest limit:** timing will be sample-accurate. The *mix* will match OpenAL Soft's documented distance model and its default stereo panning law. I'll implement that model and validate against captured OpenAL output, but I can't promise bit-identical output to OpenAL Soft's resampler and filters. I'll report the measured difference.

### 6.5 Export
Render with fixed game-time steps per output frame. Read back asynchronously: double-buffered PBOs on GL nodes, and the `GpuDevice`/`GpuBuffer` mapped read-back API on 1.21.5+ and 26.x (to be verified per node). Frames are piped as raw RGBA into an external `ffmpeg` process (codec, CRF and preset are configurable; an image-sequence mode is available). The offline WAV is muxed at the end. The ffmpeg command builder lives in `core` and is unit-tested.

### 6.6 UI
A timeline viewer (play/pause, speed, frame step, scrub with keyframes, markers, the segment and branch list with "discarded" toggles), camera modes (Exact POV, free camera with a player-entity stand-in, keyframed paths in M8), the cursor overlay (vanilla-style arrow sprite, size, skin, click ripple, held buttons) and the input overlay. Each era's UI widgets are written against a tiny internal widget layer, so that UI code isn't forked 20 times.

---

## 7. Savestates and re-records (soft dependencies)

Research before I write any integration. What I know today, to confirm in M7:

- **1.12.2:** TASmod (LegacyFabric: savestates, tickrate, input playback) and LoTAS (Forge: savestates, tickrate changer). Detection works through their savestate load events or classes, via reflection or optional mixins. There's also a generic fallback: an integrated-server world reload combined with a change in tick counter or player position → candidate rewind.
- **1.14.4+:** LoTAS (Fabric) and its successors, plus tickrate mods. Same pattern.
- **1.20.3+:** vanilla `/tick rate | freeze | step | sprint`. The tickrate history goes into `meta.json`, and freeze/step boundaries go into the timeline.
- **Manual fallback:** a "mark rewind here" keybind, and branch promotion in the UI.

---

## 8. Determinism-sensitive integrations

Every TAS-tool integration is **read-only**. TASPlayback never drives savestates or tickrate itself during recording.

---

## 9. Testing and verification

- **`core` (JUnit 5):** codec round-trips (fuzzed), interpolation, branch resolution (DAG cases), crash recovery (truncated and corrupt chunks), the mixer (impulse timing at sample `N/fps*48000`, panning L/R, distance attenuation curves), and the ffmpeg command builder.
- **Per-node client smoke test:** a dev-only "scripted session" entrypoint (only in the `runClient` dev environment, never in the release jar). It creates a fixed-seed superflat world, then drives scripted inputs per tick: open the inventory, toggle the recipe book, move items, hover, look around, play sounds. It records ~10 s, stores reference framebuffer thumbnails, then plays the recording back and compares within a tolerance.
- **Determinism test:** the same scripted session with the recorder on and off. The test asserts identical final hashes of player position and rotation, inventory, world RNG-derived state (the next value of a cloned level random, entity UUIDs) and the block-change log.
- **CI:** a GitHub Actions matrix that builds all nodes via `buildAll`, runs the `core` tests, and runs client smoke and determinism tests under **Xvfb + Mesa llvmpipe** where a client boots (I'll measure which nodes actually boot headless; 26.3/SDL3 is the risk there).
- **Here in this cloud container:** no GPU. I'll try Xvfb + llvmpipe; if clients can't boot here, I'll say so and rely on CI or your machine for client verification.

---

## 10. Milestones and acceptance criteria

| # | Deliverable | Done when |
|---|---|---|
| **0** | Build skeleton: an empty mod with a log line on load and a `/tasp` stub, on `1.12.2-forge`, `1.14.4`, `1.16.5`, `1.20.1`, `1.21.11`, `26.3`. Settle the Gradle/RFG question. | `./gradlew buildAll` produces 6 jars, plus `runClient` launch evidence per node (log line) where a client can boot. **Stop and show you.** |
| 1 | `core`: format, chunked writer and recovery, timeline, codecs, branch resolution, interpolation, mixer skeleton, ffmpeg builder. | All unit tests green, with coverage of every codec. |
| 2 | Vertical slice on **1.21.11** and **1.12.2**: record and play back exact camera, HUD/hotbar and cursor overlay; world reconstruction; segment detection basics. | Smoke test: playback thumbnails match within tolerance; determinism test passes. |
| 3 | GUI Tier 1 on both nodes. | Each Tier-1 screen recorded and reconstructed with the hover/tooltip cross-check clean. |
| 4 | Sound: live playback, then the offline mixer. | Sample-alignment test passes; A/B listening check documented. |
| 5 | Video exporter (PBO/GPU readback, ffmpeg pipe, WAV mux). | Exported 60 fps video from a low-tickrate recording, A/V sync verified with a click track. |
| 6 | Port to every Fabric node; split nodes where sources differ. | The compatibility matrix in `PROGRESS.md` has every node at build ✓ and smoke ✓/✗ with reasons. |
| 7 | Savestate and tickrate integrations, and the canonical-timeline UI. | Rewind scenarios per tool are covered by tests or documented runs. |
| 8 | Tier 2 GUIs, GUI-capture fallback, free and keyframed cameras, polish, CI, README and user docs. | CI green; docs complete. |

After every milestone: update `PROGRESS.md`, commit, and push to `claude/vibrant-bohr-v4r2ez`. Each report covers what works, how I verified it, and the open risks. If something is infeasible on a version, I'll name the blocker and propose an alternative, never quietly downgrade.

---

## 11. Risks, stated plainly

1. **Scope.** This is a ReplayMod-sized project times ~21 nodes, plus GUI reconstruction that ReplayMod never attempted. Milestones 2–3 on two nodes are where the real design gets proven. Porting to 19 more nodes (M6) is large but mechanical once that's done.
2. **Client verification in this environment.** Without a GPU or a display, I may not be able to boot every client here. Build-level verification is guaranteed; runtime verification depends on Xvfb/llvmpipe working.
3. **World reconstruction across 20 protocol versions** is the biggest single engineering cost. Packet capture is version-specific by nature.
4. **Offline audio** will be exact in timing, and only near-exact in mix (§6.4).
5. **Loom 1.19 on 1.14.4–1.16.5** with official mappings and Java 8 runs needs to be proven in M0.
6. **SDL3 on 26.3** changes cursor and input capture. 26.3's input classes need fresh decompiled reading.
7. **The GUI layer differs per era.** 1.21.6+ deferred GUI rendering means the "render with recorded mouse" step changes shape there, though the approach stays the same.

---

## 12. Decisions (answered 2026-11-27)

1. **`1.12.2-legacyfabric` node: yes.** Implemented in Milestone 2 alongside `1.12.2-forge`, so TASmod users are covered from the first vertical slice. In M0 the build skeleton already proves it builds.
2. **License: LGPL-3.0-or-later** (`COPYING`, `COPYING.LESSER`). No ReplayMod code is copied.
3. **Package / mod id:** `dev.dbenton.tasplayback` / `tasplayback`.
4. **Frozen game-time spans are skipped** on playback/export by default ("collapse"); the hold options stay available as settings.
