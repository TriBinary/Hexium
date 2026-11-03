# Hexium - Change Log

## Unreleased

### New Features

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

#### Gameplay

+ Added judging for ground taps and holds, with CHUNITHM's windows.
    + Hold ticks are judged on how long ago the key was last down.
+ Added scoring up to 1,010,000, combo and clear lamps (Full Combo, All Justice, All Justice Critical).

### Fixes

#### Charts

+ Fixed chart files loading with numbers that aren't valid JSON, like `0.5f` or `01`.
+ Fixed charts passing validation with a BPM so small that note times broke.
+ Fixed chart files saved with a byte-order mark failing to load.
+ Fixed very large or badly broken chart files running out of memory.
    + Chart files are limited to 2 MiB, and reading one lists at most 100 problems.
+ Fixed whole-number fields accepting forms like `1e0`.
+ Fixed charts with a huge hold being able to run out of memory when their ticks are built.

#### Misc

+ Hexium now requires Fabric Language Kotlin 1.14.2 or newer, instead of failing at the first chart load on older versions.
