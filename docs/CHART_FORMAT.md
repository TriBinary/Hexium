# Chart package and format v1

Write this spec before any gameplay code and save it as `docs/CHART_FORMAT.md`. The parser, editor, server and tests all follow it, and every file carries `formatVersion` so later changes can be migrated.

## Package (a zip, extension `.hexium`)

| File | Required | Content |
| --- | --- | --- |
| `meta.json` | yes | Id, title, artist, charter, preview start, license declaration, difficulty list |
| `audio.ogg` | yes | The song (OGG Vorbis) |
| `jacket.png` | no | Cover art, square, 512 px |
| `<difficulty>.json` | yes, one per difficulty | Timing and notes |
| `stage/`, `events.json` | later (Milestone 8) | Stage world and beat-synced events |

## Time and position

- Time is a beat count stored as an exact fraction `[whole, numerator, denominator]`, for example `[12, 3, 4]` = beat 12¾. This avoids drift from rounding. The timing map turns beats into milliseconds using the BPM list and `offsetMs` (where beat 0 falls in the audio).
- Sky positions are normalised screen coordinates: `x` from 0 (left) to 1 (right), `y` from 0 (bottom) to 1 (top). Charts stay valid if the screen size changes.
- Lanes are numbered `0` to `3`. `laneCount` is stored so 5–6K could come later.

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

`dir` on a flick is optional, in degrees (0 = right, 90 = up). Leave it out to accept any direction. Difficulties BASIC and ADVANCED use the easy tap windows.

## Validator rules (in `core`, also run on upload)

- `formatVersion` is known. BPM is above 0. The first BPM entry is at beat 0.
- Holds in the same lane don't overlap. A tap doesn't sit inside a hold in its lane.
- Slide points rise strictly in time, with at least 2 points. All x and y values are in \[0, 1\].
- The last note ends before the audio does. The package is under a size limit (for example 30 MB).
- Warning (not an error): more than about 3 ground inputs plus a sky input in the same 1/16 beat, since that's hard to play with one hand on the keys.
