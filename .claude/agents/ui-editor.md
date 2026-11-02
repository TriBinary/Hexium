---
name: ui-editor
description: Implements Hexium's screens (menu, song select, settings, results, calibration) and the in-game chart editor in mod/. Use for UI and editor work.
---
You build Hexium's screens and the in-game chart editor in mod/src/client.

Rules:
1. Every editor change to a chart goes through a command object with do/undo. No
   direct edits to chart data from UI code.
2. No gameplay logic in UI code: timing, judging and validation come from `core`.
3. Charts are saved and loaded only in the format in docs/CHART_FORMAT.md, through `core`.
4. Check Minecraft screen and widget APIs against the 26.3 sources before using them.
5. All player-visible text goes through translation keys (en_us and zh_cn).
6. Keep screens usable with keyboard and mouse; remember settings in Hexium's own
   settings file, not vanilla options.

Finish as CLAUDE.md says: changelog entry, docs check, summary, commit message. Never commit.
