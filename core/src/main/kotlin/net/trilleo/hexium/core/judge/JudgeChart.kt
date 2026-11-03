package net.trilleo.hexium.core.judge

import java.math.BigInteger
import net.trilleo.hexium.core.chart.Beat
import net.trilleo.hexium.core.chart.ChartLimits
import net.trilleo.hexium.core.chart.Difficulty
import net.trilleo.hexium.core.chart.DifficultyChart
import net.trilleo.hexium.core.chart.Note
import net.trilleo.hexium.core.chart.SkyButton
import net.trilleo.hexium.core.chart.SlidePath
import net.trilleo.hexium.core.chart.TimingMap

/**
 * A chart turned into the flat arrays the judge walks, built once when a chart loads so that
 * playing it doesn't allocate. Every judged object (tap, hold head, sky tap, flick, tick) has an id.
 *
 * Expects a chart that passed the validator.
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
	/** The tick's lane, or -1 for a slide tick. */
	internal val tickLane: IntArray,
	internal val tickSlide: IntArray,
	internal val timing: TimingMap,
	internal val skyObject: Array<IntArray>,
	internal val skyMs: Array<DoubleArray>,
	internal val skyX: Array<DoubleArray>,
	internal val skyY: Array<DoubleArray>,
	internal val skyRadius: Array<DoubleArray>,
	internal val flickObject: IntArray,
	internal val flickMs: DoubleArray,
	internal val flickX: DoubleArray,
	internal val flickY: DoubleArray,
	internal val flickRadius: DoubleArray,
	/** NaN accepts any direction. */
	internal val flickDir: DoubleArray,
	internal val slideStartMs: DoubleArray,
	internal val slideEndMs: DoubleArray,
	internal val slideButton: IntArray,
	internal val slideRadius: DoubleArray,
	internal val slidePath: Array<SlidePath>,
) {
	val laneCount: Int
		get() = pressMs.size

	internal val slideCount: Int
		get() = slideStartMs.size

	private class Press(val ms: Double, val note: Int, val lane: Int)

	private class Tick(val ms: Double, val note: Int, val lane: Int, val slide: Int)

	private class Sky(val ms: Double, val note: Int, val tap: Note.SkyTap)

	private class Flick(val ms: Double, val note: Int, val flick: Note.Flick)

	private class Slide(val startMs: Double, val endMs: Double, val note: Int, val slide: Note.Slide)

	companion object {
		fun compile(chart: DifficultyChart, config: JudgementConfig = JudgementConfig.DEFAULT): JudgeChart {
			val timing = TimingMap(chart.offsetMs, chart.bpm)
			val presses = ArrayList<Press>()
			val ticks = ArrayList<Tick>()
			val skyTaps = ArrayList<Sky>()
			val flicks = ArrayList<Flick>()
			val slides = ArrayList<Slide>()
			for (i in chart.notes.indices) {
				when (val note = chart.notes[i]) {
					is Note.Tap -> presses += Press(timing.beatToMs(note.t), i, note.lane)
					is Note.Hold -> {
						presses += Press(timing.beatToMs(note.t), i, note.lane)
						val count = tickCount(note.t, note.end, config.ticksPerBeat)
						require(presses.size + ticks.size + count <= ChartLimits.MAX_JUDGED_OBJECTS) { "Too many judged objects" }
						for (k in 0 until count.toInt()) {
							ticks += Tick(timing.beatToMs(tickBeat(note.t, note.end, k, count, config.ticksPerBeat)), i, note.lane, -1)
						}
					}
					is Note.SkyTap -> skyTaps += Sky(timing.beatToMs(note.t), i, note)
					is Note.Flick -> flicks += Flick(timing.beatToMs(note.t), i, note)
					is Note.Slide -> slides += Slide(timing.beatToMs(note.points.first().t), timing.beatToMs(note.points.last().t), i, note)
				}
			}
			presses.sortWith(compareBy { it.ms })
			skyTaps.sortWith(compareBy { it.ms })
			flicks.sortWith(compareBy { it.ms })
			slides.sortWith(compareBy { it.startMs })
			for (s in slides.indices) {
				val start = slides[s].slide.points.first().t
				val end = slides[s].slide.points.last().t
				val count = tickCount(start, end, config.ticksPerBeat)
				require(presses.size + ticks.size + skyTaps.size + flicks.size + count <= ChartLimits.MAX_JUDGED_OBJECTS) {
					"Too many judged objects"
				}
				for (k in 0 until count.toInt()) {
					ticks += Tick(timing.beatToMs(tickBeat(start, end, k, count, config.ticksPerBeat)), slides[s].note, -1, s)
				}
			}
			ticks.sortWith(compareBy { it.ms })

			val objectCount = presses.size + skyTaps.size + flicks.size + ticks.size
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
			val buttons = SkyButton.entries
			val skyByButton = Array(buttons.size) { b -> skyTaps.filter { it.tap.button == buttons[b] } }
			val skyObject = Array(buttons.size) { IntArray(skyByButton[it].size) }
			for (b in buttons.indices) {
				for (i in skyByButton[b].indices) {
					skyObject[b][i] = nextObject
					objectNote[nextObject++] = skyByButton[b][i].note
				}
			}
			val flickObject = IntArray(flicks.size)
			for (i in flicks.indices) {
				flickObject[i] = nextObject
				objectNote[nextObject++] = flicks[i].note
			}
			val tickObject = IntArray(ticks.size)
			val tickMs = DoubleArray(ticks.size)
			val tickLane = IntArray(ticks.size)
			val tickSlide = IntArray(ticks.size)
			for (i in ticks.indices) {
				tickObject[i] = nextObject
				tickMs[i] = ticks[i].ms
				tickLane[i] = ticks[i].lane
				tickSlide[i] = ticks[i].slide
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
				tickSlide,
				timing,
				skyObject,
				Array(buttons.size) { b -> DoubleArray(skyByButton[b].size) { skyByButton[b][it].ms } },
				Array(buttons.size) { b -> DoubleArray(skyByButton[b].size) { skyByButton[b][it].tap.x } },
				Array(buttons.size) { b -> DoubleArray(skyByButton[b].size) { skyByButton[b][it].tap.y } },
				Array(buttons.size) { b -> DoubleArray(skyByButton[b].size) { skyByButton[b][it].tap.size * config.baseHitRadius } },
				flickObject,
				DoubleArray(flicks.size) { flicks[it].ms },
				DoubleArray(flicks.size) { flicks[it].flick.x },
				DoubleArray(flicks.size) { flicks[it].flick.y },
				DoubleArray(flicks.size) { flicks[it].flick.size * config.baseHitRadius },
				DoubleArray(flicks.size) { flicks[it].flick.dir ?: Double.NaN },
				DoubleArray(slides.size) { slides[it].startMs },
				DoubleArray(slides.size) { slides[it].endMs },
				IntArray(slides.size) { slides[it].slide.button.ordinal },
				DoubleArray(slides.size) { slides[it].slide.radius },
				Array(slides.size) { SlidePath(slides[it].slide.points) },
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
