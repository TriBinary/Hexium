# Hexium — Agent Instructions

Hexium is a Fabric rhythm game mod for Minecraft 26.3 (Kotlin, Java 25): a 4-lane keyboard
track plus a mouse-aimed sky screen, with an in-game editor and online charts.
It is client-only. Milestone 3 (sky layer) has passed its gate, so the project is at
Milestone 4 (content loop); see the roadmap in docs/DESIGN.md.

## Working with me
- Before any non-trivial task, ask clarifying questions about the idea and design
  instead of assuming. Propose a plan and wait for approval.
- If a task contradicts the docs, stop and ask. Don't silently change the spec.
- Keep changes scoped to one task. Summarize what changed and why at the end.

## Read first
- docs/DESIGN.md (what Hexium is), docs/JUDGEMENT.md, docs/CHART_FORMAT.md,
  docs/REPLAY_FORMAT.md (the specs),
  docs/ARCHITECTURE.md (how the code is built), docs/DEVELOPMENT.md (commands, workflow).
- Files in docs/ have upper-case names (docs/COMMIT_STRUCTURE.md), as in TownyMenu.
- README.md faces players (what Hexium is, install, how to play, charts). Never put
  build steps, architecture or dev workflow there: those go in their own docs/ file
  (docs/DEVELOPMENT.md, docs/ARCHITECTURE.md, the specs).

## Layout
- `core/`   plain Kotlin game rules, package net.trilleo.hexium.core. No Minecraft imports.
- `mod/`    the Fabric mod. Client code in src/client (net.trilleo.hexium.client), Java
            mixins in src/client/java/net/trilleo/hexium/client/mixin. src/main holds
            only shared constants (net.trilleo.hexium).
- `server/` online server (Milestone 7), net.trilleo.hexium.server.
- `tools/`  CLI: chart lint, replay re-judge and dump, net.trilleo.hexium.tools.
- Versions live in gradle.properties; `mod_version` is the mod's version.

## Hard rules
- Gameplay time comes ONLY from the audio clock (mod/.../clock). Never use
  game ticks, tick deltas or System.currentTimeMillis for timing or note positions.
- All judgement, scoring, chart parsing and replay logic lives in `core`
  (plain Kotlin, no Minecraft imports). `mod`, `server` and `tools` call it, never copy it.
- Every mixin and event handler must early-return unless HexiumSession.active().
- Never guess Minecraft or Fabric API names. Check the 26.3 sources
  (./gradlew genSources) or Fabric docs, and note what you checked in your summary.
- Judgement windows and score weights come from core's JudgementConfig only.
- Code is Kotlin; build scripts are Gradle Kotlin DSL. Mixins are the exception:
  write them in Java, keep each one a few lines that call into Kotlin.
- Per-frame code (renderer, input, judge, clock) must not allocate: no lambdas,
  boxed collections (List<Int>, Pair), sequences or string building in the hot
  path. Use FloatArray/IntArray, plain loops and reused objects.

## After every change
- Add a CHANGELOG.md entry under `## Unreleased` for every Feature, Improvement
  and Fix, as part of the change (format in docs/RELEASING.md).
- After EVERY change, check which documents it affects and update them in the
  same change: this file, README.md (player-facing facts), CHANGELOG.md,
  docs/DESIGN.md, docs/JUDGEMENT.md, docs/CHART_FORMAT.md, docs/REPLAY_FORMAT.md,
  docs/DEVELOPMENT.md,
  docs/ARCHITECTURE.md, docs/API.md (server routes, once it exists),
  docs/RELEASING.md, docs/COMMIT_STRUCTURE.md, .claude/agents/*.md and
  .github/workflows/*.yml.
- In your summary, list each document you updated, or say "Docs checked: none
  affected". A change is not done until this check is done.

## Commits and releases
- Format `<Tag>: <What changes>`, tags Feature, Fix, Improvement, Internal,
  Backend, Update. Full rules: docs/COMMIT_STRUCTURE.md.
- Do not commit, push or open PRs. Instead, end each change (or each phase of a
  longer task) with its commit message in a code block, written by
  docs/COMMIT_STRUCTURE.md. I review and commit myself.
- `mod_version` in gradle.properties is the only source of the version. Never
  change it or create tags unless asked: that publishes a release.

## Workflow
- One task at a time, ideally under ~500 changed lines.
- `core` changes: write or update tests first, then code. `./gradlew :core:test` must pass.
- Before saying a task is done: run `./gradlew build`, call the `reviewer` agent,
  list what you verified, and list what needs a human playtest.

## Agents (.claude/agents)
- core-engineer: core/ and tools/. fabric-client: mod/ gameplay. ui-editor: mod/ screens
  and the editor. server: server/. reviewer: read-only review before hand-over.

## Commands
- Build + all tests: ./gradlew build
- Core tests only: ./gradlew :core:test
- Run client: ./gradlew :mod:runClient
- Client game tests (opens a game window; tests are in mod/src/gametest): ./gradlew :mod:runClientGameTest
- Lint charts (from Milestone 1): ./gradlew :tools:run --args="lint docs/examples"
