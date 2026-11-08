package net.trilleo.hexium.core.chart

import net.trilleo.hexium.core.judge.JudgementConfig

/**
 * A chart's sky taps, slides and flicks in time order, in song milliseconds, for drawing. A note's
 * radius is the judge's hit radius, so what's drawn is what counts. Lookups don't allocate, so
 * they're safe in per-frame code, and keep no position of their own, so time can jump either way.
 * How far away a note is drawn comes from [GroundTrack.distance], as for ground notes.
 *
 * Expects a chart that passed the validator.
 */
class SkyTrack(chart: DifficultyChart, config: JudgementConfig = JudgementConfig.DEFAULT) {
	enum class Kind { TAP, SLIDE, FLICK }

	private class Entry(
		val kind: Kind,
		val startMs: Double,
		val endMs: Double,
		val x: Double,
		val y: Double,
		val button: SkyButton?,
		val radius: Double,
		val directionDegrees: Double,
		val chartNote: Int,
		val path: SlidePath?,
	)

	private val timing = TimingMap(chart.offsetMs, chart.bpm)
	private val kinds: Array<Kind>
	private val starts: DoubleArray
	private val ends: DoubleArray
	private val xs: DoubleArray
	private val ys: DoubleArray
	private val buttons: Array<SkyButton?>
	private val radii: DoubleArray
	private val directions: DoubleArray
	private val chartNotes: IntArray
	private val paths: Array<SlidePath?>

	/** The latest end among each note and all before it. It always rises, so it can be searched. */
	private val endsSoFar: DoubleArray

	init {
		val entries = ArrayList<Entry>()
		for (i in chart.notes.indices) {
			when (val note = chart.notes[i]) {
				is Note.SkyTap -> {
					val ms = timing.beatToMs(note.t)
					entries += Entry(Kind.TAP, ms, ms, note.x, note.y, note.button, config.baseHitRadius * note.size, Double.NaN, i, null)
				}
				is Note.Flick -> {
					val ms = timing.beatToMs(note.t)
					entries += Entry(Kind.FLICK, ms, ms, note.x, note.y, null, config.baseHitRadius * note.size, note.dir ?: Double.NaN, i, null)
				}
				is Note.Slide -> {
					val first = note.points.first()
					entries += Entry(
						Kind.SLIDE, timing.beatToMs(first.t), timing.beatToMs(note.points.last().t),
						first.x, first.y, note.button, note.radius, Double.NaN, i, SlidePath(note.points),
					)
				}
				else -> {}
			}
		}
		// Stable, so notes at the same time keep the chart's order.
		entries.sortWith(compareBy { it.startMs })
		kinds = Array(entries.size) { entries[it].kind }
		starts = DoubleArray(entries.size) { entries[it].startMs }
		ends = DoubleArray(entries.size) { entries[it].endMs }
		xs = DoubleArray(entries.size) { entries[it].x }
		ys = DoubleArray(entries.size) { entries[it].y }
		buttons = Array(entries.size) { entries[it].button }
		radii = DoubleArray(entries.size) { entries[it].radius }
		directions = DoubleArray(entries.size) { entries[it].directionDegrees }
		chartNotes = IntArray(entries.size) { entries[it].chartNote }
		paths = Array(entries.size) { entries[it].path }
		endsSoFar = DoubleArray(entries.size)
		var latest = Double.NEGATIVE_INFINITY
		for (i in entries.indices) {
			if (ends[i] > latest) latest = ends[i]
			endsSoFar[i] = latest
		}
	}

	val size: Int
		get() = starts.size

	/** The screen's width over its height. A hit circle [radius] high is `radius / screenAspect` wide in x's units. */
	val screenAspect: Double = config.screenAspect

	fun kind(index: Int): Kind = kinds[index]

	/** The note's index in the chart's notes, which is what the judge reports. */
	fun chartNote(index: Int): Int = chartNotes[index]

	fun startMs(index: Int): Double = starts[index]

	/** A tap or flick ends when it starts. */
	fun endMs(index: Int): Double = ends[index]

	/** The button a tap or slide needs, or null for a flick. */
	fun button(index: Int): SkyButton? = buttons[index]

	/** The hit radius, as a fraction of the screen's height, measured with [screenAspect] (docs/JUDGEMENT.md). */
	fun radius(index: Int): Double = radii[index]

	/** A flick's required direction in degrees (0 = right, 90 = up), or NaN if any will do. NaN for taps and slides. */
	fun directionDegrees(index: Int): Double = directions[index]

	/** Where the note is across the screen at [timeMs], 0 (left) to 1 (right). Only a slide moves. */
	fun x(index: Int, timeMs: Double): Double {
		val path = paths[index] ?: return xs[index]
		return path.x(timing.msToBeat(timeMs))
	}

	/** Where the note is up the screen at [timeMs], 0 (bottom) to 1 (top). Only a slide moves. */
	fun y(index: Int, timeMs: Double): Double {
		val path = paths[index] ?: return ys[index]
		return path.y(timing.msToBeat(timeMs))
	}

	/** Every note before this index ended before [timeMs]. It's [size] if they all did. */
	fun firstEndingFrom(timeMs: Double): Int {
		var low = 0
		var high = endsSoFar.size
		while (low < high) {
			val middle = (low + high) ushr 1
			if (endsSoFar[middle] < timeMs) low = middle + 1 else high = middle
		}
		return low
	}
}
