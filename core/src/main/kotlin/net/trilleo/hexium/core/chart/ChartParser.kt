package net.trilleo.hexium.core.chart

import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import net.trilleo.hexium.core.HexiumCore

/** [value] is null when [issues] holds an error. */
data class ParseResult<out T>(val value: T?, val issues: List<ChartIssue>)

/**
 * Reads chart files. Never throws: every problem in the file comes back as an issue.
 *
 * This only checks a file's shape (fields, types, known names). Whether the values follow
 * the format's rules is the validator's job.
 */
object ChartParser {
	fun parseDifficulty(text: String): ParseResult<DifficultyChart> = parse(text, FileReader::readDifficulty)

	fun parseMeta(text: String): ParseResult<ChartMeta> = parse(text, FileReader::readMeta)

	private fun <T> parse(text: String, read: FileReader.(JsonObject) -> T?): ParseResult<T> {
		val reader = FileReader()
		if (nestsTooDeeply(text)) {
			reader.error(IssueCode.INVALID_JSON, ROOT, "is not valid JSON: nested more than $MAX_NESTING levels deep")
			return ParseResult(null, reader.issues)
		}
		val root = try {
			Json.parseToJsonElement(text)
		} catch (e: SerializationException) {
			reader.error(IssueCode.INVALID_JSON, ROOT, "is not valid JSON: ${e.message?.lineSequence()?.first()}")
			return ParseResult(null, reader.issues)
		}
		if (root !is JsonObject) {
			reader.error(IssueCode.WRONG_TYPE, ROOT, "must be an object")
			return ParseResult(null, reader.issues)
		}
		if (!reader.readFormatVersion(root)) return ParseResult(null, reader.issues)
		val value = reader.read(root)
		return ParseResult(if (reader.hasError) null else value, reader.issues)
	}

	/** The JSON library overflows the stack on extreme nesting, and uploads can be hostile. */
	private fun nestsTooDeeply(text: String): Boolean {
		var depth = 0
		var inString = false
		var i = 0
		while (i < text.length) {
			val c = text[i]
			if (inString) {
				if (c == '\\') i++ else if (c == '"') inString = false
			} else if (c == '"') {
				inString = true
			} else if (c == '[' || c == '{') {
				if (++depth > MAX_NESTING) return true
			} else if (c == ']' || c == '}') {
				depth--
			}
			i++
		}
		return false
	}
}

private const val ROOT = "$"

private const val FORMAT_VERSION = "formatVersion"

/** Far more than a chart needs (6 levels), with room for unknown fields. */
private const val MAX_NESTING = 32

private val DIFFICULTY_FIELDS = setOf(FORMAT_VERSION, "difficulty", "level", "laneCount", "offsetMs", "bpm", "meter", "notes")
private val META_FIELDS =
	setOf(FORMAT_VERSION, "id", "title", "artist", "charter", "previewStartMs", "license", "difficulties")
private val LICENSE_FIELDS = setOf("type", "source")
private val BPM_FIELDS = setOf("t", "bpm")
private val METER_FIELDS = setOf("t", "num", "den")
private val TAP_FIELDS = setOf("type", "t", "lane")
private val HOLD_FIELDS = setOf("type", "t", "end", "lane")
private val SKY_TAP_FIELDS = setOf("type", "t", "x", "y", "button", "size")
private val SLIDE_FIELDS = setOf("type", "button", "radius", "points")
private val SLIDE_POINT_FIELDS = setOf("t", "x", "y", "ease")
private val FLICK_FIELDS = setOf("type", "t", "x", "y", "dir", "size")

private val DIFFICULTY_NAMES = Difficulty.entries.map { it.name to it }
private val BUTTON_NAMES = SkyButton.entries.map { it.name to it }
private val EASE_NAMES = Ease.entries.map { it.id to it }
private val LICENSE_NAMES = LicenseType.entries.map { it.id to it }

/** Reads one file, collecting issues. A read returns null when it reported an error. */
private class FileReader {
	val issues = ArrayList<ChartIssue>()
	var hasError = false
		private set

	fun error(code: IssueCode, path: String, message: String) {
		hasError = true
		issues += ChartIssue(Severity.ERROR, code, path, message)
	}

	fun readFormatVersion(root: JsonObject): Boolean {
		val version = int(root, FORMAT_VERSION, ROOT) ?: return false
		if (version != HexiumCore.CHART_FORMAT_VERSION) {
			error(
				IssueCode.UNKNOWN_FORMAT_VERSION,
				FORMAT_VERSION,
				"is $version, but this version of Hexium reads format ${HexiumCore.CHART_FORMAT_VERSION}",
			)
			return false
		}
		return true
	}

	fun readDifficulty(root: JsonObject): DifficultyChart? {
		warnUnknownFields(root, ROOT, DIFFICULTY_FIELDS)
		val difficulty = choice(root, "difficulty", ROOT, DIFFICULTY_NAMES)
		val level = int(root, "level", ROOT)
		val laneCount = int(root, "laneCount", ROOT)
		val offsetMs = double(root, "offsetMs", ROOT)
		val bpm = objects(root, "bpm", ROOT, ::readBpm)
		val meter = objects(root, "meter", ROOT, ::readMeter)
		val notes = objects(root, "notes", ROOT, ::readNote)
		if (difficulty == null || level == null || laneCount == null || offsetMs == null) return null
		if (bpm == null || meter == null || notes == null) return null
		return DifficultyChart(HexiumCore.CHART_FORMAT_VERSION, difficulty, level, laneCount, offsetMs, bpm, meter, notes)
	}

	fun readMeta(root: JsonObject): ChartMeta? {
		warnUnknownFields(root, ROOT, META_FIELDS)
		val id = string(root, "id", ROOT)
		val title = string(root, "title", ROOT)
		val artist = string(root, "artist", ROOT)
		val charter = string(root, "charter", ROOT)
		val previewStartMs = int(root, "previewStartMs", ROOT)
		val license = child(root, "license", ROOT)?.let { readLicense(it, "license") }
		val difficulties = difficulties(root)
		if (id == null || title == null || artist == null || charter == null) return null
		if (previewStartMs == null || license == null || difficulties == null) return null
		return ChartMeta(HexiumCore.CHART_FORMAT_VERSION, id, title, artist, charter, previewStartMs, license, difficulties)
	}

	private fun readLicense(element: JsonElement, path: String): License? {
		val license = asObject(element, path) ?: return null
		warnUnknownFields(license, path, LICENSE_FIELDS)
		val type = choice(license, "type", path, LICENSE_NAMES)
		val hasSource = "source" in license
		val source = if (hasSource) string(license, "source", path) else null
		if (type == null || (hasSource && source == null)) return null
		return License(type, source)
	}

	private fun difficulties(root: JsonObject): List<Difficulty>? {
		val array = array(root, "difficulties", ROOT) ?: return null
		val result = ArrayList<Difficulty>(array.size)
		for (i in array.indices) {
			val path = "difficulties[$i]"
			val text = textOf(array[i], path) ?: continue
			result += choiceOf(text, path, DIFFICULTY_NAMES) ?: continue
		}
		return result
	}

	private fun readBpm(bpm: JsonObject, path: String): BpmChange? {
		warnUnknownFields(bpm, path, BPM_FIELDS)
		val t = beat(bpm, "t", path)
		val value = double(bpm, "bpm", path)
		if (t == null || value == null) return null
		return BpmChange(t, value)
	}

	private fun readMeter(meter: JsonObject, path: String): MeterChange? {
		warnUnknownFields(meter, path, METER_FIELDS)
		val t = beat(meter, "t", path)
		val num = int(meter, "num", path)
		val den = int(meter, "den", path)
		if (t == null || num == null || den == null) return null
		return MeterChange(t, num, den)
	}

	private fun readNote(note: JsonObject, path: String): Note? {
		val type = string(note, "type", path) ?: return null
		return when (type) {
			"tap" -> readTap(note, path)
			"hold" -> readHold(note, path)
			"skyTap" -> readSkyTap(note, path)
			"slide" -> readSlide(note, path)
			"flick" -> readFlick(note, path)
			else -> {
				error(
					IssueCode.UNKNOWN_VALUE,
					join(path, "type"),
					"is \"$type\", must be one of tap, hold, skyTap, slide, flick",
				)
				null
			}
		}
	}

	private fun readTap(note: JsonObject, path: String): Note? {
		warnUnknownFields(note, path, TAP_FIELDS)
		val t = beat(note, "t", path)
		val lane = int(note, "lane", path)
		if (t == null || lane == null) return null
		return Note.Tap(t, lane)
	}

	private fun readHold(note: JsonObject, path: String): Note? {
		warnUnknownFields(note, path, HOLD_FIELDS)
		val t = beat(note, "t", path)
		val end = beat(note, "end", path)
		val lane = int(note, "lane", path)
		if (t == null || end == null || lane == null) return null
		return Note.Hold(t, end, lane)
	}

	private fun readSkyTap(note: JsonObject, path: String): Note? {
		warnUnknownFields(note, path, SKY_TAP_FIELDS)
		val t = beat(note, "t", path)
		val x = double(note, "x", path)
		val y = double(note, "y", path)
		val button = choice(note, "button", path, BUTTON_NAMES)
		val size = double(note, "size", path)
		if (t == null || x == null || y == null || button == null || size == null) return null
		return Note.SkyTap(t, x, y, button, size)
	}

	private fun readSlide(note: JsonObject, path: String): Note? {
		warnUnknownFields(note, path, SLIDE_FIELDS)
		val button = choice(note, "button", path, BUTTON_NAMES)
		val radius = double(note, "radius", path)
		val points = objects(note, "points", path, ::readSlidePoint)
		if (button == null || radius == null || points == null) return null
		return Note.Slide(button, radius, points)
	}

	private fun readSlidePoint(point: JsonObject, path: String): SlidePoint? {
		warnUnknownFields(point, path, SLIDE_POINT_FIELDS)
		val t = beat(point, "t", path)
		val x = double(point, "x", path)
		val y = double(point, "y", path)
		val ease = choice(point, "ease", path, EASE_NAMES)
		if (t == null || x == null || y == null || ease == null) return null
		return SlidePoint(t, x, y, ease)
	}

	private fun readFlick(note: JsonObject, path: String): Note? {
		warnUnknownFields(note, path, FLICK_FIELDS)
		val t = beat(note, "t", path)
		val x = double(note, "x", path)
		val y = double(note, "y", path)
		val hasDir = "dir" in note
		val dir = if (hasDir) double(note, "dir", path) else null
		val size = double(note, "size", path)
		if (t == null || x == null || y == null || size == null || (hasDir && dir == null)) return null
		return Note.Flick(t, x, y, dir, size)
	}

	private fun warnUnknownFields(obj: JsonObject, path: String, known: Set<String>) {
		for (key in obj.keys) {
			if (key !in known) {
				issues += ChartIssue(
					Severity.WARNING,
					IssueCode.UNKNOWN_FIELD,
					join(path, key),
					"is not a known field and was ignored",
				)
			}
		}
	}

	private fun child(obj: JsonObject, key: String, path: String): JsonElement? {
		val element = obj[key]
		if (element == null) error(IssueCode.MISSING_FIELD, join(path, key), "is missing")
		return element
	}

	private fun wrongType(path: String, expected: String) {
		error(IssueCode.WRONG_TYPE, path, "must be $expected")
	}

	private fun asObject(element: JsonElement, path: String): JsonObject? {
		if (element !is JsonObject) wrongType(path, "an object")
		return element as? JsonObject
	}

	private fun array(obj: JsonObject, key: String, path: String): JsonArray? {
		val element = child(obj, key, path) ?: return null
		if (element !is JsonArray) wrongType(join(path, key), "a list")
		return element as? JsonArray
	}

	/** Reads a list of objects. Items with errors are left out, which is fine: the file has failed by then. */
	private fun <T : Any> objects(
		obj: JsonObject,
		key: String,
		path: String,
		read: (JsonObject, String) -> T?,
	): List<T>? {
		val array = array(obj, key, path) ?: return null
		val listPath = join(path, key)
		val result = ArrayList<T>(array.size)
		for (i in array.indices) {
			val itemPath = "$listPath[$i]"
			val item = asObject(array[i], itemPath) ?: continue
			result += read(item, itemPath) ?: continue
		}
		return result
	}

	private fun int(obj: JsonObject, key: String, path: String): Int? {
		val element = child(obj, key, path) ?: return null
		val value = intOf(element)
		if (value == null) wrongType(join(path, key), "a whole number")
		return value
	}

	private fun intOf(element: JsonElement): Int? {
		val primitive = element as? JsonPrimitive ?: return null
		if (primitive.isString || primitive is JsonNull) return null
		return primitive.intOrNull
	}

	private fun double(obj: JsonObject, key: String, path: String): Double? {
		val element = child(obj, key, path) ?: return null
		val primitive = element as? JsonPrimitive
		val value = if (primitive == null || primitive.isString || primitive is JsonNull) null else primitive.doubleOrNull
		if (value == null || !value.isFinite()) {
			wrongType(join(path, key), "a number")
			return null
		}
		return value
	}

	private fun string(obj: JsonObject, key: String, path: String): String? {
		val element = child(obj, key, path) ?: return null
		return textOf(element, join(path, key))
	}

	private fun textOf(element: JsonElement, path: String): String? {
		val primitive = element as? JsonPrimitive
		if (primitive == null || !primitive.isString) {
			wrongType(path, "text")
			return null
		}
		return primitive.content
	}

	private fun <T> choice(obj: JsonObject, key: String, path: String, names: List<Pair<String, T>>): T? {
		val text = string(obj, key, path) ?: return null
		return choiceOf(text, join(path, key), names)
	}

	private fun <T> choiceOf(text: String, path: String, names: List<Pair<String, T>>): T? {
		for ((name, value) in names) {
			if (name == text) return value
		}
		error(IssueCode.UNKNOWN_VALUE, path, "is \"$text\", must be one of ${names.joinToString { it.first }}")
		return null
	}

	private fun beat(obj: JsonObject, key: String, path: String): Beat? {
		val element = child(obj, key, path) ?: return null
		val array = element as? JsonArray
		if (array != null && array.size == 3) {
			val whole = intOf(array[0])
			val num = intOf(array[1])
			val den = intOf(array[2])
			if (whole != null && num != null && den != null) return Beat(whole, num, den)
		}
		wrongType(join(path, key), "[whole, numerator, denominator], all whole numbers")
		return null
	}

	private fun join(path: String, key: String): String = if (path == ROOT) key else "$path.$key"
}
