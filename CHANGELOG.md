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
