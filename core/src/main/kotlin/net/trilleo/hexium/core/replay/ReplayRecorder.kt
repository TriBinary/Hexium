package net.trilleo.hexium.core.replay

import net.trilleo.hexium.core.chart.SkyButton
import net.trilleo.hexium.core.judge.Judge
import net.trilleo.hexium.core.judge.JudgementConfig

/**
 * Sends input to the judge and records it, so a replay holds exactly what the judge saw. An
 * event older than the current time is recorded as happening now, which is how the judge
 * treats it. Events with times or positions that aren't finite are dropped.
 *
 * Doesn't allocate until it passes [expectedEvents], so size that for the whole song. Past
 * [Replay.MAX_EVENTS] it stops recording but keeps judging: check [isFull] before submitting.
 */
class ReplayRecorder(
	val judge: Judge,
	private val chartHash: ByteArray,
	private val judgementOffsetMs: Double,
	expectedEvents: Int,
) {
	private var capacity = expectedEvents.coerceIn(1, Replay.MAX_EVENTS)
	private var events = ByteArray(capacity)
	private var indexes = ByteArray(capacity)
	private var times = DoubleArray(capacity)
	private var xs = FloatArray(capacity)
	private var ys = FloatArray(capacity)
	private var count = 0
	private var latestMs = Double.NEGATIVE_INFINITY

	/** True if events have been lost to the limit. */
	var isFull = false
		private set

	fun pressLane(lane: Int, timeMs: Double) {
		if (!timeMs.isFinite() || lane !in 0..MAX_INDEX) return
		val time = latest(timeMs)
		record(ReplayEvent.LANE_DOWN, lane, time, 0f, 0f)
		judge.pressLane(lane, time)
	}

	fun releaseLane(lane: Int, timeMs: Double) {
		if (!timeMs.isFinite() || lane !in 0..MAX_INDEX) return
		val time = latest(timeMs)
		record(ReplayEvent.LANE_UP, lane, time, 0f, 0f)
		judge.releaseLane(lane, time)
	}

	fun pressButton(button: SkyButton, timeMs: Double, x: Float, y: Float) {
		if (!timeMs.isFinite() || !x.isFinite() || !y.isFinite()) return
		val time = latest(timeMs)
		record(ReplayEvent.BUTTON_DOWN, button.ordinal, time, x, y)
		judge.pressButton(button, time, x, y)
	}

	fun releaseButton(button: SkyButton, timeMs: Double) {
		if (!timeMs.isFinite()) return
		val time = latest(timeMs)
		record(ReplayEvent.BUTTON_UP, button.ordinal, time, 0f, 0f)
		judge.releaseButton(button, time)
	}

	fun aimSample(timeMs: Double, x: Float, y: Float) {
		if (!timeMs.isFinite() || !x.isFinite() || !y.isFinite()) return
		val time = latest(timeMs)
		record(ReplayEvent.AIM, 0, time, x, y)
		judge.aimSample(time, x, y)
	}

	fun advanceTo(timeMs: Double) {
		if (!timeMs.isFinite()) return
		judge.advanceTo(latest(timeMs))
	}

	fun finish() {
		judge.finish()
	}

	fun toReplay(): Replay = Replay(
		JudgementConfig.JUDGE_VERSION,
		chartHash,
		judgementOffsetMs,
		count,
		events.copyOf(count),
		indexes.copyOf(count),
		times.copyOf(count),
		xs.copyOf(count),
		ys.copyOf(count),
	)

	private fun latest(timeMs: Double): Double {
		if (timeMs > latestMs) latestMs = timeMs
		return latestMs
	}

	private fun record(event: ReplayEvent, index: Int, timeMs: Double, x: Float, y: Float) {
		if (count == capacity && !grow()) {
			isFull = true
			return
		}
		events[count] = event.ordinal.toByte()
		indexes[count] = index.toByte()
		times[count] = timeMs
		xs[count] = x
		ys[count] = y
		count++
	}

	private fun grow(): Boolean {
		if (capacity >= Replay.MAX_EVENTS) return false
		capacity = minOf(capacity.toLong() * 2, Replay.MAX_EVENTS.toLong()).toInt()
		events = events.copyOf(capacity)
		indexes = indexes.copyOf(capacity)
		times = times.copyOf(capacity)
		xs = xs.copyOf(capacity)
		ys = ys.copyOf(capacity)
		return true
	}

	private companion object {
		/** An event's index has 4 bits in the file. */
		const val MAX_INDEX = 15
	}
}
