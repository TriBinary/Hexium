package net.trilleo.hexium.core.chart

enum class Severity { ERROR, WARNING }

/** What kind of problem an issue is. Stable, so the editor and tests can act on it. */
enum class IssueCode {
	INVALID_JSON,
	WRONG_TYPE,
	MISSING_FIELD,
	UNKNOWN_FIELD,
	UNKNOWN_VALUE,
	UNKNOWN_FORMAT_VERSION,
	FILE_TOO_LARGE,
	TOO_MANY_PROBLEMS,

	BAD_LANE_COUNT,
	BAD_LEVEL,
	OFFSET_NOT_FINITE,
	INVALID_BEAT,
	BPM_NOT_POSITIVE,
	FIRST_BPM_NOT_AT_ZERO,
	BPM_NOT_RISING,
	FIRST_METER_NOT_AT_ZERO,
	METER_NOT_RISING,
	METER_NOT_POSITIVE,
	LANE_OUT_OF_RANGE,
	HOLD_END_NOT_AFTER_START,
	HOLDS_OVERLAP,
	TAP_INSIDE_HOLD,
	DUPLICATE_TAP,
	SLIDE_TOO_FEW_POINTS,
	SLIDE_POINTS_NOT_RISING,
	POSITION_OUT_OF_RANGE,
	SIZE_NOT_POSITIVE,
	RADIUS_NOT_POSITIVE,
	DIR_OUT_OF_RANGE,
	NOTE_AFTER_AUDIO,
	TOO_MANY_JUDGED_OBJECTS,
	TOO_DENSE,

	BAD_ID,
	BLANK_TEXT,
	NEGATIVE_PREVIEW,
	NO_DIFFICULTIES,
	REPEATED_DIFFICULTY,
	MISSING_DIFFICULTY_FILE,
	UNLISTED_DIFFICULTY_FILE,
	DUPLICATE_DIFFICULTY_FILE,
	PACKAGE_TOO_LARGE,
}

/** A problem in a chart file. [path] points at it, for example `notes[3].lane`, or `$` for the whole file. */
data class ChartIssue(
	val severity: Severity,
	val code: IssueCode,
	val path: String,
	val message: String,
) {
	override fun toString(): String = "$severity $path: $message"
}

/** Cuts file text repeated in an issue, so a hostile file can't make huge messages. */
internal fun shortened(text: String): String =
	if (text.length <= ChartLimits.MAX_ISSUE_TEXT_CHARS) text else text.take(ChartLimits.MAX_ISSUE_TEXT_CHARS) + "…"
