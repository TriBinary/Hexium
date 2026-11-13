package net.trilleo.hexium.core.calibration

import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/** How far off the beat a player tapped. Positive is late. */
class TapEstimate(val errorMs: Double, val spreadMs: Double, val tapsUsed: Int, val tapsGiven: Int, val driftMs: Double) {
	/**
	 * False if [errorMs] doesn't mean much: the taps were too scattered, too many were thrown out, or
	 * they slid steadily off the beat, as they do when the player can't actually hear the clicks.
	 */
	val isSteady: Boolean
		get() = spreadMs <= Calibration.MAX_SPREAD_MS &&
			tapsUsed * Calibration.MIN_KEPT_QUARTERS >= tapsGiven * Calibration.QUARTERS &&
			abs(driftMs) <= Calibration.MAX_DRIFT_MS
}

/**
 * The maths behind the two-step calibration in docs/JUDGEMENT.md. The player taps to clicks they can
 * only hear, which gives offset B, then to notes they can only see, with no offset applied, which gives A.
 */
object Calibration {
	const val MIN_TAPS = 8
	const val MAX_SPREAD_MS = 40.0

	/** How much the second half of the taps may sit from the first. */
	const val MAX_DRIFT_MS = 25.0
	const val QUARTERS = 3
	const val MIN_KEPT_QUARTERS = 4

	/** Nobody taps further ahead of a beat than this share of one, so it must be the beat before, heard late. */
	private const val MOST_EARLY_BEATS = 0.25

	/** A tap this close to the rest is never thrown out, however tight the rest are. */
	private const val MIN_OUTLIER_MS = 50.0
	private const val OUTLIER_SPREADS = 3.0
	private const val TURN = 2.0 * Math.PI

	/**
	 * Reads the first [count] of [tapsMs], song times with a beat every [beatMs] from time 0, or returns null
	 * if too few are usable. A tap could belong to either beat around it, so the taps are first gathered
	 * round where most of them lie, and one stray tap doesn't matter. The result is from a quarter of a beat
	 * early to three quarters late, since late output such as Bluetooth can be a long way behind.
	 */
	fun estimate(tapsMs: DoubleArray, count: Int, beatMs: Double): TapEstimate? {
		if (count < MIN_TAPS) return null
		val centre = centreOfPhases(tapsMs, count, beatMs)
		val errors = DoubleArray(count) { wrap(tapsMs[it] - centre, beatMs) + centre }
		val middle = median(errors, count)
		val limit = maxOf(MIN_OUTLIER_MS, OUTLIER_SPREADS * spread(errors, count, middle))
		var kept = 0
		for (i in 0 until count) {
			if (abs(errors[i] - middle) <= limit) errors[kept++] = errors[i]
		}
		if (kept < MIN_TAPS) return null
		val keptMiddle = median(errors, kept)
		val error = if (keptMiddle < -MOST_EARLY_BEATS * beatMs) keptMiddle + beatMs else keptMiddle
		return TapEstimate(error, spread(errors, kept, keptMiddle), kept, count, drift(errors, kept))
	}

	/** The average of the later half of [values] minus that of the earlier half. */
	private fun drift(values: DoubleArray, count: Int): Double {
		val half = count / 2
		var early = 0.0
		var late = 0.0
		for (i in 0 until half) {
			early += values[i]
			late += values[count - 1 - i]
		}
		return (late - early) / half
	}

	/** Offset B: judgement time is input time minus B, so a player tapping late to sound judges as on time. */
	fun judgementOffsetMs(toSound: TapEstimate): Int = toSound.errorMs.roundToInt()

	/**
	 * Offset A, from taps to notes drawn with no offset. Drawing a note A later moves the player's tap A
	 * later, so A = B minus that error makes their tap judge as on time.
	 */
	fun visualOffsetMs(judgementOffsetMs: Int, toNotes: TapEstimate): Int = (judgementOffsetMs - toNotes.errorMs).roundToInt()

	private fun centreOfPhases(tapsMs: DoubleArray, count: Int, beatMs: Double): Double {
		var x = 0.0
		var y = 0.0
		for (i in 0 until count) {
			val angle = tapsMs[i] / beatMs * TURN
			x += cos(angle)
			y += sin(angle)
		}
		return atan2(y, x) / TURN * beatMs
	}

	/** [ms] moved by whole beats to within half a beat of 0. */
	private fun wrap(ms: Double, beatMs: Double): Double = ms - Math.rint(ms / beatMs) * beatMs

	private fun median(values: DoubleArray, count: Int): Double {
		val sorted = values.copyOf(count)
		sorted.sort()
		val half = count / 2
		return if (count % 2 == 1) sorted[half] else (sorted[half - 1] + sorted[half]) / 2.0
	}

	private fun spread(values: DoubleArray, count: Int, middle: Double): Double =
		median(DoubleArray(count) { abs(values[it] - middle) }, count)
}
