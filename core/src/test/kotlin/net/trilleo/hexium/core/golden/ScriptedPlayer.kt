package net.trilleo.hexium.core.golden

import net.trilleo.hexium.core.chart.ChartParser
import net.trilleo.hexium.core.chart.DifficultyChart
import net.trilleo.hexium.core.chart.Note
import net.trilleo.hexium.core.chart.SkyButton
import net.trilleo.hexium.core.chart.SlidePath
import net.trilleo.hexium.core.chart.TimingMap
import net.trilleo.hexium.core.judge.JudgeChart
import net.trilleo.hexium.core.judge.JudgementConfig
import net.trilleo.hexium.core.replay.Replay
import net.trilleo.hexium.core.replay.ReplayRecorder

/** How a scripted player plays a chart. Plays one is built from are recorded as golden replays. */
internal data class PlayStyle(
	val playGround: Boolean = true,
	val playSky: Boolean = true,
	/** Added to every press that hits a tap, hold head or sky tap. */
	val lateMs: Double = 0.0,
	val framesPerSecond: Double = 120.0,
	/** Input lag: every time is this much later, which offset B then cancels. */
	val judgementOffsetMs: Double = 0.0,
	/** A different extra offset for each press, from its note's index. Used by the property tests. */
	val jitterMs: (Int) -> Double = { 0.0 },
	/** Added to the moment a swipe passes through a flick. */
	val swipeLateMs: Double = 0.0,
)

/**
 * Plays a chart from what it says, without looking at the judge's results, and records it.
 * The crosshair follows slides, swipes through flicks and otherwise rests in the middle.
 */
internal class ScriptedPlayer(private val chartFile: ByteArray) {
	private val chart: DifficultyChart = ChartParser.parseDifficulty(String(chartFile)).value!!
	private val timing = TimingMap(chart.offsetMs, chart.bpm)

	private class Input(val ms: Double, val apply: (ReplayRecorder, Double) -> Unit)

	private class SlideRun(val startMs: Double, val endMs: Double, val path: SlidePath)

	private class Swipe(val ms: Double, val x: Double, val y: Double, val radians: Double)

	fun play(style: PlayStyle): ReplayRecorder {
		val inputs = ArrayList<Input>()
		val slides = ArrayList<SlideRun>()
		val swipes = ArrayList<Swipe>()
		var lastMs = 0.0
		var firstMs = Double.POSITIVE_INFINITY

		for (i in chart.notes.indices) {
			val lag = style.lateMs + style.jitterMs(i)
			when (val note = chart.notes[i]) {
				is Note.Tap -> {
					val at = timing.beatToMs(note.t)
					firstMs = minOf(firstMs, at)
					lastMs = maxOf(lastMs, at)
					if (style.playGround) {
						inputs += Input(at + lag) { r, t -> r.pressLane(note.lane, t) }
						inputs += Input(at + lag + TAP_LENGTH_MS) { r, t -> r.releaseLane(note.lane, t) }
					}
				}
				is Note.Hold -> {
					val at = timing.beatToMs(note.t)
					val end = timing.beatToMs(note.end)
					firstMs = minOf(firstMs, at)
					lastMs = maxOf(lastMs, end)
					if (style.playGround) {
						inputs += Input(at + lag) { r, t -> r.pressLane(note.lane, t) }
						inputs += Input(end - 1.0) { r, t -> r.releaseLane(note.lane, t) }
					}
				}
				is Note.SkyTap -> {
					val at = timing.beatToMs(note.t)
					firstMs = minOf(firstMs, at)
					lastMs = maxOf(lastMs, at)
					if (style.playSky) {
						inputs += Input(at + lag) { r, t -> r.pressButton(note.button, t, note.x.toFloat(), note.y.toFloat()) }
						inputs += Input(at + lag + TAP_LENGTH_MS) { r, t -> r.releaseButton(note.button, t) }
					}
				}
				is Note.Slide -> {
					val start = timing.beatToMs(note.points.first().t)
					val end = timing.beatToMs(note.points.last().t)
					firstMs = minOf(firstMs, start)
					lastMs = maxOf(lastMs, end)
					if (style.playSky) {
						val first = note.points.first()
						inputs += Input(start) { r, t -> r.pressButton(note.button, t, first.x.toFloat(), first.y.toFloat()) }
						inputs += Input(end) { r, t -> r.releaseButton(note.button, t) }
						slides += SlideRun(start, end, SlidePath(note.points))
					}
				}
				is Note.Flick -> {
					val at = timing.beatToMs(note.t)
					firstMs = minOf(firstMs, at)
					lastMs = maxOf(lastMs, at)
					if (style.playSky) swipes += Swipe(at + style.swipeLateMs, note.x, note.y, Math.toRadians(note.dir ?: 0.0))
				}
			}
		}
		inputs.sortBy { it.ms }

		val offset = style.judgementOffsetMs
		val frame = 1000.0 / style.framesPerSecond
		val frames = ((lastMs - firstMs + 2 * MARGIN_MS) / frame).toInt() + 1
		val recorder = ReplayRecorder(JudgeChart.compile(chart), Replay.hashOf(chartFile), offset, frames + inputs.size)
		var next = 0
		for (f in 0 until frames) {
			val frameMs = firstMs - MARGIN_MS + f * frame
			while (next < inputs.size && inputs[next].ms <= frameMs) {
				inputs[next].apply(recorder, inputs[next].ms + offset)
				next++
			}
			recorder.aimSample(frameMs + offset, aimX(frameMs, slides, swipes), aimY(frameMs, slides, swipes))
		}
		while (next < inputs.size) {
			inputs[next].apply(recorder, inputs[next].ms + offset)
			next++
		}
		recorder.finish()
		return recorder
	}

	private fun aimX(ms: Double, slides: List<SlideRun>, swipes: List<Swipe>): Float {
		for (swipe in swipes) {
			if (Math.abs(ms - swipe.ms) <= SWIPE_HALF_MS) {
				return (swipe.x + Math.cos(swipe.radians) * SWIPE_SPEED * (ms - swipe.ms) / JudgementConfig.DEFAULT.screenAspect).toFloat()
			}
		}
		for (slide in slides) {
			if (ms >= slide.startMs && ms <= slide.endMs) return slide.path.x(timing.msToBeat(ms)).toFloat()
		}
		return REST
	}

	private fun aimY(ms: Double, slides: List<SlideRun>, swipes: List<Swipe>): Float {
		for (swipe in swipes) {
			if (Math.abs(ms - swipe.ms) <= SWIPE_HALF_MS) {
				return (swipe.y + Math.sin(swipe.radians) * SWIPE_SPEED * (ms - swipe.ms)).toFloat()
			}
		}
		for (slide in slides) {
			if (ms >= slide.startMs && ms <= slide.endMs) return slide.path.y(timing.msToBeat(ms)).toFloat()
		}
		return REST
	}

	private companion object {
		const val TAP_LENGTH_MS = 30.0
		const val MARGIN_MS = 500.0
		const val REST = 0.5f

		/** In screen heights per millisecond, well above the minimum speed. */
		const val SWIPE_SPEED = 0.006
		const val SWIPE_HALF_MS = 40.0
	}
}
