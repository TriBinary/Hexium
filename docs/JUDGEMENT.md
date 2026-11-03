# Judgement and scoring

Judgements use CHUNITHM's names and windows: Justice Critical, Justice, Attack, Miss. A perfect play scores 1,010,000. These windows are community-measured, not official, so they live in one config table in `core` and can be tuned later ([timing source](https://chunithm-org.pages.dev/basic/timing), [score weights](https://cavac.at/cavacopedia/Chunithm)).

## Windows (ms, ± around the target time)

| Applies to | Justice Critical | Justice | Attack | Miss |
| --- | --- | --- | --- | --- |
| Ground tap, ground hold head, sky tap (Expert and above) | ≤ 33.33 | ≤ 66.67 | ≤ 83.33 | beyond 83.33 |
| Same, easy charts (Basic, Advanced) | ≤ 33.33 | ≤ 66.67 | ≤ 100 | beyond 100 |
| Ground hold ticks, sky slide ticks (input before the tick) | −50 to 0 | −100 to −50 | −150 to −100 | earlier than −150 |
| Sky flick (adapted from CHUNITHM's flick swipe phase) | ≤ 83.33 | ≤ 166.67 | — | beyond 166.67 |

Limits are inclusive. For taps, sky taps and hold heads, the Miss window runs from 150 ms early up to the start of the Attack window: a press there uses up the note as a Miss. A press earlier than 150 ms does nothing. A note nobody pressed becomes a Miss once its Attack window has passed.

## Rules per note

- **Tap and sky tap:** judged by |press time − target|. A sky tap also needs the crosshair inside the note's hit area at the moment of the click. A note's hit area is a circle whose radius is the base hit radius in `JudgementConfig` (0.08) times the note's `size`. Hit radii are fractions of the screen's height, and circles are measured using the screen's 2:1 aspect, also in `JudgementConfig`. A press with no note in its Miss window does nothing (no penalty). A sky tap click outside the hit area, or with the wrong button, is ignored by that note, so the player can click again in time. The crosshair position is recorded with the click.
- **Ground hold:** the head is judged like a tap. The body has invisible ticks, one every 1/4 beat (fixed in `JudgementConfig`, not set per chart, at least one per hold). Ticks are counted from the hold's start and exclude its start and end. A hold too short for any tick gets one at its end. Each tick is judged at its own time, on how long ago the lane key was last down, even if it was pressed before the hold began. A press after the tick doesn't help it. Releasing and pressing again inside the window keeps Justice Critical, like CHUNITHM. There's no tail judgement, and holding past the end is never penalised.
- **Sky slide:** no head or tail judgement. Ticks every 1/4 beat along the path (the same fixed rate and placement as holds, from the first point to the last). A tick is "held" while the required button is down AND the crosshair is inside the path's hit radius (its `radius`, a fraction of the screen's height) at the path's current position. Before the first point, the position is the first point. Each tick is judged on how long ago the last aim sample that was "held" came, with the tick windows above.
- **Flick:** hit when the crosshair passes through the note's hit area between two frames, moving faster than a minimum speed (screen units per second), in the optional required direction. The judgement is the crossing time against the target: the moment the swipe first enters the hit area, worked out along the line between the two samples. A swipe that starts inside uses the time of that first sample. The minimum speed is 1 screen height per second and the direction tolerance is ±45°, both in `JudgementConfig`. One swipe can hit several flicks.
- A missed head doesn't auto-fail the hold's ticks. Each tick stands on its own.

## Score

Every judged object (tap, hold head, each tick, each flick) counts as one note out of N. Its accuracy weight is Justice Critical 101%, Justice 100%, Attack 50%, Miss 0%. Score = 1,000,000 × Σweight ÷ N, rounded down, so a perfect play is 1,010,000. A chart with no judged objects scores 0 and has no lamp. Every judged object also adds to combo, and a Miss breaks it.

Clear lamps: Full Combo (no Miss), All Justice (nothing below Justice), All Justice Critical. There's no life gauge or fail state in v1. Whether to add one later is an open question.

## Two offsets, like CHUNITHM

- **Visual offset (A):** shifts where notes are drawn relative to the music.
- **Judgement offset (B):** shifts the judgement windows without moving the notes.

CHUNITHM uses the same split ([settings](https://chunithm-org.pages.dev/basic/settings)). Both go on the mod's settings page with a calibration screen: tap to a metronome, and the mod suggests the values.
