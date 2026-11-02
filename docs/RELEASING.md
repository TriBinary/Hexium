# Releasing

Two GitHub workflows, based on TownyMenu's, do all builds and releases. `build.yml` builds and tests every push and PR and keeps the jar as a downloadable artifact. `release.yml` publishes a GitHub Release, and optionally a Modrinth version, whenever a commit that changes `mod_version` lands on `main` in TriBinary/Hexium. Forks never release, so pushing to your fork is always safe.

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

## `.github/workflows/build.yml`

```yaml
# Builds and tests every push and pull request, and keeps the mod jar
# as a downloadable artifact (a dev build).
name: build
on: [push, pull_request]

jobs:
  build:
    runs-on: ubuntu-24.04
    steps:
      - name: checkout repository
        uses: actions/checkout@v6
      - name: validate gradle wrapper
        uses: gradle/actions/wrapper-validation@v6
      - name: setup jdk
        uses: actions/setup-java@v5
        with:
          java-version: '25'
          distribution: 'microsoft'
      - name: make gradle wrapper executable
        run: chmod +x ./gradlew
      - name: build and test
        run: ./gradlew build
      - name: capture mod jar
        uses: actions/upload-artifact@v7
        with:
          name: hexium-dev-build
          path: mod/build/libs/

  # Turn on from M2, once client game tests exist. Check the task name
  # Loom creates for client game tests in the 26.3 toolchain.
  # client-gametest:
  #   runs-on: ubuntu-24.04
  #   steps:
  #     - uses: actions/checkout@v6
  #     - uses: actions/setup-java@v5
  #       with: { java-version: '25', distribution: 'microsoft' }
  #     - run: chmod +x ./gradlew
  #     - run: xvfb-run -a ./gradlew :mod:runClientGameTest
```

## `.github/workflows/release.yml`

```yaml
# Creates a release when a new mod_version lands on main. The tag
# (v<mod_version>) is made by the release itself, in the repository the
# commit was merged into. Forks never release. The release notes are the
# matching "## Version X.Y.Z" section of CHANGELOG.md.
name: release
on:
  push:
    branches:
      - main
    paths:
      - gradle.properties
  workflow_dispatch:

permissions:
  contents: write

concurrency:
  group: release
  cancel-in-progress: false

jobs:
  release:
    if: ${{ !github.event.repository.fork }}
    runs-on: ubuntu-24.04
    steps:
      - name: checkout repository
        uses: actions/checkout@v6
      - name: read mod version
        id: version
        run: |
          VERSION="$(sed -n 's/^mod_version=//p' gradle.properties | tr -d '[:space:]')"
          if [ -z "$VERSION" ]; then
            echo "::error::mod_version is missing from gradle.properties"
            exit 1
          fi
          echo "version=$VERSION" >> "$GITHUB_OUTPUT"
          if git ls-remote --exit-code --tags origin "refs/tags/v$VERSION" > /dev/null; then
            echo "Release v$VERSION already exists, nothing to do."
            echo "exists=true" >> "$GITHUB_OUTPUT"
          else
            echo "exists=false" >> "$GITHUB_OUTPUT"
          fi
      - name: extract changelog section
        if: steps.version.outputs.exists == 'false'
        env:
          VERSION: ${{ steps.version.outputs.version }}
        run: |
          awk -v ver="$VERSION" '
            $0 == "## Version " ver { found = 1; next }
            found && /^## / { exit }
            found { print }
          ' CHANGELOG.md > RELEASE_NOTES.md
          if ! grep -q '[^[:space:]]' RELEASE_NOTES.md; then
            echo "::error::No changelog section for version $VERSION in CHANGELOG.md"
            exit 1
          fi
      - name: setup jdk
        if: steps.version.outputs.exists == 'false'
        uses: actions/setup-java@v5
        with:
          java-version: '25'
          distribution: 'microsoft'
      - name: make gradle wrapper executable
        if: steps.version.outputs.exists == 'false'
        run: chmod +x ./gradlew
      - name: build and test
        if: steps.version.outputs.exists == 'false'
        run: ./gradlew build
      - name: create github release
        if: steps.version.outputs.exists == 'false'
        uses: softprops/action-gh-release@v2
        with:
          tag_name: v${{ steps.version.outputs.version }}
          target_commitish: ${{ github.sha }}
          name: Hexium ${{ steps.version.outputs.version }}
          body_path: RELEASE_NOTES.md
          files: mod/build/libs/hexium-${{ steps.version.outputs.version }}.jar
      # Optional, from M9: also publish to Modrinth. Needs the repo variable
      # MODRINTH_PROJECT_ID and the secret MODRINTH_TOKEN. Check the action's
      # current version and inputs before turning it on.
      - name: publish to modrinth
        if: steps.version.outputs.exists == 'false' && vars.MODRINTH_PROJECT_ID != ''
        uses: Kir-Antipov/mc-publish@v3.3
        with:
          modrinth-id: ${{ vars.MODRINTH_PROJECT_ID }}
          modrinth-token: ${{ secrets.MODRINTH_TOKEN }}
          files: mod/build/libs/hexium-${{ steps.version.outputs.version }}.jar
          name: Hexium ${{ steps.version.outputs.version }}
          version: ${{ steps.version.outputs.version }}
          changelog-file: RELEASE_NOTES.md
          loaders: fabric
          game-versions: '26.3'
          dependencies: |
            fabric-api(required)
            fabric-language-kotlin(required)
```

## Releasing a version

1. In `CHANGELOG.md`, rename `## Unreleased` to `## Version 0.2.0` and add a fresh empty `## Unreleased` above it.
2. Set `mod_version=0.2.0` in `gradle.properties`.
3. Commit both as `Update: Mod version 0.2.0 release`, push to your fork, and merge the PR into TriBinary/Hexium `main`.
4. `release.yml` builds, tags `v0.2.0`, attaches the jar and posts the changelog section. If the tag already exists it does nothing, so re-runs are safe.

Use semver as in TownyMenu: patch for fixes, minor for features, major for breaking changes (for example a chart format change old versions can't read). The online server is deployed separately and isn't part of these releases. Its deploy workflow comes in M7.
