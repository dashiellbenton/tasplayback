# TASPlayback — Progress

| Milestone | Status |
|---|---|
| Plan | Approved 2026-11-27 (decisions in PLAN.md §12) |
| **0 Build skeleton** | **Done, awaiting your review** |
| 1 core | not started |
| 2 Vertical slice (1.21.11, 1.12.2-forge, 1.12.2-legacyfabric) | not started |
| 3 GUI Tier 1 | not started |
| 4 Sound | not started |
| 5 Exporter | not started |
| 6 Port all Fabric nodes | not started |
| 7 Savestate/tickrate integrations | not started |
| 8 Tier 2, polish, CI, docs | not started |

## Compatibility matrix

"Boots" = the dev client started headless (Xvfb + Mesa llvmpipe), logged TASPlayback's init line, logged the
smoke mixin line (proving mixin + remapping work), and reached the title screen with no crash report.

| Node | Loader / toolchain | Java | Jar builds | Mixin target namespace in jar | Boots | Notes |
|---|---|---|---|---|---|---|
| 1.12.2-forge | Forge 14.23.5.2847, RFG 2.0.6, MixinBooter 12.5 | 8 | ✅ | MCP class names + SRG refmap | ✅ | |
| 1.12.2-legacyfabric | LegacyFabric, legacy-looming 1.16.1 (Loom 1.16.3), `net.legacyfabric:yarn:1.12.2+build.mcp` | 8 | ✅ | `net/minecraft/class_1600` (LegacyFabric intermediary, same as released TASmod) | ✅ | |
| 1.14.4 | Fabric, Loom 1.19.4 (remap), Mojang mappings | 8 | ✅ | `class_310` (Fabric intermediary) | ✅ | |
| 1.16.5 | same | 8 | ✅ | intermediary | ✅ | |
| 1.20.1 | same | 17 | ✅ | intermediary | ✅ | |
| 1.21.11 | same | 21 | ✅ | `class_310` | ✅ | |
| 26.3 | Fabric, Loom 1.19.4 (no remap), unobfuscated | 25 | ✅ | `net/minecraft/client/Minecraft` | ✅ | headless needs `SDL_VIDEO_FORCE_EGL=1` (see below) |

Evidence: `docs/m0/smoke-contact-sheet.jpg` (title screens of all seven, in table order).

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
