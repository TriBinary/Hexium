# Hexium - Change Log

## Unreleased

### New Features

#### Menus

+ Added a Hexium button on the title screen that opens Hexium's menu.
    + The menu lists every chart it finds, including a built-in Test Pattern and any folders in `.minecraft/hexium/charts`.
    + Broken charts are marked and show their first problem, and the full list goes to the log.
    + Charts with sky notes are shown but can't be selected yet.

#### Charts

+ Defined chart format v1.
    + Note times are exact beat fractions, so they never drift.
    + BPM changes and the chart offset set where each beat falls in the song.
+ Added reading and writing of chart files.
    + A broken chart lists every problem it has and where to find it, instead of stopping at the first one.
+ Added the chart validator.
    + Checks charts and packages against every rule in the chart format.
    + Warns when a moment is too dense to play with one hand on the keys.
+ Added basic, advanced and expert example charts for charters to learn the format from.
+ Added a command-line chart linter, `lint`, that checks chart files and folders against the format.

#### Gameplay

+ Added judging for ground taps and holds, with CHUNITHM's windows.
    + Hold ticks are judged on how long ago the key was last down.
+ Added scoring up to 1,010,000, combo and clear lamps (Full Combo, All Justice, All Justice Critical).

#### Sky Notes

+ Added judging for sky taps, slides and flicks.
    + A swipe that jumps over a flick between two frames still hits it, so low frame rates stay fair.

#### Online

+ Added replay files, which record every input of a play.
    + Re-judging a replay gives exactly the result of the live play, so scores can be checked.

### Fixes

#### Charts

+ Fixed chart files loading with numbers that aren't valid JSON, like `0.5f` or `01`.
+ Fixed charts passing validation with a BPM so small that note times broke.
+ Fixed chart files saved with a byte-order mark failing to load.
+ Fixed very large or badly broken chart files running out of memory.
    + Chart files are limited to 2 MiB, and reading one lists at most 100 problems.
+ Fixed whole-number fields accepting forms like `1e0`.
+ Fixed charts with a huge hold being able to run out of memory when their ticks are built.
+ Fixed charts with notes too far away to have a time passing validation.
+ Fixed difficulty files named for another difficulty passing the linter.

#### Sky Notes

+ Fixed an open flick window holding back other notes, so a press could count for a hold tick that had already passed.
+ Fixed a swipe already inside a flick when its window opens missing at some frame rates: it now counts as crossing at that moment.

#### Misc

+ Hexium now requires Fabric Language Kotlin 1.14.2 or newer, instead of failing at the first chart load on older versions.

### Technical Details

+ Added song playback through Hexium's own OpenAL source and an audio clock that follows it.
    + When the audio device changes or sounds reload, the song returns at the right place once the game has reloaded.
+ Added an Audio Test screen with a debug overlay, for checking that the clock stays with the music over a whole song.
+ Added `rejudge` and `dump` to the command-line tools.
+ Added golden replays and property tests that guard the judge.
+ The build now lints the example charts.
