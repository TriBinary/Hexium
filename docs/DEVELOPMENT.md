# Development

How to build, run and work on Hexium. For what Hexium is and how it plays, see [DESIGN.md](DESIGN.md); for how the code is organised, see [ARCHITECTURE.md](ARCHITECTURE.md).

## Requirements

- JDK 25
- IntelliJ IDEA (import the folder as a Gradle project)
- Git

The Gradle wrapper downloads the right Gradle version, and Loom downloads Minecraft on the first build, so the first sync takes a while.

## Project layout

| Path | What it is |
| --- | --- |
| `core/` | Plain Kotlin: chart model, timing, judge, scoring, replays. No Minecraft code. |
| `mod/` | The Fabric mod. `src/client` holds almost everything (Hexium is client-only); mixins are Java in `src/client/java`. |
| `server/` | The online server (from Milestone 7). |
| `tools/` | Command-line tools: chart lint, replay re-judge, replay dump. |
| `docs/` | Design, specs and developer docs (upper-case file names). |
| `docs/examples/` | Hand-written example charts in the writer's layout, used as test fixtures. |
| `mod/src/client/resources/hexium_packs/test-pattern/` | The chart pack bundled in the mod: a click track, `basic.json` (an exact copy of the example chart), a 3-minute `advanced.json` for drift tests, and a 90-second `expert.json` with sky notes in every corner, mixed with ground notes, for the Milestone 3 readability playtest. |
| `CHANGELOG.md` | Player-facing change log, see [RELEASING.md](RELEASING.md). |

## Commands

| Command | Does |
| --- | --- |
| `./gradlew build` | Builds every module and runs all tests |
| `./gradlew :mod:runClient` | Starts Minecraft with Hexium in a dev environment |
| `./gradlew :mod:runClientGameTest` | Runs the client game tests in `mod/src/gametest`. Opens a game window, and writes screenshots to `mod/build/run/clientGameTest/screenshots` |
| `./gradlew :core:test` | Runs only the judge and chart tests (fast) |
| `./gradlew :tools:run --args="lint docs/examples"` | Lints chart files or folders. A folder with a `meta.json` is also checked as a package, including that it has its `audio.ogg` |
| `./gradlew :tools:run --args="rejudge docs/examples/basic.json play.hexreplay"` | Runs a replay through the judge and prints the result |
| `./gradlew :tools:run --args="dump play.hexreplay"` | Prints a replay as text |
| `./gradlew :core:test --rerun -Dhexium.updateGolden=true` | Regenerates the golden replays, after a deliberate change to the judge |
| `./gradlew :server:run` | Runs the server locally (from Milestone 7) |

The test pack's `audio.ogg` is a 185-second click track at 120 BPM with an accent on every fourth beat, so beats land exactly on the chart's grid. It was made with:

```bash
ffmpeg -f lavfi -i "aevalsrc='sin(2*PI*if(eq(mod(floor(t*2),4),0),1760,880)*t)*exp(-60*mod(t,0.5))':s=44100:d=185" -ac 2 -c:a vorbis -strict -2 audio.ogg
```

The built mod is `mod/build/libs/hexium-<version>.jar`. The version comes only from `mod_version` in `gradle.properties`.

## Making changes

- Work on your fork and send changes to the organization repository by pull request. CI must pass.
- Commit messages follow [COMMIT_STRUCTURE.md](COMMIT_STRUCTURE.md).
- Every Feature, Improvement or Fix adds an entry under `## Unreleased` in `CHANGELOG.md`, in the same commit ([RELEASING.md](RELEASING.md) has the format).
- After every change, check which documents it affects and update them in the same change.
- `README.md` is for players only. Build steps, architecture and workflow belong in `docs/`.
- Never change `mod_version` or create tags unless you mean to release: see [RELEASING.md](RELEASING.md).

## Working with Claude Code

- `CLAUDE.md` holds the project rules every session reads. Agents for each area live in `.claude/agents/` (`core-engineer`, `fabric-client`, `ui-editor`, `server`, `reviewer`).
- `.claude/settings.json` lets agents run Gradle and read git freely, blocks `git commit`, `git push`, `git tag` and `gh pr create`, and asks before `rm -rf` or edits to `server/deploy/` and `.github/workflows/`.
- A Stop hook (`.claude/hooks/core-tests.sh`) runs `./gradlew :core:test` whenever `core/` has uncommitted changes, and sends failures back to the agent before it finishes.
- Personal overrides go in `.claude/settings.local.json`, which git ignores.

### The task loop

1. Start a fresh session (`/clear`) and give one task from the milestone list, pointing at the spec sections it depends on.
2. Use plan mode first. Read the plan; correct design misunderstandings before any code is written.
3. The agent changes files in the working tree only. It never commits, pushes or opens PRs. For parallel work, create a separate git worktree per task yourself, and never run two agents in the same module at once.
4. The agent runs the tests, calls `reviewer`, fixes what it finds, adds the changelog entry, and checks every document the change affects and updates it. It ends with a summary (verified / needs playtest / docs updated) and the commit message for the change in a code block, written by docs/COMMIT_STRUCTURE.md. For a long task, it does this after each phase.
5. You review the diff and playtest using the milestone's checklist. Then you commit with the proposed message (edit it if you like), push to your fork and open a PR into TriBinary/Hexium. Or file the feel problems as the next task.

Parallel lanes that work well: after Milestone 1, `server` can build auth and chart storage while `fabric-client` builds the playable prototype. Both depend only on `core`.

### Test strategy

- **`core` unit tests:** one file of synthetic input per rule in `docs/JUDGEMENT.md`. For example: "tap 20 ms late → Justice Critical", "hold released 120 ms before a tick → Attack on that tick", "slide with crosshair outside radius → Miss on those ticks", "flick too slow → no hit".
- **Golden replays:** replays with their known scores live in `core/src/test/resources/golden`. Any change to the judge must keep them identical, or update them on purpose in the same PR (and raise `JudgementConfig.JUDGE_VERSION`). So far they come from scripted players, with scores worked out by hand in `GoldenReplayTest`. Real plays recorded in the game live in `golden/recorded/`: they lock in what the judge gave, since nobody can work out a person's results by hand. To add one, copy a `.hexreplay` of the bundled test pack from `mod/run/hexium/replays` there, keeping its name, and regenerate. A judge change is accepted by regenerating, since these ignore the judge version. Editing a test pack chart means deleting its recordings and playing new ones. Editing an example chart changes its hash, so its golden replays must be regenerated too.
- **Property tests (jqwik):** shifting every input by the judgement offset B leaves the score unchanged when B is set to match. A steady straight swipe gets the same judgement at every frame rate (other results can differ by what one frame's sampling can, see [JUDGEMENT.md](JUDGEMENT.md)). Random input never breaks the judge, and its replay always gives the same results.
- **Fabric client game tests:** the title button opens the menu; the menu loads the stage; the stage holds the player still and blocks action keys while the camera still turns, and a big swing stops at the sky screen's edges plus the margin; leaving ends the session; joining a normal singleplayer world creates no session and the player can move; a bot plays a short chart to the end, pausing three times with each resume rewinding and judging nothing until the pause point, changing the note speed, with notes drawn on the way and no Misses, and a click is recorded where the turned camera pointed; its saved replay file re-judges to the same result; Retry starts over from the results and pause screens; the debug overlay is drawn; the track is never drawn in a normal world; Expert's sky notes are drawn and a bot hits its first two sky taps by turning the camera onto them and clicking; the play FOV reaches the camera, double play sensitivity turns the camera twice as far, and view assist brings it back to the middle on a ground chart; calibration sends uneven taps back, turns taps late by known amounts into the right offsets, saves them to `settings.json`, and the next play's replay records B. The bot pushes into `LaneInput` and `SkyInput` directly, so real key presses and clicks through SDL need a human. A button that disconnects must do it through the task queue (`StageWorld.leave` shows how): the test framework defers a disconnect made while handling a click, so the test would see a different order of events from the real game.
- **Human playtest checklist per milestone:** offset calibration feels right; no drift after a 3-minute song; ground track readable while aiming at the corners; hold, slide and flick feel fair.

### Milestone 2 gate

Passed on macOS on 2026-11-04, with the recorded plays in `golden/recorded`. Steps 1 and 2 haven't been run on Windows yet.

Real keys go through SDL, which the game tests can't reach, so this needs a person. F6 shows the numbers during a play. If you can, repeat 1 and 2 on Windows, where SDL's key timestamps may be coarser.

1. **Timing feels tight.** Calibrate, then play **Test Pattern → Basic** and **Advanced**. The ms under the judgement should sit around 0 when you're on the beat, and notes should meet the line on the clicks. Then set Max Framerate to 30 and tap: `key stamp age` should spread from about 0 up to a frame (33 ms) or more. Always near 0 means the OS stamps aren't real, and timing is really down to the frame. `late keys` should stay at 0 at 30 FPS and above, though a single long frame can add some.
2. **Nothing drifts.** Play Advanced to the end, about 3 minutes. `drift` should stay under about 20 ms, `resyncs` and `underruns` at 0, and the clicks should still match the notes at the end. The overlay goes with the results screen, so the log's last `Final drift` line has the numbers for the end.
3. **Frame rate holds.** Throughout, `frame` should stay steady with `worst` free of big spikes, without touching the mouse for over a minute.
4. **Pausing is fair.** Pause mid-song, during the lead-in and during a countdown, and in the middle of a hold, changing keys while paused. The countdown should end on the pause point, a hold kept down shouldn't lose ticks, and a hold let go should.
5. **Vanilla worlds are untouched.** Leave the stage, then in a normal world and on a server check movement, hotbar, inventory, F1, F3, F5, F3+F6, the pause menu and music. A, S, D and F must be normal there, and F6 must do nothing.
6. **Nothing is left behind.** After leaving, your camera view and HUD setting are as you had them, the stage isn't in your world list, and quitting from any screen doesn't hang.
7. **Record the goldens.** Keep at least one finished play each of Basic and Advanced, and add them to `core/src/test/resources/golden/recorded` as described under Test strategy.

### Checking the clock

The Hexium menu has an Audio Test button. It plays the bundled click track with a flash on every beat and the debug overlay on screen. Let the whole song play: the flash and the click should still line up at the end, and `resyncs` and `underruns` should stay at 0. After the first couple of seconds `drift` should stay small: it peaked at 8.5 ms over the whole song on a Mac outside the game, so anything over about 20 ms needs a look. `max` includes the start of playback, when the audio is still behind by the sound card's latency. Back 3 s tests seeking. Changing the audio device or pressing F3+T tests recovery: the song goes quiet under the loading screen, then comes back at the right place.

### Example task prompts

- *"Read docs/CHART_FORMAT.md. Implement the chart model, JSON parser and validator in `core`, test-first. Cover every validator rule with a passing and a failing example. No Minecraft code."*
- *"Read docs/JUDGEMENT.md. Implement the judge for ground tap and hold (head + ticks) in `core`. Input is a list of timestamped key events; output is a list of judgements. Write the tests from the rules section first."*
- *"Milestone 2, task 3: implement the audio clock in `mod`. Stream OGG with stb_vorbis into our own OpenAL source and expose songTimeMs() as described in the Audio and clock section. Add a debug overlay with drift vs AL_SAMPLE_OFFSET. Check every LWJGL/Minecraft call against the 26.3 sources."*
- *"Plan only: propose how the editor's undo/redo command system should look, with the list of commands for Milestone 6. Don't write code yet."*
