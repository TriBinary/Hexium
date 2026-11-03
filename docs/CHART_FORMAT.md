# Chart package and format v1

Write this spec before any gameplay code and save it as `docs/CHART_FORMAT.md`. The parser, editor, server and tests all follow it, and every file carries `formatVersion` so later changes can be migrated.

## Package (a zip, extension `.hexium`)

| File | Required | Content |
| --- | --- | --- |
| `meta.json` | yes | Id, title, artist, charter, preview start, license declaration, difficulty list |
| `audio.ogg` | yes | The song (OGG Vorbis) |
| `jacket.png` | no | Cover art, square, 512 px |
| `<difficulty>.json` | yes, one per difficulty | Timing and notes. The file name is the difficulty in lower case, for example `expert.json` |
| `stage/`, `events.json` | later (Milestone 8) | Stage world and beat-synced events |

## Time and position

- Time is a beat count stored as an exact fraction `[whole, numerator, denominator]`, for example `[12, 3, 4]` = beat 12¾. This avoids drift from rounding. `whole` is 0 or more, `denominator` is above 0 and `numerator` runs from 0 up to (not including) `denominator`. Fractions don't have to be reduced: `[12, 2, 4]` and `[12, 1, 2]` are the same time, but `[12, 5, 4]` is invalid. The timing map turns beats into milliseconds using the BPM list and `offsetMs` (where beat 0 falls in the audio).
- Sky positions are normalised screen coordinates: `x` from 0 (left) to 1 (right), `y` from 0 (bottom) to 1 (top). Charts stay valid if the screen size changes.
- Lanes are numbered `0` to `3`. `laneCount` is stored so 5–6K could come later.

## meta.json

```json
{
  "formatVersion": 1,
  "id": "example-song",
  "title": "Example Song",
  "artist": "Example Artist",
  "charter": "Example Charter",
  "previewStartMs": 30000,
  "license": { "type": "permitted", "source": "https://example.com/license" },
  "difficulties": [ "BASIC", "ADVANCED", "EXPERT" ]
}
```

| Field | Content |
| --- | --- |
| `id` | Lower-case slug, `[a-z0-9-]`, 1 to 64 characters |
| `title`, `artist`, `charter` | Display text, not blank |
| `previewStartMs` | Where the song select preview starts, an integer of 0 or more |
| `license.type` | `original` or `permitted` |
| `license.source` | Optional link or note showing where the permission comes from |
| `difficulties` | At least one difficulty, no repeats. Each one has its matching `<difficulty>.json` in the package, and the package has no other difficulty files |

Difficulties are `BASIC`, `ADVANCED`, `EXPERT` and `MASTER`.

## Example difficulty file

```json
{
  "formatVersion": 1,
  "difficulty": "EXPERT",
  "level": 11,
  "laneCount": 4,
  "offsetMs": 120,
  "bpm": [ { "t": [0,0,1], "bpm": 174 }, { "t": [64,0,1], "bpm": 87 } ],
  "meter": [ { "t": [0,0,1], "num": 4, "den": 4 } ],
  "notes": [
    { "type": "tap",   "t": [4,0,1], "lane": 0 },
    { "type": "hold",  "t": [4,1,2], "end": [6,0,1], "lane": 2 },
    { "type": "skyTap", "t": [5,0,1], "x": 0.25, "y": 0.60, "button": "L", "size": 1.0 },
    { "type": "slide", "button": "L", "radius": 0.06, "points": [
        { "t": [6,0,1], "x": 0.20, "y": 0.50, "ease": "linear" },
        { "t": [7,0,1], "x": 0.70, "y": 0.80, "ease": "inOut" } ] },
    { "type": "flick", "t": [8,0,1], "x": 0.50, "y": 0.40, "dir": 90, "size": 1.0 }
  ]
}
```

`dir` on a flick is optional, in degrees (0 = right, 90 = up). Leave it out to accept any direction. Difficulties BASIC and ADVANCED use the easy tap windows. `level` is a whole number.

`button` is required on sky taps and slides, and is `L` or `R`. Every other field in the example is required. `size` is a multiplier on the base hit area in `JudgementConfig`, so 1 is normal. `radius` is a fraction of the screen's height, so a slide's hit area is a circle: on a 6-block-tall screen, a `radius` of 0.06 is 0.36 blocks in every direction.

`ease` is `linear`, `in`, `out` or `inOut`. It shapes the segment leaving that point, so the last point's `ease` is ignored (but still required).

Optional fields are left out when unused, not set to `null`. Unknown fields are ignored with a warning, so a chart with extra fields still loads. JSON nested more than 32 levels deep is rejected, and numbers in known fields must follow JSON's grammar (`0.5f` and `01` are rejected). Whole-number fields, including every part of a beat, are digits with an optional minus (`1e0` and `1.0` are rejected). If a key is repeated in an object, the last one wins. A `meta.json` or difficulty file may be at most 2 MiB, counted in characters. The parser lists at most 100 problems in a file, then one final issue saying there are more. The validator has no such cap, so the editor can mark every problem. A byte-order mark at the start of a file is ignored.

Hold and slide tick rates are not part of the chart. They're fixed in `JudgementConfig` (see [JUDGEMENT.md](JUDGEMENT.md)).

## Validator rules (in `core`, also run on upload)

The validator returns errors and warnings and never throws. The audio length and package size are given by the caller, and a rule is skipped when its value isn't given (a loose difficulty file has neither). The server works out both values itself and never trusts the uploader's.

Errors:

- `formatVersion` is known. `laneCount` is 4 in format v1. `level` is 1 or more.
- Every beat time is a valid fraction (see Time and position).
- `offsetMs` is finite. BPM is above 0 and finite, and small enough values that a beat's length overflows are rejected. The first BPM entry is at beat 0. BPM entries rise strictly in time.
- The first meter entry is at beat 0. Meter entries rise strictly in time. `num` and `den` are above 0.
- `lane` is from 0 up to `laneCount`. A hold's `end` is after its `t`.
- Holds in the same lane don't overlap. A tap doesn't sit inside a hold in its lane. A hold covers its start up to, but not including, its end: a hold may start exactly where another ends, and a tap may sit exactly on a hold's end, but a tap on a hold's head is an error.
- No two taps or hold heads share the same time and lane, since one press resolves at most one note. (Two holds sharing a head are reported as an overlap.)
- Slide points rise strictly in time, with at least 2 points. All x and y values are in \[0, 1\].
- `size` and `radius` are finite and above 0. `dir` is from 0 up to (not including) 360.
- The last note ends before the audio does. The package is under 30 MiB.
- `meta.json` follows the rules in its section above, and every chart's `difficulty` is listed in it.

Warning (not an error):

- A sliding window shorter than 1/16 beat (notes exactly 1/16 beat apart aren't in the same window) holds at least 4 ground inputs and at least 1 sky input, since that's hard to play with one hand on the keys. Only note starts count: taps and hold heads for ground, sky taps, flicks and the first point of a slide for sky.
