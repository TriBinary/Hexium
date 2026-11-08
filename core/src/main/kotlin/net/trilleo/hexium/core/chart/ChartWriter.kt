package net.trilleo.hexium.core.chart

import kotlinx.serialization.json.JsonPrimitive

/**
 * Writes chart files in a fixed layout, one note per line, so the same chart always gives
 * the same text and diffs stay readable. It writes what it's given and doesn't validate,
 * but throws [IllegalArgumentException] on NaN or infinity, which JSON can't hold.
 */
object ChartWriter {
	fun writeDifficulty(chart: DifficultyChart): String = buildString {
		append("{\n")
		append("  \"formatVersion\": ").append(chart.formatVersion).append(",\n")
		append("  \"difficulty\": ").append(text(chart.difficulty.name)).append(",\n")
		append("  \"level\": ").append(chart.level).append(",\n")
		append("  \"laneCount\": ").append(chart.laneCount).append(",\n")
		append("  \"offsetMs\": ").append(number(chart.offsetMs)).append(",\n")
		appendList("bpm", chart.bpm) { append(bpmLine(it)) }
		append(",\n")
		appendList("meter", chart.meter) { append(meterLine(it)) }
		append(",\n")
		appendList("notes", chart.notes) { appendNote(it) }
		append("\n}\n")
	}

	fun writeMeta(meta: ChartMeta): String = buildString {
		append("{\n")
		append("  \"formatVersion\": ").append(meta.formatVersion).append(",\n")
		append("  \"id\": ").append(text(meta.id)).append(",\n")
		append("  \"title\": ").append(text(meta.title)).append(",\n")
		append("  \"artist\": ").append(text(meta.artist)).append(",\n")
		append("  \"charter\": ").append(text(meta.charter)).append(",\n")
		append("  \"previewStartMs\": ").append(meta.previewStartMs).append(",\n")
		append("  \"license\": { \"type\": ").append(text(meta.license.type.id))
		if (meta.license.source != null) append(", \"source\": ").append(text(meta.license.source))
		append(" },\n")
		append("  \"difficulties\": [ ")
		append(meta.difficulties.joinToString { text(it.name) })
		append(" ]\n}\n")
	}

	private fun <T> StringBuilder.appendList(key: String, items: List<T>, appendItem: StringBuilder.(T) -> Unit) {
		append("  \"").append(key).append("\": [")
		for (i in items.indices) {
			append(if (i == 0) "\n    " else ",\n    ")
			appendItem(items[i])
		}
		if (items.isNotEmpty()) append("\n  ")
		append("]")
	}

	private fun bpmLine(bpm: BpmChange) = "{ \"t\": ${beat(bpm.t)}, \"bpm\": ${number(bpm.bpm)} }"

	private fun meterLine(meter: MeterChange) = "{ \"t\": ${beat(meter.t)}, \"num\": ${meter.num}, \"den\": ${meter.den} }"

	private fun StringBuilder.appendNote(note: Note) {
		when (note) {
			is Note.Tap -> append("{ \"type\": \"tap\", \"t\": ${beat(note.t)}, \"lane\": ${note.lane} }")
			is Note.Hold ->
				append("{ \"type\": \"hold\", \"t\": ${beat(note.t)}, \"end\": ${beat(note.end)}, \"lane\": ${note.lane} }")
			is Note.SkyTap -> append(
				"{ \"type\": \"skyTap\", \"t\": ${beat(note.t)}, \"x\": ${number(note.x)}, \"y\": ${number(note.y)}, " +
					"\"button\": \"${note.button.name}\", \"size\": ${number(note.size)} }",
			)
			is Note.Slide -> {
				append("{ \"type\": \"slide\", \"button\": \"${note.button.name}\", \"radius\": ${number(note.radius)}, \"points\": [")
				for (i in note.points.indices) {
					append(if (i == 0) "\n      " else ",\n      ")
					append(slidePointLine(note.points[i]))
				}
				if (note.points.isNotEmpty()) append("\n    ")
				append("] }")
			}
			is Note.Flick -> {
				append("{ \"type\": \"flick\", \"t\": ${beat(note.t)}, \"x\": ${number(note.x)}, \"y\": ${number(note.y)}, ")
				append("\"size\": ${number(note.size)} }")
			}
		}
	}

	private fun slidePointLine(point: SlidePoint) =
		"{ \"t\": ${beat(point.t)}, \"x\": ${number(point.x)}, \"y\": ${number(point.y)}, \"ease\": \"${point.ease.id}\" }"

	private fun beat(beat: Beat) = "[${beat.whole},${beat.num},${beat.den}]"

	private fun text(value: String) = JsonPrimitive(value).toString()

	/** Whole values are written without a decimal point. Other values keep the digits that read back exactly. */
	private fun number(value: Double): String {
		require(value.isFinite()) { "JSON can't hold $value" }
		val whole = value.toLong()
		return if (whole.toDouble().equals(value)) whole.toString() else value.toString()
	}
}
