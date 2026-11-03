package net.trilleo.hexium.core.chart

import java.math.BigInteger
import net.trilleo.hexium.core.HexiumCore
import net.trilleo.hexium.core.judge.JudgeChart
import net.trilleo.hexium.core.judge.JudgementConfig

/** The numbers behind the validator's rules (docs/CHART_FORMAT.md). */
object ChartLimits {
	/** A package must be smaller than this. */
	const val MAX_PACKAGE_BYTES: Long = 30L * 1024 * 1024

	/** A chart or meta file may be this long, counted in characters. */
	const val MAX_JSON_FILE_CHARS: Int = 2 * 1024 * 1024

	/** The parser stops listing problems after this many. */
	const val MAX_PARSE_ISSUES: Int = 100

	/** File text repeated in an issue is cut to this length. */
	const val MAX_ISSUE_TEXT_CHARS: Int = 80

	/** Keeps a huge hold from exhausting memory when its ticks are built. */
	const val MAX_JUDGED_OBJECTS: Int = 1_000_000

	const val LANE_COUNT: Int = 4
	const val MAX_ID_LENGTH: Int = 64

	/** The density warning needs at least this many inputs in a window of 1/[DENSITY_WINDOW_DENOMINATOR] beat. */
	const val DENSITY_GROUND_INPUTS: Int = 4
	const val DENSITY_SKY_INPUTS: Int = 1
	const val DENSITY_WINDOW_DENOMINATOR: Int = 16
}

/**
 * Checks charts against the rules in docs/CHART_FORMAT.md. Never throws: every problem comes
 * back as an issue. A bad value is reported once, and rules that need it skip it.
 *
 * The audio length and package size come from the caller, and their rules are skipped when
 * they're null. The server must work them out itself, never trust the uploader's.
 */
object ChartValidator {
	fun validateChart(chart: DifficultyChart, audioDurationMs: Double? = null): List<ChartIssue> =
		ChartChecker(chart, audioDurationMs).check()

	fun validateMeta(meta: ChartMeta): List<ChartIssue> {
		val issues = ArrayList<ChartIssue>()
		if (meta.formatVersion != HexiumCore.CHART_FORMAT_VERSION) {
			issues += error(
				IssueCode.UNKNOWN_FORMAT_VERSION,
				"formatVersion",
				"is ${meta.formatVersion}, but this version of Hexium reads format ${HexiumCore.CHART_FORMAT_VERSION}",
			)
		}
		if (!isValidId(meta.id)) {
			issues += error(
				IssueCode.BAD_ID,
				"id",
				"is \"${shortened(meta.id)}\", must be 1 to ${ChartLimits.MAX_ID_LENGTH} characters from a-z, 0-9 and -",
			)
		}
		if (meta.title.isBlank()) issues += error(IssueCode.BLANK_TEXT, "title", "must not be blank")
		if (meta.artist.isBlank()) issues += error(IssueCode.BLANK_TEXT, "artist", "must not be blank")
		if (meta.charter.isBlank()) issues += error(IssueCode.BLANK_TEXT, "charter", "must not be blank")
		if (meta.previewStartMs < 0) {
			issues += error(IssueCode.NEGATIVE_PREVIEW, "previewStartMs", "is ${meta.previewStartMs}, must be 0 or more")
		}
		if (meta.difficulties.isEmpty()) {
			issues += error(IssueCode.NO_DIFFICULTIES, "difficulties", "must list at least one difficulty")
		}
		for (i in meta.difficulties.indices) {
			if (meta.difficulties.indexOf(meta.difficulties[i]) != i) {
				issues += error(IssueCode.REPEATED_DIFFICULTY, "difficulties[$i]", "${meta.difficulties[i]} is listed twice")
			}
		}
		return issues
	}

	fun validatePackage(
		pkg: ChartPackage,
		audioDurationMs: Double? = null,
		packageSizeBytes: Long? = null,
	): List<ChartIssue> {
		val issues = ArrayList<ChartIssue>()
		if (packageSizeBytes != null && packageSizeBytes >= ChartLimits.MAX_PACKAGE_BYTES) {
			issues += error(
				IssueCode.PACKAGE_TOO_LARGE,
				"$",
				"is $packageSizeBytes bytes, must be under ${ChartLimits.MAX_PACKAGE_BYTES}",
			)
		}
		for (issue in validateMeta(pkg.meta)) issues += issue.copy(path = "$META_FILE:${issue.path}")
		checkDifficultyFiles(pkg, issues)
		for (chart in pkg.charts) {
			val file = fileName(chart.difficulty)
			for (issue in validateChart(chart, audioDurationMs)) issues += issue.copy(path = "$file:${issue.path}")
		}
		return issues
	}

	private fun checkDifficultyFiles(pkg: ChartPackage, issues: MutableList<ChartIssue>) {
		val listed = pkg.meta.difficulties
		for (i in listed.indices) {
			if (listed.indexOf(listed[i]) != i) continue
			if (pkg.charts.none { it.difficulty == listed[i] }) {
				issues += error(
					IssueCode.MISSING_DIFFICULTY_FILE,
					"$META_FILE:difficulties[$i]",
					"lists ${listed[i]}, but the package has no ${fileName(listed[i])}",
				)
			}
		}
		val seen = HashSet<Difficulty>()
		for (chart in pkg.charts) {
			val file = fileName(chart.difficulty)
			if (chart.difficulty !in listed) {
				issues += error(IssueCode.UNLISTED_DIFFICULTY_FILE, file, "is not listed in $META_FILE")
			}
			if (!seen.add(chart.difficulty)) {
				issues += error(IssueCode.DUPLICATE_DIFFICULTY_FILE, file, "appears more than once in the package")
			}
		}
	}

	private fun isValidId(id: String): Boolean {
		if (id.isEmpty() || id.length > ChartLimits.MAX_ID_LENGTH) return false
		for (c in id) {
			if (c !in 'a'..'z' && c !in '0'..'9' && c != '-') return false
		}
		return true
	}

	/** The name a difficulty's file must have in a package. */
	fun fileName(difficulty: Difficulty): String = "${difficulty.name.lowercase()}.json"

	/** Whoever reads the files checks this, since [ChartPackage] keeps no file names. */
	fun validateFileName(chart: DifficultyChart, fileName: String): List<ChartIssue> {
		val expected = fileName(chart.difficulty)
		if (fileName == expected) return emptyList()
		return listOf(
			error(IssueCode.FILE_NAME_MISMATCH, "$", "holds ${chart.difficulty}, so it must be named $expected, not $fileName"),
		)
	}

	private const val META_FILE = "meta.json"
}

private fun error(code: IssueCode, path: String, message: String) = ChartIssue(Severity.ERROR, code, path, message)

private fun text(beat: Beat) = "[${beat.whole},${beat.num},${beat.den}]"

/** A tap or hold whose times are fit for the overlap rules. [end] is null for a tap. */
private class GroundNote(val t: Beat, val end: Beat?, val path: String)

/** Where a note starts, for the density warning. */
private class NoteStart(val t: Beat, val isSky: Boolean, val path: String)

private class ChartChecker(private val chart: DifficultyChart, private val audioDurationMs: Double?) {
	private val issues = ArrayList<ChartIssue>()
	private val groundByLane = LinkedHashMap<Int, MutableList<GroundNote>>()
	private val starts = ArrayList<NoteStart>()
	private var lastEnd: Beat? = null
	private var lastEndPath = ""
	private var judgedObjects = 0L

	fun check(): List<ChartIssue> {
		checkHeader()
		val bpmIsUsable = checkBpm()
		val offsetIsUsable = chart.offsetMs.isFinite()
		if (!offsetIsUsable) report(IssueCode.OFFSET_NOT_FINITE, "offsetMs", "is ${chart.offsetMs}, must be a finite number")
		checkMeter()
		for (i in chart.notes.indices) checkNote(chart.notes[i], "notes[$i]")
		for (lane in groundByLane.values) checkLaneOverlaps(lane)
		if (bpmIsUsable && offsetIsUsable) checkTimes()
		if (judgedObjects > ChartLimits.MAX_JUDGED_OBJECTS) {
			report(IssueCode.TOO_MANY_JUDGED_OBJECTS, "notes", "has $judgedObjects judged objects, the limit is ${ChartLimits.MAX_JUDGED_OBJECTS}")
		}
		checkDensity()
		return issues
	}

	private fun report(code: IssueCode, path: String, message: String) {
		issues += error(code, path, message)
	}

	private fun checkHeader() {
		if (chart.formatVersion != HexiumCore.CHART_FORMAT_VERSION) {
			report(
				IssueCode.UNKNOWN_FORMAT_VERSION,
				"formatVersion",
				"is ${chart.formatVersion}, but this version of Hexium reads format ${HexiumCore.CHART_FORMAT_VERSION}",
			)
		}
		if (chart.level < 1) report(IssueCode.BAD_LEVEL, "level", "is ${chart.level}, must be 1 or more")
		if (chart.laneCount != ChartLimits.LANE_COUNT) {
			report(IssueCode.BAD_LANE_COUNT, "laneCount", "is ${chart.laneCount}, must be ${ChartLimits.LANE_COUNT}")
		}
	}

	/** Reports an invalid beat and returns whether it was valid. */
	private fun checkBeat(beat: Beat, path: String): Boolean {
		if (beat.isValid) return true
		report(
			IssueCode.INVALID_BEAT,
			path,
			"is ${text(beat)}: whole must be 0 or more, and numerator from 0 up to the denominator, which must be above 0",
		)
		return false
	}

	/** Returns whether the BPM list is fit for the timing map. */
	private fun checkBpm(): Boolean {
		val before = issues.size
		if (chart.bpm.isEmpty()) report(IssueCode.FIRST_BPM_NOT_AT_ZERO, "bpm", "must have an entry at beat 0")
		var previous: Beat? = null
		for (i in chart.bpm.indices) {
			val change = chart.bpm[i]
			if (!TimingMap.isUsableBpm(change.bpm)) {
				report(IssueCode.BPM_NOT_POSITIVE, "bpm[$i].bpm", "is ${change.bpm}, must be above 0 and give a finite beat length")
			}
			val path = "bpm[$i].t"
			if (!checkBeat(change.t, path)) continue
			if (i == 0 && change.t.compareTo(BEAT_ZERO) != 0) {
				report(IssueCode.FIRST_BPM_NOT_AT_ZERO, path, "is ${text(change.t)}, the first entry must be at beat 0")
			}
			if (previous != null && change.t <= previous) {
				report(IssueCode.BPM_NOT_RISING, path, "is ${text(change.t)}, must be after the entry before it (${text(previous)})")
			}
			previous = change.t
		}
		return issues.size == before
	}

	private fun checkMeter() {
		if (chart.meter.isEmpty()) report(IssueCode.FIRST_METER_NOT_AT_ZERO, "meter", "must have an entry at beat 0")
		var previous: Beat? = null
		for (i in chart.meter.indices) {
			val change = chart.meter[i]
			if (change.num <= 0) report(IssueCode.METER_NOT_POSITIVE, "meter[$i].num", "is ${change.num}, must be above 0")
			if (change.den <= 0) report(IssueCode.METER_NOT_POSITIVE, "meter[$i].den", "is ${change.den}, must be above 0")
			val path = "meter[$i].t"
			if (!checkBeat(change.t, path)) continue
			if (i == 0 && change.t.compareTo(BEAT_ZERO) != 0) {
				report(IssueCode.FIRST_METER_NOT_AT_ZERO, path, "is ${text(change.t)}, the first entry must be at beat 0")
			}
			if (previous != null && change.t <= previous) {
				report(IssueCode.METER_NOT_RISING, path, "is ${text(change.t)}, must be after the entry before it (${text(previous)})")
			}
			previous = change.t
		}
	}

	private fun checkNote(note: Note, path: String) {
		when (note) {
			is Note.Tap -> checkTap(note, path)
			is Note.Hold -> checkHold(note, path)
			is Note.SkyTap -> checkSkyTap(note, path)
			is Note.Slide -> checkSlide(note, path)
			is Note.Flick -> checkFlick(note, path)
		}
	}

	private fun checkTap(note: Note.Tap, path: String) {
		val laneIsValid = checkLane(note.lane, path)
		if (!checkBeat(note.t, "$path.t")) return
		starts += NoteStart(note.t, false, path)
		noteEnd(note.t, path)
		judgedObjects++
		if (laneIsValid) groundNotes(note.lane) += GroundNote(note.t, null, path)
	}

	private fun checkHold(note: Note.Hold, path: String) {
		val laneIsValid = checkLane(note.lane, path)
		val startIsValid = checkBeat(note.t, "$path.t")
		val endIsValid = checkBeat(note.end, "$path.end")
		if (startIsValid) starts += NoteStart(note.t, false, path)
		if (!startIsValid || !endIsValid) return
		noteEnd(note.t, path)
		noteEnd(note.end, path)
		if (note.end <= note.t) {
			report(
				IssueCode.HOLD_END_NOT_AFTER_START,
				"$path.end",
				"is ${text(note.end)}, must be after the hold's start (${text(note.t)})",
			)
			return
		}
		judgedObjects += 1 + ticks(note.t, note.end)
		if (laneIsValid) groundNotes(note.lane) += GroundNote(note.t, note.end, path)
	}

	private fun checkSkyTap(note: Note.SkyTap, path: String) {
		checkPosition(note.x, note.y, path)
		checkSize(note.size, path)
		if (!checkBeat(note.t, "$path.t")) return
		starts += NoteStart(note.t, true, path)
		noteEnd(note.t, path)
		judgedObjects++
	}

	private fun checkSlide(note: Note.Slide, path: String) {
		if (!(note.radius > 0.0 && note.radius.isFinite())) {
			report(IssueCode.RADIUS_NOT_POSITIVE, "$path.radius", "is ${note.radius}, must be a finite number above 0")
		}
		if (note.points.size < 2) {
			report(IssueCode.SLIDE_TOO_FEW_POINTS, "$path.points", "has ${note.points.size}, must have at least 2")
		}
		var previous: Beat? = null
		var allValid = true
		for (i in note.points.indices) {
			val point = note.points[i]
			val pointPath = "$path.points[$i]"
			checkPosition(point.x, point.y, pointPath)
			if (!checkBeat(point.t, "$pointPath.t")) {
				allValid = false
				continue
			}
			if (previous != null && point.t <= previous) {
				report(
					IssueCode.SLIDE_POINTS_NOT_RISING,
					"$pointPath.t",
					"is ${text(point.t)}, must be after the point before it (${text(previous)})",
				)
			}
			previous = point.t
		}
		if (note.points.isNotEmpty() && note.points[0].t.isValid) {
			starts += NoteStart(note.points[0].t, true, path)
		}
		if (allValid) {
			for (point in note.points) noteEnd(point.t, path)
			val last = note.points.lastOrNull()
			if (last != null && last.t > note.points[0].t) judgedObjects += ticks(note.points[0].t, last.t)
		}
	}

	private fun checkFlick(note: Note.Flick, path: String) {
		checkPosition(note.x, note.y, path)
		checkSize(note.size, path)
		val dir = note.dir
		if (dir != null && !(dir >= 0.0 && dir < 360.0)) {
			report(IssueCode.DIR_OUT_OF_RANGE, "$path.dir", "is $dir, must be from 0 up to 360")
		}
		if (!checkBeat(note.t, "$path.t")) return
		starts += NoteStart(note.t, true, path)
		noteEnd(note.t, path)
		judgedObjects++
	}

	private fun ticks(start: Beat, end: Beat) = JudgeChart.tickCount(start, end, JudgementConfig.DEFAULT.ticksPerBeat)

	private fun checkLane(lane: Int, path: String): Boolean {
		if (lane >= 0 && lane < chart.laneCount) return true
		report(IssueCode.LANE_OUT_OF_RANGE, "$path.lane", "is $lane, must be from 0 up to ${chart.laneCount}")
		return false
	}

	private fun checkPosition(x: Double, y: Double, path: String) {
		if (!(x >= 0.0 && x <= 1.0)) report(IssueCode.POSITION_OUT_OF_RANGE, "$path.x", "is $x, must be from 0 to 1")
		if (!(y >= 0.0 && y <= 1.0)) report(IssueCode.POSITION_OUT_OF_RANGE, "$path.y", "is $y, must be from 0 to 1")
	}

	private fun checkSize(size: Double, path: String) {
		if (!(size > 0.0 && size.isFinite())) report(IssueCode.SIZE_NOT_POSITIVE, "$path.size", "is $size, must be a finite number above 0")
	}

	private fun groundNotes(lane: Int) = groundByLane.getOrPut(lane) { ArrayList() }

	private fun noteEnd(t: Beat, path: String) {
		val last = lastEnd
		if (last == null || t > last) {
			lastEnd = t
			lastEndPath = path
		}
	}

	/** Walks one lane in time order, in one pass so a huge uploaded chart can't take quadratic time. */
	private fun checkLaneOverlaps(lane: List<GroundNote>) {
		val sorted = lane.sortedWith(compareBy { it.t })
		var longest: GroundNote? = null
		for (note in sorted) {
			val end = note.end
			if (end == null) continue
			val held = longest
			if (held != null && note.t < held.end!!) {
				report(
					IssueCode.HOLDS_OVERLAP,
					note.path,
					"overlaps the hold at ${held.path}, which runs until ${text(held.end)}",
				)
			}
			if (held == null || end > held.end!!) longest = note
		}

		longest = null
		var nextHold = 0
		val holds = sorted.filter { it.end != null }
		var previousTap: GroundNote? = null
		for (note in sorted) {
			if (note.end != null) continue
			while (nextHold < holds.size && holds[nextHold].t <= note.t) {
				val hold = holds[nextHold++]
				val held = longest
				if (held == null || hold.end!! > held.end!!) longest = hold
			}
			val held = longest
			if (held != null && note.t < held.end!!) {
				report(
					IssueCode.TAP_INSIDE_HOLD,
					note.path,
					"at ${text(note.t)} sits inside the hold at ${held.path}, which runs until ${text(held.end)}",
				)
			}
			if (previousTap != null && previousTap.t.compareTo(note.t) == 0) {
				report(IssueCode.DUPLICATE_TAP, note.path, "is at the same time and lane as the tap at ${previousTap.path}")
			}
			previousTap = note
		}
	}

	/** Times must stay finite, or a note could never be judged. */
	private fun checkTimes() {
		val timing = TimingMap(chart.offsetMs, chart.bpm)
		for (i in chart.bpm.indices) {
			if (!timing.beatToMs(chart.bpm[i].t).isFinite()) {
				report(IssueCode.TIME_NOT_FINITE, "bpm[$i].t", "is ${text(chart.bpm[i].t)}, too far away to have a time at this BPM")
				return
			}
		}
		val end = lastEnd ?: return
		val endMs = timing.beatToMs(end)
		if (!endMs.isFinite()) {
			report(IssueCode.TIME_NOT_FINITE, lastEndPath, "ends at beat ${text(end)}, too far away to have a time at this BPM")
			return
		}
		if (audioDurationMs != null && !(endMs < audioDurationMs)) {
			report(IssueCode.NOTE_AFTER_AUDIO, lastEndPath, "ends at $endMs ms, but the audio ends at $audioDurationMs ms")
		}
	}

	private fun checkDensity() {
		val sorted = starts.sortedWith(compareBy { it.t })
		var left = 0
		var ground = 0
		var sky = 0
		var warnedUntil = -1
		for (right in sorted.indices) {
			if (sorted[right].isSky) sky++ else ground++
			while (!isInsideDensityWindow(sorted[left].t, sorted[right].t)) {
				if (sorted[left].isSky) sky-- else ground--
				left++
			}
			if (ground >= ChartLimits.DENSITY_GROUND_INPUTS && sky >= ChartLimits.DENSITY_SKY_INPUTS) {
				if (left > warnedUntil) {
					issues += ChartIssue(
						Severity.WARNING,
						IssueCode.TOO_DENSE,
						sorted[left].path,
						"starts $ground ground and $sky sky inputs within 1/${ChartLimits.DENSITY_WINDOW_DENOMINATOR} beat, " +
							"which is hard to play with one hand on the keys",
					)
				}
				warnedUntil = right
			}
		}
	}

	/** Exact, since denominators can be anything: whether `later - earlier` is under the window. */
	private fun isInsideDensityWindow(earlier: Beat, later: Beat): Boolean {
		val wholeBeats = later.whole - earlier.whole
		if (wholeBeats > 1) return false
		val earlierDen = BigInteger.valueOf(earlier.den.toLong())
		val laterDen = BigInteger.valueOf(later.den.toLong())
		val bothDens = earlierDen * laterDen
		val difference = BigInteger.valueOf(wholeBeats.toLong()) * bothDens +
			BigInteger.valueOf(later.num.toLong()) * earlierDen -
			BigInteger.valueOf(earlier.num.toLong()) * laterDen
		return difference * BigInteger.valueOf(ChartLimits.DENSITY_WINDOW_DENOMINATOR.toLong()) < bothDens
	}

	private companion object {
		val BEAT_ZERO = Beat(0, 0, 1)
	}
}
