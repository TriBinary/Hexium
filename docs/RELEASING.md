# Releasing

Two GitHub workflows, based on TownyMenu's, do all builds and releases (listed under Workflows below). `build.yml` builds and tests every push and PR, lints the example charts and keeps the jar as a downloadable artifact. `release.yml` publishes a GitHub Release, and optionally a Modrinth version, whenever a commit that changes `mod_version` lands on `main` in TriBinary/Hexium. Forks never release, so pushing to your fork is always safe.

## Changelog format

`CHANGELOG.md` follows the same style as TownyMenu (SkyHanni-style):

```markdown
# Hexium - Change Log

## Unreleased

## Version 0.2.0

### New Features

#### Sky Notes

+ Added flick notes.
    + Swipe through them with the crosshair, no click needed.

### Fixes

#### Gameplay

+ Fixed hold ticks missing after resuming from pause.
```

| Level | Heading | Purpose |
| --- | --- | --- |
| `##` | `Unreleased` / `Version X.Y.Z` | One section per release; `Unreleased` collects entries during development |
| `###` | Category | `New Features`, `Improvements`, `Fixes`, `Technical Details`, `Removed Features` |
| `####` | Feature area | `Gameplay`, `Sky Notes`, `Charts`, `Editor`, `Online`, `Menus`, `Settings`, `Misc` |
| `+` | Entry | One change per bullet; indent `+` sub-bullets for details |

- Add the entry under `## Unreleased` in the same commit as the change.
- Only include categories and feature areas that have entries.
- Write for players and charters, not developers. Developer-facing changes go under `Technical Details`.
- Never edit the section of a version that has already been released.

## Gradle setup the workflows rely on

- `gradle.properties` holds `mod_version=0.1.0` and `archives_base_name=hexium`.
- `mod/build.gradle.kts` sets `version = mod_version`, so the jar is `mod/build/libs/hexium-<version>.jar` (plus a `-sources` jar that isn't released).
- `fabric.mod.json` reads its version from Gradle (`"version": "${version}"` via `processResources`). Never type a version anywhere else. It also sets "environment": "client" and lists fabric-language-kotlin and fabric-api under depends.
- `./gradlew build` builds all modules and runs the `core`, `tools` and `server` tests.

## Workflows

- [`.github/workflows/build.yml`](../.github/workflows/build.yml) builds and tests every push and pull request with JDK 25, lints the example charts, and uploads `mod/build/libs/` as the `hexium-dev-build` artifact. A commented-out job runs client game tests under a virtual display; turn it on in Milestone 2 once those tests exist (check Loom's task name first).
- [`.github/workflows/release.yml`](../.github/workflows/release.yml) runs when `gradle.properties` changes on `main` (or by hand). It skips forks, reads `mod_version`, and does nothing if the tag `v<mod_version>` already exists. Otherwise it takes the matching `## Version X.Y.Z` section of `CHANGELOG.md` as release notes (and fails if there isn't one), builds, and creates the GitHub Release with `hexium-<version>.jar` attached.
- Modrinth publishing is part of `release.yml` but stays off until the repository variable `MODRINTH_PROJECT_ID` and the secret `MODRINTH_TOKEN` are set. It lists Fabric API and Fabric Language Kotlin as required dependencies. Check the publishing action's current version and inputs before turning it on.

## Releasing a version

1. In `CHANGELOG.md`, rename `## Unreleased` to `## Version 0.2.0` and add a fresh empty `## Unreleased` above it.
2. Set `mod_version=0.2.0` in `gradle.properties`.
3. Commit both as `Update: Mod version 0.2.0 release`, push to your fork, and merge the PR into TriBinary/Hexium `main`.
4. `release.yml` builds, tags `v0.2.0`, attaches the jar and posts the changelog section. If the tag already exists it does nothing, so re-runs are safe.

Use semver as in TownyMenu: patch for fixes, minor for features, major for breaking changes (for example a chart format change old versions can't read). The online server is deployed separately and isn't part of these releases. Its deploy workflow comes in M7.
