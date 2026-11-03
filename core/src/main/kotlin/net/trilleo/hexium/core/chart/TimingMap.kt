package net.trilleo.hexium.core.chart

/**
 * Turns beats into song milliseconds using `offsetMs` and the BPM list. Lookups don't
 * allocate, so they're safe in per-frame code.
 *
 * Expects BPM data that passed the validator, and throws [IllegalArgumentException] if it
 * didn't: running a chart without validating it is a programming error.
 */
class TimingMap(offsetMs: Double, bpm: List<BpmChange>) {
	private val startBeat = DoubleArray(bpm.size)
	private val startMs = DoubleArray(bpm.size)
	private val msPerBeat = DoubleArray(bpm.size)

	init {
		require(offsetMs.isFinite()) { "Offset is not finite" }
		require(bpm.isNotEmpty()) { "BPM list is empty" }
		for (i in bpm.indices) {
			val change = bpm[i]
			require(change.t.isValid) { "BPM entry $i has an invalid time" }
			require(isUsableBpm(change.bpm)) { "BPM entry $i is not a finite number above 0 with a finite beat length" }
			startBeat[i] = change.t.toDouble()
			msPerBeat[i] = MS_PER_MINUTE / change.bpm
			if (i == 0) {
				require(startBeat[0] == 0.0) { "First BPM entry is not at beat 0" }
				startMs[0] = offsetMs
			} else {
				require(change.t > bpm[i - 1].t) { "BPM entry $i does not rise in time" }
				startMs[i] = startMs[i - 1] + (startBeat[i] - startBeat[i - 1]) * msPerBeat[i - 1]
			}
		}
	}

	fun beatToMs(beat: Beat): Double = beatToMs(beat.toDouble())

	fun beatToMs(beat: Double): Double {
		// Binary search for the last segment starting at or before this beat.
		var low = 0
		var high = startBeat.size - 1
		while (low < high) {
			val middle = (low + high + 1) ushr 1
			if (startBeat[middle] <= beat) low = middle else high = middle - 1
		}
		return startMs[low] + (beat - startBeat[low]) * msPerBeat[low]
	}

	internal companion object {
		private const val MS_PER_MINUTE = 60000.0

		/** Very small values pass "above 0" but make a beat infinitely long, which turns times into NaN. */
		fun isUsableBpm(bpm: Double): Boolean = bpm > 0.0 && bpm.isFinite() && (MS_PER_MINUTE / bpm).isFinite()
	}
}
