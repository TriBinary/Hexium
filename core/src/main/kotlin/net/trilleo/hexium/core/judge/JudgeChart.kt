package net.trilleo.hexium.core.judge

import java.math.BigInteger
import net.trilleo.hexium.core.chart.Beat
import net.trilleo.hexium.core.chart.ChartLimits
import net.trilleo.hexium.core.chart.Difficulty
import net.trilleo.hexium.core.chart.DifficultyChart
import net.trilleo.hexium.core.chart.Note
import net.trilleo.hexium.core.chart.TimingMap

/**
 * A chart turned into the flat arrays the judge walks, built once when a chart loads so that
 * playing it doesn't allocate. Every judged object (tap, hold head, tick) has an id.
 *
 * Expects a chart that passed the validator. Only ground notes are judged so far.
 */
class JudgeChart private constructor(
	val config: JudgementConfig,
	internal val attackWindowMs: Double,
	val objectCount: Int,
	internal val objectNote: IntArray,
	internal val pressObject: Array<IntArray>,
	internal val pressMs: Array<DoubleArray>,
	internal val tickObject: IntArray,
	internal val tickMs: DoubleArray,
	internal val tickLane: IntArray,
) {
	val laneCount: Int
		get() = pressMs.size

	private class Press(val ms: Double, val note: Int, val lane: Int)

	private class Tick(val ms: Double, val note: Int, val lane: Int)

	companion object {
		fun compile(chart: DifficultyChart, config: JudgementConfig = JudgementConfig.DEFAULT): JudgeChart {
			val timing = TimingMap(chart.offsetMs, chart.bpm)
			val presses = ArrayList<Press>()
			val ticks = ArrayList<Tick>()
			for (i in chart.notes.indices) {
				when (val note = chart.notes[i]) {
					is Note.Tap -> presses += Press(timing.beatToMs(note.t), i, note.lane)
					is Note.Hold -> {
						presses += Press(timing.beatToMs(note.t), i, note.lane)
						val count = tickCount(note.t, note.end, config.ticksPerBeat)
						require(presses.size + ticks.size + count <= ChartLimits.MAX_JUDGED_OBJECTS) { "Too many judged objects" }
						for (k in 0 until count.toInt()) {
							ticks += Tick(timing.beatToMs(tickBeat(note.t, note.end, k, count, config.ticksPerBeat)), i, note.lane)
						}
					}
					is Note.SkyTap, is Note.Slide, is Note.Flick -> {}
				}
			}
			presses.sortWith(compareBy { it.ms })
			ticks.sortWith(compareBy { it.ms })

			val objectCount = presses.size + ticks.size
			val objectNote = IntArray(objectCount)
			var nextObject = 0
			val pressObject = Array(chart.laneCount) { lane -> IntArray(presses.count { it.lane == lane }) }
			val pressMs = Array(chart.laneCount) { DoubleArray(pressObject[it].size) }
			val filled = IntArray(chart.laneCount)
			for (press in presses) {
				val slot = filled[press.lane]++
				pressObject[press.lane][slot] = nextObject
				pressMs[press.lane][slot] = press.ms
				objectNote[nextObject++] = press.note
			}
			val tickObject = IntArray(ticks.size)
			val tickMs = DoubleArray(ticks.size)
			val tickLane = IntArray(ticks.size)
			for (i in ticks.indices) {
				tickObject[i] = nextObject
				tickMs[i] = ticks[i].ms
				tickLane[i] = ticks[i].lane
				objectNote[nextObject++] = ticks[i].note
			}
			val isEasy = chart.difficulty == Difficulty.BASIC || chart.difficulty == Difficulty.ADVANCED
			return JudgeChart(
				config,
				if (isEasy) config.easyAttackMs else config.attackMs,
				objectCount,
				objectNote,
				pressObject,
				pressMs,
				tickObject,
				tickMs,
				tickLane,
			)
		}

		/**
		 * How many ticks a hold or slide from [start] to [end] has: one every 1/[ticksPerBeat] beat,
		 * excluding both ends, or one at the end if none fit. Exact, since a tick that lands on the
		 * end must be left out however the fractions round.
		 */
		internal fun tickCount(start: Beat, end: Beat, ticksPerBeat: Int): Long {
			val startDen = BigInteger.valueOf(start.den.toLong())
			val endDen = BigInteger.valueOf(end.den.toLong())
			val bothDens = startDen * endDen
			val length = BigInteger.valueOf(end.whole.toLong() - start.whole.toLong()) * bothDens +
				BigInteger.valueOf(end.num.toLong()) * startDen -
				BigInteger.valueOf(start.num.toLong()) * endDen
			val steps = length * BigInteger.valueOf(ticksPerBeat.toLong())
			val division = steps.divideAndRemainder(bothDens)
			val between = if (division[1].signum() == 0) division[0] - BigInteger.ONE else division[0]
			return maxOf(1L, between.toLong())
		}

		private fun tickBeat(start: Beat, end: Beat, index: Int, count: Long, ticksPerBeat: Int): Double {
			if (count == 1L && start.toDouble() + 1.0 / ticksPerBeat >= end.toDouble()) return end.toDouble()
			return start.toDouble() + (index + 1).toDouble() / ticksPerBeat
		}
	}
}
