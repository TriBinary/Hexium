# Commit messages

Every commit says what it changes in one line, starting with a tag, so the history
reads as a list of what Hexium gained, fixed and tidied.

## The subject line

    <Tag>: <What changes>

- One tag from the table below, a colon and a space.
- Then a sentence in the imperative present ("Add", "Show", "Stop", "Keep"), starting
  with a capital and ending without a full stop.
- Name the part of Hexium it touches ("the judge", "sky slides", "the editor's
  timeline", "song select", "leaderboards", "the stage loader"), so the line makes
  sense without the diff.
- Say what changes for the player or charter, not which files moved: "Keep hold ticks
  judged after resuming from pause", not "Reset lastHeld in HoldJudge.resume()".
- One line, however long it needs to be (most are 40–100 characters). Two related
  changes can share it with "and" or a comma; two unrelated ones are two commits.
- A short reason or source can follow in parentheses: "(CHUNITHM timing)".

## Tags

| Tag             | For                                                                                                 | Example                                                        |
| :-------------- | :-------------------------------------------------------------------------------------------------- | :------------------------------------------------------------- |
| **Feature**     | Something players or charters couldn't do before: a note type, screen, setting, editor tool, API route. | `Feature: Add sky flick notes to the judge and renderer`       |
| **Fix**         | Something that was wrong: a bug, a crash, drift, a mis-judged note, a security finding.             | `Fix: Stop hold ticks missing after resuming from pause`       |
| **Improvement** | Something that already worked, made better: how it looks, feels, reads, or how fast it runs.        | `Improvement: Batch note models to keep 240 FPS on dense charts` |
| **Internal**    | Nothing a player would notice: docs, specs, agent instructions, comments, refactors, tests alone.   | `Internal: Clarify slide tick rules in JUDGEMENT.md`           |
| **Backend**     | How Hexium is built and shipped: dependencies, Minecraft/Fabric versions, Gradle, CI, Docker, deployment. | `Backend: Update Minecraft to 26.4`                            |
| **Update**      | Mod version releases (the commit that changes `mod_version`).                                       | `Update: Mod version 0.3.0 release`                            |

### Choosing between them

- **Backend isn't server code.** A new API route or leaderboard rule is a Feature; a
  bug in one is a Fix. Backend is the machinery around the code (gradle files,
  .github/, the Dockerfile, server/deploy/).
- **Gameplay specs aren't Internal.** Changing a judgement window, score weight or the
  chart format changes how Hexium plays: tuning is an Improvement, a new chart field
  is a Feature, a wrong value is a Fix. Rewording a spec without changing it is Internal.
- **Feature or Improvement?** A new thing to do or see is a Feature, even inside an
  existing screen ("Add BPM tapping to the editor"). The same thing, only better, is an
  Improvement.
- **Performance** is an Improvement, unless it was slow enough to be broken (stutter
  that breaks timing, failed builds): then a Fix.
- **Tag the main change.** A feature's tests, docs and changelog entry ride in its
  commit under its tag; updating CLAUDE.md alongside doesn't make it Internal.

## Verbs

Pick the verb that says what happens now, not the tag again:

- `Fix: Keep the audio clock in sync after alt-tabbing`, not `Fix: Fix clock drift`.
- `Feature: Add …`, `Show …`, `Let charters …`, `Build …`, `Read …`.
- `Fix: Stop …`, `Keep …`, `Handle …`, `Reject …`, `Retry …`.
- `Improvement: Make …`, `Tidy …`, `Rework …`, `Narrow …`.

## Changelog

A Feature, Improvement or Fix commit adds its own entry under `## Unreleased` in
CHANGELOG.md, in the same commit (format in docs/RELEASING.md):

| Tag                        | Changelog category      |
| :------------------------- | :---------------------- |
| **Feature**                | `### New Features`      |
| **Improvement**            | `### Improvements`      |
| **Fix**                    | `### Fixes`             |
| **Backend** / **Internal** | `### Technical Details` |
| **Update**                 | Usually no entry        |

Feature areas: Gameplay, Sky Notes, Charts, Editor, Online, Menus, Settings, Misc.
Server-only changes get an entry under Online only when players notice them.

## Body and trailers

Most commits are the subject alone. Add a body (after a blank line, wrapped at 72) only
when the reason isn't obvious from the subject and the diff: what was going wrong and
why this is the fix. Trailers such as `Co-Authored-By:` go last, after another blank
line.

    Fix: Keep sky flicks registering at low frame rates

    Flicks were tested against the crosshair point of each frame, so a fast
    swipe at 60 FPS could jump over the note. Testing the line between two
    aim samples catches it.

## Follow-ups

A commit that finishes the one before it (a file left out, a test forgotten) says so,
under the tag of what it completes:
`Fix: Add the slide tick tests left out of the slide judge commit`.

## For agents

Don't commit (CLAUDE.md). At the end of a task, or of each phase of a longer one, give
the commit message for the change in a code block, written by these rules, and include
its changelog entry in the change. Never change `mod_version`, create tags or
merge into TriBinary/Hexium unless asked: that publishes a release.
