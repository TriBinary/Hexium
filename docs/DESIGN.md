# Hexium — Design

What Hexium is and how it plays. The detailed rules live in their own docs: [JUDGEMENT.md](JUDGEMENT.md) (timing and scoring), [CHART_FORMAT.md](CHART_FORMAT.md) (chart files) and [ARCHITECTURE.md](ARCHITECTURE.md) (how the code is built). Development workflow: [DEVELOPMENT.md](DEVELOPMENT.md).

## Overview

Hexium is a client-side Fabric mod for Minecraft 26.3 that combines a 4-lane keyboard track with a mouse-aimed sky screen, judged on CHUNITHM's timing windows. Online sharing and leaderboards come later. The two things that decide success are an audio-driven clock (never Minecraft's 20 TPS tick) and a camera-aim model that stays readable.

Confirmed decisions:

| Topic | Decision |
| --- | --- |
| Stage layout | Player stands on a fixed spot facing a transparent judgement screen; notes come from the distance toward the screen |
| Aiming | Real camera rotation; the crosshair is the aim point (option 2c) |
| Keys | Fixed 4K for the left hand, remappable (default A S D F) |
| Note look | Block-style 3D models, rendered client-side (not entities) |
| Stages | Built-in arena first; chart-authored stage worlds with beat-synced events in a later phase |
| Editor | In-game, with instant test play |
| Music policy | Original or permitted songs only; uploader confirms rights; takedown process |
| Backend | Separate VPS in mainland China; domain and site already ICP-filed |
| Platform | Fabric, Minecraft 26.3, solo play now; multiplayer possibly later |
| Repo | TriBinary/Hexium; development on a fork, merged by PR (as with TownyMenu) |
| Judgement | CHUNITHM standard; ground hold = head judgement + ticks, no tail; sky slide = hold click + follow path, ticks only, no head or tail |
| Slide button | Each slide names its required button (L or R), like sky taps |
| Tick rate | Hold and slide ticks are fixed at 1/4 beat in `JudgementConfig`, not set per chart |
| Package extension | Chart packages are `.hexium` files |

One caution on aiming: with real camera rotation, the ground track moves on screen whenever the player turns. Milestone 3 has a readability playtest gate, with a fallback if it fails.

## Gameplay spec

Everything is judged on one plane: the transparent screen in front of the player. Its bottom edge is the ground judgement line, and its surface is the sky judgement area.

### Stage geometry (starting values, all tunable)

- The player is locked on the stand spot (the red circle). Movement, jumping, sneaking, hotbar and inventory keys are suppressed during play.
- Screen plane: 8 blocks in front of the player's eyes, about 12 blocks wide and 6 tall, with its bottom edge on the floor.
- Ground lanes: 4 lanes lie on the floor and run away from the player past the screen into the distance. Notes spawn far down the lanes and slide toward the bottom edge of the screen.
- Sky notes: they fly straight toward the screen along the depth axis, visible through it, and are judged when they reach their (x, y) spot on the screen.
- Note speed ("HiSpeed") is a player setting. It changes how far away notes appear, never their timing.
- Rest view: centred on the screen with the whole ground track visible. Yaw and pitch are clamped to the screen's edges plus a small margin, so the player can't spin away.

### Note types

| Layer | Note | Input | Judged on |
| --- | --- | --- | --- |
| Ground | Tap | Press the lane key | Press time |
| Ground | Hold | Press the lane key, keep holding | Head press + ticks along the body; no tail |
| Sky | Tap (L) | Aim at it, left click | Click time + crosshair inside the hit area |
| Sky | Tap (R) | Aim at it, right click | Click time + crosshair inside the hit area |
| Sky | Slide | Hold the click button and keep the crosshair on the moving path | Ticks along the path; no head or tail (add a sky tap at the head if needed) |
| Sky | Flick | Swipe the crosshair through it, no click | Crossing time + swipe speed (+ optional direction) |

### Input rules

- Default keys: A S D F for lanes 1 to 4. Rebinding happens on the mod's own settings page, separate from vanilla controls.
- A sky tap pressed with the wrong button is ignored by that note. It neither hits nor breaks it.
- One press resolves at most one note: the earliest unjudged note in range, by timing.
- Mouse sensitivity during play is the mod's own setting. It applies on top of Minecraft's raw mouse input, so menu feel doesn't change.
- Pause (Esc) pauses the audio and the clock together. Resuming has a 3-second countdown with the notes rewound a little, as most rhythm games do.

### Aiming with real camera rotation

The aim point is where the camera's look ray meets the screen plane. Because the camera turns, keep these mitigations:

- A note's hit area is measured in screen-plane units, so it doesn't depend on FOV.
- The ground track is drawn in world space, so it stays put in the world while the view moves. Playtest whether it stays readable when you look at the top corners.
- Optional comfort settings: a lower default FOV for play, and a "view assist" that slowly pulls the camera back to the rest view when no sky notes are near.
- Fallback if playtests fail: a hybrid where the camera follows a free cursor by only a few degrees. This only touches the input layer, not charts or judgement.

## Editor

The editor runs inside the same stage, with the real renderer. Charters see exactly what players will see, and pressing one key switches between editing and test play from the current position.

### Layout

- **Bottom panel (2D):** a horizontal timeline with a waveform, beat grid, and lanes for ground notes and sky notes. Scroll wheel moves through time, Ctrl+scroll zooms.
- **3D view (top):** the live stage. Sky notes are placed and moved by aiming the crosshair at the screen and clicking, the same way they're played. Slide points are added by aiming, one point per grid step.
- **Side panel:** properties of the selected note (time, lane, x, y, button, size, direction, ease).

### Tools

- Snap divisions: 1/4, 1/8, 1/12, 1/16, 1/24, 1/32 beat, plus free placement while holding Alt.
- Timing tools: BPM tap, BPM change markers, offset nudge with a metronome click, playback at 25 / 50 / 75 / 100% speed.
- Edit tools: select by box, copy and paste, mirror left-right (lanes and x), shift in time, quantise.
- Undo and redo on every change, built on a command pattern from day one.
- Live validator: errors and density warnings appear on the timeline as they happen.
- Test play from the cursor, with autoplay as an option so you can watch.

### Files

- **New chart:** pick an OGG, enter metadata, set BPM and offset.
- **Save:** writes the package to `hexium/charts/<id>/` with an autosave every 60 seconds.
- **Export:** writes a `.hexium` file. Upload is a separate step after export (Milestone 7).

### Build order

Build the editor in Milestone 6, after gameplay is stable. Until then, write test charts by hand in JSON or generate them with the `tools` CLI. The editor is the largest UI piece, so split it into small agent tasks: timeline view, note placement, properties panel, undo and redo, then test play.

## Online server

The server is a Kotlin service on a VPS in mainland China. It reuses `core`, so every leaderboard score is re-judged from its replay before it counts. Logins use the player's Minecraft account, so there are no passwords to store.

The subagent and the Gradle module are both called `server`, not "backend", because in the commit tags **Backend** means build and deployment machinery, not server code.

### Stack

- Kotlin + Ktor (the standard Kotlin web framework) on Java 25. It depends on core directly, so re-judging uses the exact game code.
- PostgreSQL for users, charts, scores and reports. Chart packages and replays are stored as files on disk at first, with object storage (Tencent COS or Aliyun OSS) later if storage grows.
- Nginx in front with HTTPS on the ICP-filed domain. Deploy with Docker Compose (app + Postgres + Nginx).

### Login with the Minecraft account

1. The mod asks the backend for a one-time challenge string.
2. The mod calls Mojang's session "join" endpoint with the player's session token and that challenge. This is the same thing that happens when joining an online-mode server.
3. The backend calls Mojang's "hasJoined" endpoint with the username and challenge. If it confirms, the backend issues its own token (a JWT, valid for 7 days).

The player's Mojang token never leaves the client. Two things to check early: whether the VPS can reliably reach Mojang's session server, and what to do about players on offline-mode launchers, which are common in China (see Risks).

### API (v1)

| Method | Path | Purpose |
| --- | --- | --- |
| POST | `/v1/auth/challenge` | Get a login challenge |
| POST | `/v1/auth/verify` | Confirm login with Mojang, get a token |
| GET | `/v1/charts?q=&sort=&page=` | Browse and search approved charts |
| GET | `/v1/charts/{id}` | Chart details and versions |
| GET | `/v1/charts/{id}/download` | Download a package |
| POST | `/v1/charts` | Upload a package (rights confirmation required) |
| POST | `/v1/charts/{id}/report` | Report a chart (copyright, content) |
| GET | `/v1/charts/{id}/{difficulty}/leaderboard` | Top scores + your own rank |
| POST | `/v1/scores` | Submit a replay; the server computes the score |
| GET | `/v1/replays/{id}` | Download a replay to watch |
| — | `/admin/*` | Review queue, takedown, ban (web page, admin only) |

### Rules that keep the leaderboard honest

- An uploaded chart version is never changed. Every version has a content hash, and leaderboards are per version hash, so editing a chart starts a new board.
- The client sends the replay, not a score. The server runs the `core` judge on it and stores the score it gets. If it doesn't match the client's result, the submission is flagged.
- Basic replay sanity checks: aim samples at a plausible frame rate, no inhuman mouse jumps, inputs inside the song's length. Rate-limit submissions per account.
- This stops edited score files and most simple cheats. It doesn't stop a good bot. Accept that for v1 and keep replays watchable so the community can flag cheaters.

### Content and compliance

- On upload, the uploader must tick a box saying the song is original or that they have permission to use it, and optionally give a source link. Store this with the chart.
- New uploads wait in a review queue before they're public. Once you trust some charters, you can let their uploads go straight through.
- Report button in game and on the site. A takedown hides the chart and its leaderboard immediately, and you keep a log of every takedown.
- The domain already has ICP filing. Before launch, check with the VPS provider whether a site with user uploads and accounts also needs the public-security filing (公安备案) or real-name verification for uploaders. This is an open to-do, not settled.
- Keep the mod free, with no paid features, to stay within Minecraft's usage guidelines.

## Milestone roadmap

Ten milestones, each ending in a gate you check yourself. The first playable build is Milestone 2, and the riskiest design question (camera rotation) is settled at Milestone 3, before the editor or online work starts.

Gameplay runs along the top row and content and release along the bottom row. Online work starts after the core engine and joins at release.

1. **M0 Foundations.** Repo, CI, `CLAUDE.md`, subagents, `docs/JUDGEMENT.md`, `docs/CHART_FORMAT.md`, three hand-written example charts.
   - Gate: you've read and approved both specs.
2. **M1 Core engine** (`core-engineer`). Chart model, parser and validator; timing map; judge for all six note types; scoring; replay format; `tools` CLI (lint, re-judge).
   - Gate: every rule in the judgement spec has a test; golden replays pass.
3. **M2 Ground prototype** (`fabric-client`). Title button and menu; session gating; stage world loading; player lock; audio clock; ground tap and hold rendering; key input; combo and judgement HUD; results screen; offset calibration; debug overlay.
   - Gate: playtest. Timing feels tight, nothing drifts over a 3-minute song, and vanilla worlds are untouched.
4. **M3 Sky layer** (`fabric-client`). Screen pane; aim ray; camera clamp; sky taps L/R, slide and flick; comfort settings.
   - Gate: readability playtest. Keep real camera rotation or switch to the hybrid fallback.
5. **M4 Content loop** (`ui-editor` + `fabric-client`). `.hexium` packages; song select with preview; difficulty select; settings and keybinds; local best scores; save and watch replays; autoplay.
   - Gate: three full charts playable start to finish from the menu.
6. **M5 Feel and polish.** Hit effects and sounds; note skins; zh_cn and en_us text; reach the 240 FPS budget.
   - Gate: a friend plays without your help and enjoys it.
7. **M6 Editor** (`ui-editor`). Timeline, placement, properties, undo/redo, snapping, BPM tools, validator, test play, export.
   - Gate: you chart a full new song in the editor alone.
8. **M7 Online** (`server`, parts can start right after M1). Minecraft-account login; upload with rights confirmation; review queue; browse and download in game; leaderboards with replay re-judging; reports and takedown; deployment on the China VPS.
   - Gate: closed beta with a few players; filings checked.
9. **M8 Stage worlds and events.** Chart packages can carry a stage world; beat-synced events (block changes, lights, camera moves) in `events.json`; editor support.
   - Gate: one showcase chart with its own stage.
10. **M9 Release 1.0.** Modrinth page, docs for charters, crash reporting, backups on the server.
    - Gate: public launch.

## Risks and open questions

The biggest risk is the gameplay feel of real camera rotation. It's tested in Milestone 3 before anything is built on top of it.

| Risk | Effect | Mitigation |
| --- | --- | --- |
| Camera rotation makes the ground track hard to read | Core gameplay feels bad | Readability gate in Milestone 3; fallback is the hybrid cursor (input layer only) |
| Input arrives once per frame | Up to one frame of timing error | Recommend 240+ FPS during play; keep rendering light; judge from event timestamps |
| Audio clock drift or stutter | Notes and music drift apart | Smoothed clock with drift overlay; 3-minute no-drift test in every milestone |
| Minecraft or Fabric updates break rendering or input hooks | Mod breaks on new versions | Keep MC-specific code in thin adapter classes; logic stays in `core` |
| One-handed keyboard play gets too dense | Charts feel unfair | Density warning in validator and editor; tune with real charts |
| Copyrighted music uploaded | Legal trouble for the server | Rights confirmation, review queue, reports, fast takedown |
| Mojang session server slow or unreachable from China | Players can't log in | Test from the VPS early; retry with backoff; scores queue offline and upload later |
| Offline-mode launchers common in China | Many players can't log in with Mojang | Decide policy (open question 1) |
| China rules for user uploads | Site may need extra filings | Check with the VPS provider before Milestone 7 launch |

### Open questions

1. **Offline-launcher players:** let them play everything except leaderboards and uploads? Or add your own website accounts as a second login method?
2. **Life gauge:** none in v1 (score and lamps only). Do you want a CHUNITHM-style clear gauge later?
3. **Speed changes:** mid-song note speed changes ("soflan") are left out of format v1. Do you want them in v2?
