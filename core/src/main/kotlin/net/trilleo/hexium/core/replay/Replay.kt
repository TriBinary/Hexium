package net.trilleo.hexium.core.replay

import java.security.MessageDigest

enum class ReplayEvent { AIM, LANE_DOWN, LANE_UP, BUTTON_DOWN, BUTTON_UP }

/** Every input of one play, in order (docs/REPLAY_FORMAT.md). */
class Replay internal constructor(
	val judgeVersion: Int,
	val chartHash: ByteArray,
	val judgementOffsetMs: Double,
	val eventCount: Int,
	private val events: ByteArray,
	private val indexes: ByteArray,
	private val times: DoubleArray,
	private val xs: FloatArray,
	private val ys: FloatArray,
) {
	fun event(i: Int): ReplayEvent = EVENTS[events[i].toInt()]

	/** The lane, or the [net.trilleo.hexium.core.chart.SkyButton] ordinal. */
	fun index(i: Int): Int = indexes[i].toInt()

	fun timeMs(i: Int): Double = times[i]

	/** Only for [ReplayEvent.AIM] and [ReplayEvent.BUTTON_DOWN]. */
	fun x(i: Int): Float = xs[i]

	/** Only for [ReplayEvent.AIM] and [ReplayEvent.BUTTON_DOWN]. */
	fun y(i: Int): Float = ys[i]

	companion object {
		const val MAX_EVENTS: Int = 2_000_000

		internal val EVENTS = ReplayEvent.entries.toTypedArray()

		/** The hash a replay holds for a difficulty file's bytes. */
		fun hashOf(chartFile: ByteArray): ByteArray = MessageDigest.getInstance("SHA-256").digest(chartFile)
	}
}
