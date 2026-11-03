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
		require(bpm.isNotEmpty()) { "BPM list is empty" }
		for (i in bpm.indices) {
			val change = bpm[i]
			require(change.t.isValid) { "BPM entry $i has an invalid time" }
			require(change.bpm > 0.0) { "BPM entry $i is not above 0" }
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
		var i = startBeat.size - 1
		while (i > 0 && beat < startBeat[i]) i--
		return startMs[i] + (beat - startBeat[i]) * msPerBeat[i]
	}

	private companion object {
		const val MS_PER_MINUTE = 60000.0
	}
}
