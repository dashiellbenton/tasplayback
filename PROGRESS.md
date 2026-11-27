# TASPlayback — Progress

| Milestone | Status |
|---|---|
| Plan | Approved 2026-11-27 (decisions in PLAN.md §12) |
| 0 Build skeleton | Done |
| 1 core | Done |
| **2 Vertical slice (1.21.11, 1.12.2-forge, 1.12.2-legacyfabric)** | **Done** |
| 3 GUI Tier 1 | not started |
| 4 Sound | not started |
| 5 Exporter | not started |
| 6 Port all Fabric nodes | not started |
| 7 Savestate/tickrate integrations | not started |
| 8 Tier 2, polish, CI, docs | not started |

## Compatibility matrix

"Boots" = the dev client started headless (Xvfb + Mesa llvmpipe), logged TASPlayback's init line, logged the
smoke mixin line (proving mixin + remapping work), and reached the title screen with no crash report.

| Node | Loader / toolchain | Java | Jar builds | Mixin target namespace in jar | Boots | Record + replay (M2) | Notes |
|---|---|---|---|---|---|---|---|
| 1.12.2-forge | Forge 14.23.5.2847, RFG 2.0.6, MixinBooter 12.5 | 8 | ✅ | MCP class names + SRG refmap | ✅ | ✅ avg diff 0.04 | |
| 1.12.2-legacyfabric | LegacyFabric, legacy-looming 1.16.1 (Loom 1.16.3), `net.legacyfabric:yarn:1.12.2+build.mcp` | 8 | ✅ | `net/minecraft/class_1600` (LegacyFabric intermediary, same as released TASmod) | ✅ | ✅ avg diff 0.03 | |
| 1.14.4 | Fabric, Loom 1.19.4 (remap), Mojang mappings | 8 | ✅ | `class_310` (Fabric intermediary) | ✅ | not yet (M6) | |
| 1.16.5 | same | 8 | ✅ | intermediary | ✅ | not yet (M6) | |
| 1.20.1 | same | 17 | ✅ | intermediary | ✅ | not yet (M6) | |
| 1.21.11 | same | 21 | ✅ | `class_310` | ✅ | ✅ avg diff 0.51 | |
| 26.3 | Fabric, Loom 1.19.4 (no remap), unobfuscated | 25 | ✅ | `net/minecraft/client/Minecraft` | ✅ | not yet (M6) | headless needs `SDL_VIDEO_FORCE_EGL=1` (see below) |

Evidence: `docs/m0/smoke-contact-sheet.jpg` (title screens of all seven, in table order).

## Milestone 2 report: vertical slice on 1.21.11, 1.12.2-forge and 1.12.2-legacyfabric

### What works
- **Recording** (F8 toggles; the recording starts with the next world join and spans every rejoin as a
  new segment). Everything is captured read-only and handed to the non-blocking writer:
  - **World:** on 1.21.11 the singleplayer pipeline already carries encoded packets, so a netty tee
    copies the raw frames, and a hook on the main-thread packet queue records when each one was handled
    (`RAW_FRAME` + `DELIVER`). On 1.12.2 the channel carries packet objects, so each packet is encoded
    with its own codec at the moment the main thread handles it (`PACKET`).
  - **Per tick:** the local player's render state (positions and their "old" values, rotations, body
    and head yaw, view bob, hand sway, swing, use-item, hurt and death, walk animation, cape motion,
    portal and nausea warp, attack cooldown, selected slot, hand-renderer equip state and items, own
    block-break progress), inventory changes as the client saw them, and block changes the client
    predicted for its own actions.
  - **Per frame:** final camera position and rotation, world and hand FOV, perspective, raw and
    GUI-scaled mouse position, buttons, held keys, flags (cursor grabbed, screen open, F1, paused, F3),
    window, framebuffer and GUI scale, wall-clock time (metadata only).
- **Playback** (title screen → "TAS Replays", or `-Dtasplayback.autoplay`). A replay connection with no
  server behind it is fed the recorded packets at their recorded points: on 1.21.11 a vanilla in-memory
  pipeline on a netty `EmbeddedChannel`, so login, configuration and play run through the real
  listeners; on 1.12.2 a `NetHandlerPlayClient` (Forge also gets a `NetworkDispatcher`). The replay
  runs client ticks itself, one at a time, sets the partial tick from the `PlaybackClock`, and restores
  the recorded player state after each tick. The vanilla renderer then produces the frame, with camera
  and FOV overridden from the recording on 1.21.11. Frozen spans are skipped. The cursor is drawn from
  the recorded position, since the OS cursor is never in the framebuffer. Toasts on 1.21.11 animate on
  the recorded wall clock. Controls: Space pause, Left/Right frame step, Up/Down speed.

### How I verified it
`scripts/record-replay-verify.sh` runs a scripted session (fixed-seed superflat world, teleport,
`/give`, walk, turn, jump, hotbar changes, break and place blocks, third-person front and back) with
thumbnails captured every 10 frames, then replays it frame by frame in a fresh client and compares.
`scripts/determinism.sh` runs the same script with recording off, off again (vanilla baseline) and on.

| Node | Frames compared | Mean abs diff (0-255) avg / max | Full-size frames pixel-identical | Determinism off = off = on | RNG tripwire |
|---|---|---|---|---|---|
| 1.21.11 | 30 | 0.51 / 8.8 (max = frame 0, loading screen) | not measured this run; HUD region 0 px in all | ✅ bit-identical | ✅ 0 violations |
| 1.12.2-forge | 63 | 0.04 / 0.54 | 13 of 16 | ✅ bit-identical | n/a (see risks) |
| 1.12.2-legacyfabric | 63 | 0.03 / 0.87 | 10 of 16 (others: horizon line, ≤ 1627 px) | ✅ bit-identical | n/a |

Evidence: `docs/m2/1.21.11-record-vs-replay.jpg`, `docs/m2/1.12.2-record-vs-replay.jpg`.

Bugs found and fixed through this verification: the replayed player was frustum-culled (position set
without its bounding box); the wrong default skin on 1.12.2 (the replay used the viewer's random
offline profile; the recorded profile is now in `meta.json`); a missing attack-cooldown indicator;
missing own break cracks and predicted block changes; the viewer's tutorial hints and pause-on-focus-loss
leaking into replays; and a race in the test script itself (it started before the `/tp` had arrived),
which had made vanilla's own baseline non-repeatable on Forge.

### Remaining differences and open risks
1. **Particles from client randomness** (block-hit and break particles, some ambient ones) don't
   reproduce, because the replay client's random state differs. Proposed fix (later milestone): record
   particle spawns, or snapshot and restore the client RNG state per tick.
2. **Loading screen:** the first frames differ. The recording showed the server's chunk-progress view,
   which a server-less replay can't have. Distant chunks may also mesh at a slightly different pace,
   which shows as a thin horizon line.
3. **Recording starts at world join.** A recording can't start mid-session, because playback needs the
   connection from login onwards. Synthesizing the initial state is possible later; TAS workflows rejoin
   or reload anyway.
4. **Rewind detection is basic:** each reconnect is a new segment treated as a continuation. Real
   savestate and rewind detection with TASmod, LoTAS and `/tick` is M7.
5. **No RNG tripwire on 1.12.2:** it uses `java.util.Random`, which a mixin can't instrument. Requirement
   1 rests there on the on/off determinism test and on code review (capture code only reads fields and
   encodes packets).
6. **1.12.2 is not covered on two points:** mouse position is integer pixels (LWJGL2 has no sub-pixel
   cursor), and toast timing isn't driven from the recorded clock yet. Skin and cape textures download
   asynchronously, so whether a cape shows can differ.
7. **Sound** (M4), **GUI screens** (M3) and the **other Fabric nodes** (M6) aren't wired yet. On those
   nodes the jar still loads and logs that it has no implementation.
8. **The two "MCP" mapping sets differ:** LegacyFabric's `build.mcp` mappings differ from Forge's
   `stable_39` in a few member names (found: `Minecraft.networkManager`/`myNetworkManager`,
   `gameDir`/`mcDataDir`). The shared 1.12.2 code avoids both. Any new 1.12.2 hook has to be checked
   against both mapping sets.

## Milestone 1 report: `core`

### What works
Everything below is pure Java 8 with no dependencies outside the JDK and no Minecraft imports
(`core/src/main/java/dev/dbenton/tasplayback/core`). It is shaded into all 7 jars.

| Package | What it does |
|---|---|
| `io`, `json` | Bounds-checked binary I/O with varints. A small strict JSON reader and writer. |
| `time` | `GameTime` and `TimeKey (segment, tick, partial, frame)` with recording order and game order. |
| `format` | The `.tasp` container: stream ids, chunk layout with CRCs, `StreamWriter`, a recovering `StreamReader`, `AsyncRecorder` (writer thread, bounded queue, game threads never block), `RecordingMeta` (including resource-pack mismatch warnings), markers, and `TaspArchive` (open a `.tasp` or a `.part` directory, pack, recover). |
| `frame` | `FrameRecord`, the full per-frame camera, hand, mouse and input state from PLAN §4.3, plus a lossless delta codec (floats stored as XOR of their raw bits, so values round-trip bit-exactly). Under 100 bytes per frame after compression on the synthetic test data (enforced by a test; real recordings will be measured in M2). |
| `event`, `sound` | The generic event stream codec. `SoundEvent` PLAY/UPDATE/STOP/STOP_ALL carrying the resolved variant, attenuation, seed and delay. |
| `branch` | `SegmentGraph` (`segments.json`) and `CanonicalResolver`: drops rewound branches, walks past ancestors that began after the link point, and supports user overrides (choose the final segment, re-link, mark a false positive). |
| `timeline` | `PlaybackClock`: game time maps to output seconds at any TPS and speed. Frozen spans are skipped by default; hold-for-wall-time and hold-fixed are options. Sounds between frames land at their exact tick. `Interp`: shortest-arc angles, step semantics for discrete state. |
| `audio` | `OfflineMixer`: 48 kHz stereo, rendered block-sequentially so long exports stream to disk. Sample-exact starts, OpenAL-style linear distance model, constant-power panning from the listener's right vector, pitch resampling, loops, stops and per-tick updates. Plus a streaming `WavWriter` (16-bit or 32-bit float). |
| `export` | `FfmpegCommand`: an argument-vector builder (no shell) for raw-RGBA stdin, the WAV input, codec/CRF/preset, vflip, rational FPS and image sequences. |

The file format is written up in `docs/tasp-format.md`.

### How I verified it
`./gradlew :core:test :core:testJava8`: **54 tests, all passing on both Java 21 and Java 8** (Azul 8u, the
runtime of the 1.12.2–1.16.5 nodes). `buildAll` now depends on both test runs. Highlights:
- **Audio sync (the PLAN's required test):** for 24, 30, 60 and 144 fps, a click on frame N of a 2-TPS
  recording goes through `PlaybackClock` to `OfflineMixer` and its first non-zero output sample is exactly
  at `round(N / fps × 48000)` (within 1 sample, from float rounding of the partial tick). Starts are
  sample-exact for any block alignment and for 48k, 44.1k and 22.05k sources, and rendering in pieces is
  bit-identical to rendering in one go.
- **Crash recovery:** 300 random truncation points and 200 random byte flips. The reader always keeps
  exactly the chunks before the damage and decodes them bit-exactly. A simulated crash (status still
  "recording", frames file cut mid-chunk) recovers, and packing it produces a clean archive.
- **Round-trips:** 5000 frames across 16 chunks are bit-exact, as are events, sound payloads, `meta.json`,
  `segments.json` and `markers.json`.
- **Branch resolution:** single segment; simple rewind; rewind to before the current segment's start;
  nested rewind; rewind to a state saved in an already discarded branch; promote a branch; re-link;
  false-positive boundary; partial-tick link points.
- **Never blocking the game:** flooding a recorder with an 8-slot queue (20,000 frames plus 20,000 4 KiB
  GUI-capture records). Every record is either written or counted as dropped, the GUI-capture stream is
  dropped first, and no single offer took longer than 50 ms. (That ceiling only catches gross blocking;
  the actual non-blocking guarantee comes from `ArrayBlockingQueue.offer`.)
- **Fuzzing:** 20,000 random inputs each into the frame, event, sound and whole-stream decoders. Only
  `FormatException` is allowed. This found and fixed one bug: a corrupt event type surfaced as
  `IllegalArgumentException`.

### Open risks and deferred items
- **The mixer's model parameters are placeholders until M4:** linear distance with reference 0, rolloff 1,
  max = attenuation distance, and constant-power panning. I'll confirm them against each version's sound
  engine and OpenAL setup in the decompiled sources. Timing is exact; the mix is approximate (as stated
  in the plan).
- **Not built yet:** the pure-Java Vorbis decoder (needed to feed the mixer real OGGs) comes in M4. I'll
  check its license before vendoring it. The settings model will be built alongside the UI that uses it
  (M5/M8).
- `TaspArchive.open` reads whole streams into memory. That's fine for hours of frames (tens of MB), but
  `world.bin` for long runs may need streaming chunk access during M2's world reconstruction.

## Milestone 0 report

### What works
- **One command, one Gradle build:** `./gradlew buildAll` tests `core`, builds all 7 nodes and copies the
  jars into `dist/`. Loom 1.19.4, RetroFuturaGradle 2.0.6 and legacy-looming 1.16.1 (which brings Loom 1.16.3)
  all run in the same build on Gradle 9.9.1. None of the fallbacks from the plan were needed.
- Stonecutter 0.10 layout: a root branch (`src/`) for the modern Fabric nodes and a `legacy` branch
  (`legacy/src/`) for both 1.12.2 nodes. The 1.12.2 code is shared verbatim between Forge and LegacyFabric,
  because both use MCP names. Loader glue lives in `legacy/src/forge` and `legacy/src/legacyfabric`.
- `core` and `platform-api` (Java 8 bytecode, no Minecraft imports) are shaded into every jar.
- Each jar carries a read-only smoke mixin on `Minecraft.<init>`, and every one of them applied at runtime.

### How I verified it
- `./gradlew buildAll` → `BUILD SUCCESSFUL`, 7 jars.
- I ran `javap` on each jar's mixin class to confirm its target namespace (the table above).
- `scripts/smoke-boot.sh <node> <outdir>` booted each client headless and checked for both log lines and
  no crash report, then took a screenshot.

### Problems found and how they were handled
1. **Stonecutter 0.10 requires a Java 25 Gradle daemon.** Pinned through `gradle/gradle-daemon-jvm.properties`
   (auto-provisioned via foojay). The launcher JVM can be anything ≥17.
2. **loom-back-compat puts Loom on the classpath of every project that has `loomx.loom_version`.** If that
   property were in the root `gradle.properties`, Loom 1.19 would leak onto the root and `legacy` classloaders
   and clash with RFG and legacy-looming. So it's set per modern node in `versions/<node>/gradle.properties`.
3. **TASmod's intermediary.** Released TASmod (Beta2.0/2.1) is built on the LegacyFabric intermediary
   (`class_1600` = `Minecraft`). TASmod's `develop` branch has moved to OrnitheMC `ploceus` with **gen2
   intermediary**, which differs from LegacyFabric for 3320 of 3344 classes. Our LegacyFabric jar matches the
   **released** TASmod. If a future TASmod release moves to Ornithe, adding a `1.12.2-ornithe` node is a
   buildscript-only change, because the source uses MCP names either way.
4. **26.3 headless:** the new `renderpearl` OpenGL backend asks SDL3 for an sRGB-capable framebuffer
   (`SDL_GL_FRAMEBUFFER_SRGB_CAPABLE=1`, GL 3.3 core forward-compatible). That's from reading the decompiled
   `GlBackend`. Xvfb's GLX visuals have no sRGB visual, and Vulkan has no surface support here.
   `SDL_VIDEO_FORCE_EGL=1` makes SDL use Mesa EGL, which works. This only affects headless/CI; real desktops
   have sRGB visuals.
5. **1.12.2 headless:** LWJGL2 shells out to the `xrandr` binary to list display modes, so CI and this
   container need `x11-xserver-utils` installed.
6. **This sandbox only:** `repo.maven.apache.org` returns HTTP 429 through the egress proxy. Local builds here
   use an init script passed with `--init-script` that rewrites Central to Google's official Central mirror.
   Nothing about this is committed, and CI uses Central directly.
7. **Not part of M0's scope, but flagged for M4 (sound):** there's no audio device in this container, so
   OpenAL fails to open (`Failed to open OpenAL device`) and the game runs without sound. Live-playback sound
   tests will need a virtual audio device (PulseAudio null sink or ALSA loopback). The offline mixer doesn't
   need one.

### Open risks carried forward
- Range claims in `fabric.mod.json` (for example 1.16.5 → `>=1.16.2 <=1.16.5`) are still hypotheses until
  M6 checks them against decompiled sources.
- 1.12.2-forge uses MixinBooter 12.5 (CleanMix 0.9 / Mixin 0.8.7). LoTAS for 1.12.2 is Forge, and whether it
  also uses MixinBooter is checked in M7.
- `/tasp` command stub from the plan: not added. A command API isn't available uniformly on every node
  without Fabric API, and it isn't needed until the M2 UI.

## Log
- 2026-11-27: Re-checked toolchain versions (Stonecutter 0.10, Loom 1.19.4, loom-back-compat 0.4.3,
  RFG 2.0.6, MixinBooter 12.5, Gradle 9.9.2). Wrote PLAN.md. Plan approved with decisions in PLAN.md §12.
- 2026-11-27: Milestone 0. One build, 7 nodes, all boot headless. Details above.
- 2026-11-27: Milestone 1. `core` complete, 54 tests green on Java 21 and Java 8. Details above.
- 2026-11-27: Milestone 2. Record + replay on 1.21.11, 1.12.2-forge and 1.12.2-legacyfabric; verified frame by frame and with on/off determinism runs. Details above.
