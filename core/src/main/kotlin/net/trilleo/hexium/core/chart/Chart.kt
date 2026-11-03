package net.trilleo.hexium.core.chart

/*
 * The chart format as data, exactly as in docs/CHART_FORMAT.md. These classes can hold
 * values the format forbids, so the validator can report them instead of the parser
 * failing on the first one.
 */

enum class Difficulty { BASIC, ADVANCED, EXPERT, MASTER }

enum class SkyButton { L, R }

/** Shapes the slide segment leaving a point. */
enum class Ease(val id: String) {
	LINEAR("linear"),
	IN("in"),
	OUT("out"),
	IN_OUT("inOut"),
}

/** One `<difficulty>.json` file. */
data class DifficultyChart(
	val formatVersion: Int,
	val difficulty: Difficulty,
	val level: Int,
	val laneCount: Int,
	val offsetMs: Double,
	val bpm: List<BpmChange>,
	val meter: List<MeterChange>,
	val notes: List<Note>,
)

data class BpmChange(val t: Beat, val bpm: Double)

data class MeterChange(val t: Beat, val num: Int, val den: Int)

sealed interface Note {
	data class Tap(val t: Beat, val lane: Int) : Note

	data class Hold(val t: Beat, val end: Beat, val lane: Int) : Note

	data class SkyTap(
		val t: Beat,
		val x: Double,
		val y: Double,
		val button: SkyButton,
		val size: Double,
	) : Note

	data class Slide(
		val button: SkyButton,
		val radius: Double,
		val points: List<SlidePoint>,
	) : Note

	/** [dir] is in degrees (0 = right, 90 = up), or null to accept any direction. */
	data class Flick(
		val t: Beat,
		val x: Double,
		val y: Double,
		val dir: Double?,
		val size: Double,
	) : Note
}

data class SlidePoint(val t: Beat, val x: Double, val y: Double, val ease: Ease)

/** The `meta.json` file. */
data class ChartMeta(
	val formatVersion: Int,
	val id: String,
	val title: String,
	val artist: String,
	val charter: String,
	val previewStartMs: Int,
	val license: License,
	val difficulties: List<Difficulty>,
)

data class License(val type: LicenseType, val source: String?)

enum class LicenseType(val id: String) {
	ORIGINAL("original"),
	PERMITTED("permitted"),
}

/** The chart data of a `.hexium` package: its `meta.json` and every difficulty file. */
data class ChartPackage(val meta: ChartMeta, val charts: List<DifficultyChart>)
