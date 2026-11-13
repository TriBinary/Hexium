package net.trilleo.hexium.core.chart

/**
 * A chart's taps and holds in time order, in song milliseconds, for drawing. Lookups don't allocate, so
 * they're safe in per-frame code, and keep no position of their own, so time can jump either way.
 *
 * Expects a chart that passed the validator.
 */
class GroundTrack(chart: DifficultyChart) {
	private class Entry(val startMs: Double, val endMs: Double, val lane: Int, val chartNote: Int, val isHold: Boolean)

	private val chartNotes: IntArray
	private val lanes: IntArray
	private val starts: DoubleArray
	private val ends: DoubleArray
	private val holds: BooleanArray

	/** The latest end among each note and all before it. It always rises, so it can be searched. */
	private val endsSoFar: DoubleArray

	init {
		val timing = TimingMap(chart.offsetMs, chart.bpm)
		val entries = ArrayList<Entry>()
		for (i in chart.notes.indices) {
			when (val note = chart.notes[i]) {
				is Note.Tap -> {
					val ms = timing.beatToMs(note.t)
					entries += Entry(ms, ms, note.lane, i, false)
				}
				is Note.Hold -> entries += Entry(timing.beatToMs(note.t), timing.beatToMs(note.end), note.lane, i, true)
				else -> {}
			}
		}
		entries.sortWith(compareBy { it.startMs })
		chartNotes = IntArray(entries.size) { entries[it].chartNote }
		lanes = IntArray(entries.size) { entries[it].lane }
		starts = DoubleArray(entries.size) { entries[it].startMs }
		ends = DoubleArray(entries.size) { entries[it].endMs }
		holds = BooleanArray(entries.size) { entries[it].isHold }
		endsSoFar = DoubleArray(entries.size)
		var latest = Double.NEGATIVE_INFINITY
		for (i in entries.indices) {
			if (ends[i] > latest) latest = ends[i]
			endsSoFar[i] = latest
		}
	}

	val size: Int
		get() = starts.size

	fun lane(index: Int): Int = lanes[index]

	/** The note's index in the chart's notes, which is what the judge reports. */
	fun chartNote(index: Int): Int = chartNotes[index]

	fun startMs(index: Int): Double = starts[index]

	/** A tap ends when it starts. */
	fun endMs(index: Int): Double = ends[index]

	fun isHold(index: Int): Boolean = holds[index]

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

	companion object {
		/** Offset A in docs/JUDGEMENT.md: a positive offset draws notes later. */
		fun drawTimeMs(songMs: Double, visualOffsetMs: Double): Double = songMs - visualOffsetMs

		/**
		 * How many blocks a time on a note is up the lane from the judgement line. Negative is past it.
		 * Speed only changes how far away a note appears, never when it arrives.
		 */
		fun distance(noteMs: Double, drawMs: Double, blocksPerSecond: Double): Double =
			(noteMs - drawMs) * blocksPerSecond / 1000.0
	}
}
