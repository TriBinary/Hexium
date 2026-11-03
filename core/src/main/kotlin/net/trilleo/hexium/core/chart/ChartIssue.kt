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
