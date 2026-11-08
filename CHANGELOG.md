# Hexium - Change Log

## Unreleased

### New Features

#### Menus

+ Added a Hexium button on the title screen that opens Hexium's menu.
    + The menu lists every chart it finds, including a built-in Test Pattern and any folders in `.minecraft/hexium/charts`.
    + Broken charts are marked and show their first problem, and the full list goes to the log.
+ Choosing a difficulty now plays it on the stage, and Esc offers a way back to the menu.

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
+ The built-in Test Pattern has a new Expert chart: 90 seconds of sky taps, slides and flicks all over the screen, corners included, mixed with ground notes.
+ Added a command-line chart linter, `lint`, that checks chart files and folders against the format.

#### Gameplay

+ Ground charts can now be played: press A S D F (the same positions on any keyboard layout) in time with the song, with combo, judgement and score on screen.
    + Presses are timed from when the key went down, not when the game got round to it.
    + Taps and holds come down the four lanes to a judgement line, with each lane lighting up while its key is down.
    + The pause screen sets the note speed, which changes how far away notes appear but never their timing, and a visual offset, which shifts where notes are drawn relative to the music.
+ Pausing is now fair: resuming goes back 3 seconds and plays the song under a 3-2-1 countdown, and judging starts again exactly where you paused.
    + Keys you changed while paused count from that moment, so you can get your fingers back in place.
+ Added a results screen with your score, clear lamp, judgement counts and best combo.
+ Added Retry, on the results and pause screens, which starts the chart again without reloading the stage.
+ Finished plays now save a replay in `.minecraft/hexium/replays`.
+ F6 during a play shows timing numbers: clock drift, frame time, how long key presses waited to reach the judge, how many arrived too late to keep their own time, and your offsets.
+ Added calibration, from the Calibrate button in Hexium's menu.
    + Tap to clicks with your eyes closed, then to moving notes with no sound, and it suggests a judgement offset and a visual offset that you can adjust before saving.
+ Your note speed and offsets are now remembered, in `.minecraft/hexium/settings.json`.
+ Added the stage: a plain floor with a raised platform you stand on, looking down the lanes, kept apart from your normal worlds.
    + You stand 9 blocks from the judgement line, with a sky screen 18 blocks wide and 9 tall whose middle is level with your eyes.
    + You can look around, but moving, jumping and action keys are off while you're on it.
    + The camera stops just past the sky screen's edges, so you can't turn away from the stage.
    + The hunger bar is hidden.
+ Added judging for ground taps and holds, with CHUNITHM's windows.
    + Hold ticks are judged on how long ago the key was last down.
+ Added scoring up to 1,010,000, combo and clear lamps (Full Combo, All Justice, All Justice Critical).

#### Sky Notes

+ Sky notes can now be played on the screen in front of you: aim with the camera and click.
    + Sky taps fly toward the screen and are hit with a left click (orange) or a right click (blue).
    + Slides are a trail of beads to follow with the button held, with a ring where the slide crosses the screen, white while you hold the button.
    + Flicks are magenta, with an arrow when they need a direction: swipe through them.
    + A faint ring on the screen shows where a coming note will land.
+ Added judging for sky taps, slides and flicks.
    + A swipe that jumps over a flick between two frames still hits it, so low frame rates stay fair.
+ Left and right clicks on the stage are now timed from when the button went down and placed where the crosshair meets the sky screen, ready for sky notes.
+ Pausing is fair for sky notes too: a mouse button you changed while paused counts from the moment you paused, and moving the mouse while paused never counts as a swipe through a flick.

#### Settings

+ Added comfort settings to the pause screen, kept in `.minecraft/hexium/settings.json`.
    + Mouse: your mouse sensitivity during play, on top of Minecraft's own, so menus feel the same.
    + FOV: the field of view during play, 70 by default.
    + View Assist: slowly brings the camera back to the middle of the sky screen when no sky notes are coming, off by default.

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

#### Gameplay

+ Fixed most key presses being timed by the frame that noticed them instead of when they happened.
    + Misses and hold ticks now show up to 50 ms later, while hits show as fast as before.

#### Sky Notes

+ Fixed an open flick window holding back other notes, so a press could count for a hold tick that had already passed.
+ Fixed a swipe already inside a flick when its window opens missing at some frame rates: it now counts as crossing at that moment.

#### Misc

+ Hexium now requires Fabric Language Kotlin 1.14.2 or newer, instead of failing at the first chart load on older versions.

### Technical Details

+ Added song playback through Hexium's own OpenAL source and an audio clock that follows it.
    + When the audio device changes or sounds reload, the song returns at the right place once the game has reloaded.
+ Added client game tests that load the stage in the real game and check that normal worlds are untouched.
+ Added an Audio Test screen with a debug overlay, for checking that the clock stays with the music over a whole song.
+ Added `rejudge` and `dump` to the command-line tools.
+ Added golden replays and property tests that guard the judge.
+ The build now lints the example charts.
