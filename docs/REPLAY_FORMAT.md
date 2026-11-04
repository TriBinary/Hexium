# Replay format v1

A replay is every input of one play, in order, so the same judge that ran live can run again and give the same result. The server re-judges replays this way, so it never trusts a score sent by a client. The rules the judge applies are in [JUDGEMENT.md](JUDGEMENT.md).

## File (extension `.hexreplay`)

The file is gzip-compressed. Inside, all numbers are big-endian.

| Field | Size | Content |
| --- | --- | --- |
| Magic | 4 bytes | The text `HXRP` |
| Format version | 4 bytes, integer | `1` |
| Judge version | 4 bytes, integer | The judge rules the play ran under, see below |
| Chart hash | 32 bytes | SHA-256 of the difficulty file's bytes |
| Judgement offset B | 8 bytes, double | In ms. The judge subtracts it from every event time |
| Event count | 4 bytes, integer | How many events follow |
| Events | see below | In the order they happened |

Each event starts with one byte, the kind in the high 4 bits and the index in the low 4 bits, followed by an 8-byte double time in song milliseconds. Times are raw, before offset B is subtracted.

| Kind | Event | Index | Then |
| --- | --- | --- | --- |
| 0 | Aim sample | 0 | `x`, `y` as 4-byte floats |
| 1 | Lane key down | Lane | |
| 2 | Lane key up | Lane | |
| 3 | Sky button down | 0 = L, 1 = R | `x`, `y` as 4-byte floats |
| 4 | Sky button up | 0 = L, 1 = R | |

`x` and `y` are normalised screen coordinates, as in chart files, and may lie off the screen.

## Rules

- Times never go down, so a replay always runs forward. When a player resumes from pause and the notes rewind, input isn't judged or recorded until play passes the point where it paused. A lane key that changed in between is recorded as changing at exactly that point.
- Times, `x`, `y` and offset B are finite. Times may be negative: a play starts with a lead-in before the song.
- A file holds at most 2,000,000 events, about 2 hours at 240 FPS. Reading stops at that size once decompressed, so a small file can't unpack into something huge.
- A file whose contents have anything else wrong, including bytes left over after the last event, is rejected with a message. Reading never throws. Only the unpacked contents are checked, so bytes outside the gzip data are ignored.

## Judge version

The judge version is `JudgementConfig.JUDGE_VERSION`. Raise it whenever a change to the windows, weights or judge rules could change a replay's result. A replay only re-judges under the version it was played with, so old scores are never silently changed. Leaderboards are per chart hash, and what happens to old versions' scores is decided when the server is built (Milestone 7).

## Recording

The mod sends input through `PlayInput`, which holds the pause rule above, and on to `ReplayRecorder`, which records each event and passes it to the judge, so a replay holds exactly what the judge saw. An event older than the current time is recorded as happening now, which is also how the judge treats it.
