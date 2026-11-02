# Technical architecture

The music's playback position is the only clock. Minecraft's 20 TPS tick (50 ms) is never used for anything that is timed or drawn in gameplay. All game rules live in a pure-Kotlin `core` module that the mod and the server share. That lets the server re-judge replays with exactly the same code.

## Toolchain

Minecraft 26.3 is unobfuscated, so no mappings are needed. The project uses Gradle 9.8.2, Fabric Loom 1.19, Fabric Loader 0.20.1, Fabric API 0.165.0+26.3, Kotlin 2.4.21 and Fabric Language Kotlin 1.14.2, on Java 25. `gradle.properties` and the Gradle wrapper are the source of truth for versions; update this line when they change. Hexium is written in Kotlin with Gradle Kotlin DSL build scripts. The mod depends on Fabric Language Kotlin, which players install alongside Fabric API. Mixins stay in Java: each one is a few lines that call into Kotlin.

## Gradle modules

| Module | Depends on | Holds |
| --- | --- | --- |
| `core` | nothing (plain Kotlin/JVM, Java 25) | Chart model, parser, validator, timing map (BPM changes → ms), judge engine, scoring, replay format |
| `mod` | `core`, Fabric API, Fabric Language Kotlin | Menu, session, stage loading, audio clock, input capture, rendering, HUD, screens, editor, network client |
| `server` | `core` | Backend API, auth, chart storage, leaderboards, replay verifier |
| `tools` | `core` | CLI: chart lint, chart convert, replay re-judge (used by CI and developers) |

Only `core` knows the rules. The mod, the server and the `tools` CLI all depend on it.

## Packages

| Module | Package |
| --- | --- |
| `core` | `net.trilleo.hexium.core` |
| `mod` (common, `src/main`) | `net.trilleo.hexium` |
| `mod` (client, `src/client`) | `net.trilleo.hexium.client`; mixins in `net.trilleo.hexium.client.mixin` (Java) |
| `server` | `net.trilleo.hexium.server` |
| `tools` | `net.trilleo.hexium.tools` |

Hexium is client-only (`"environment": "client"` in `fabric.mod.json`), so nearly all mod code lives in the client source set. `core` is bundled inside the mod jar (jar-in-jar).

## Session gating ("normal worlds are untouched")

- The title screen gets one extra button that opens the mod's menu. That menu is the only place a `HexiumSession` is ever created.
- Every mixin and event hook starts with `if (!HexiumSession.active()) return;`. Joining a normal world or server means no session, so vanilla behaviour stays as it was.
- The stage is a template world bundled in the mod. It's copied into the mod's own folder (for example `.minecraft/hexium/stages/`), not `saves/`, so it never shows up in the vanilla world list. It's loaded on the integrated server with frozen game rules: no daylight cycle, no mobs, no random ticks, no weather.
- The player entity is placed on the stand spot and locked. Gameplay never depends on the server; the world is just scenery.

## Audio and clock

- Songs are not played through Minecraft's sound manager. The mod decodes OGG with stb_vorbis (bundled with LWJGL) and streams it into its own OpenAL source on the game's existing OpenAL context.
- Song position comes from `AL_SAMPLE_OFFSET`, plus the OpenAL Soft latency extension if it's there. This raw value updates in steps, so the clock runs on `System.nanoTime()` and is gently nudged toward the audio position. It never jumps backwards.
- Draw time = song time − visual offset (A). Judgement time = input song time − judgement offset (B).

## Input timing

- Hook the keyboard and mouse callbacks (Minecraft's keyboard and mouse handlers). Stamp each event with `glfwGetTime()` when it arrives and convert it to song time with the clock's mapping.
- GLFW delivers events once per frame, so timestamp error is at most one frame. Tell players to run uncapped or at 240+ FPS during play (about 4 ms of error). Keep rendering fast so this holds.
- Every frame, record an aim sample (song time, yaw, pitch → crosshair point on the screen plane). Slides and flicks are judged from these samples. Flicks test the line between two samples, so fast swipes at low FPS still register.
- Events go into a queue that the judge drains every frame. Every input event and aim sample is also appended to the replay.

## Rendering

- Hook Fabric's world-render event for 26.3 (check the exact name in Fabric's 26.x docs, since rendering APIs changed). Draw only notes inside the visible time window.
- Notes are block models (note block styles per type) batched into one vertex buffer per frame. Hold bodies are stretched quads, slide paths are ribbons, and the screen is a translucent pane with a frame.
- Hit effects are client-only particles and a short sound, with a hit-sound volume setting.
- Performance budget: 240 FPS with 300 notes on screen on a mid-range GPU. Add a debug overlay showing clock drift, frame time and input-to-judgement delay from Milestone 2.

## Local data

`.minecraft/hexium/` holds `charts/` (one folder per chart package), `replays/`, `scores.json` (best scores per chart) and `settings.json`. That's enough for v1. A real database only lives on the server.
