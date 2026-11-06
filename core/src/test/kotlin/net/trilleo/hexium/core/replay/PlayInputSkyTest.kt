package net.trilleo.hexium.core.replay

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import net.trilleo.hexium.core.chart.Beat
import net.trilleo.hexium.core.chart.BpmChange
import net.trilleo.hexium.core.chart.Difficulty
import net.trilleo.hexium.core.chart.DifficultyChart
import net.trilleo.hexium.core.chart.MeterChange
import net.trilleo.hexium.core.chart.Note
import net.trilleo.hexium.core.chart.SkyButton
import net.trilleo.hexium.core.chart.SlidePoint
import net.trilleo.hexium.core.chart.Ease
import net.trilleo.hexium.core.judge.JudgeChart
import net.trilleo.hexium.core.judge.Judgement

/** Sky buttons and aim through [PlayInput]: the same hold-back and pause rule as lane keys. */
class PlayInputSkyTest {
	private fun beat(whole: Int) = Beat(whole, 0, 1)

	/** At 120 BPM a beat is 500 ms: an L sky tap at 10 s mid-screen, and a flick at 12.5 s on the right. */
	private val chart = JudgeChart.compile(
		DifficultyChart(
			1, Difficulty.EXPERT, 1, 4, 0.0,
			listOf(BpmChange(beat(0), 120.0)),
			listOf(MeterChange(beat(0), 4, 4)),
			listOf(Note.SkyTap(beat(20), 0.5, 0.5, SkyButton.L, 1.0), Note.Flick(beat(25), 0.8, 0.5, null, 1.0)),
		),
	)
	private val recorder = ReplayRecorder(chart, ByteArray(32), 0.0, 1000)
	private val input = PlayInput(recorder, 4)

	private fun events(): List<String> {
		val replay = recorder.toReplay()
		return (0 until replay.eventCount).map {
			val position = if (replay.event(it) == ReplayEvent.LANE_DOWN || replay.event(it) == ReplayEvent.LANE_UP || replay.event(it) == ReplayEvent.BUTTON_UP) "" else " (${replay.x(it)}, ${replay.y(it)})"
			"${replay.event(it)} ${replay.index(it)} at ${replay.timeMs(it)}$position"
		}
	}

	private fun buttonEvents(): List<String> = events().filter { it.startsWith("BUTTON") }

	private fun pauseAndComeBack(atMs: Double): Double {
		input.pause(atMs)
		return input.resume()
	}

	@Test
	fun aClickGoesStraightToTheJudgeWithItsPosition() {
		input.button(SkyButton.L, true, 10000.0, 0.5f, 0.5f)
		assertEquals(Judgement.JUSTICE_CRITICAL, recorder.judge.judgement(0))
		assertEquals(listOf("BUTTON_DOWN 0 at 10000.0 (0.5, 0.5)"), events())
		assertTrue(input.isButtonDown(SkyButton.L))
		assertFalse(input.isButtonDown(SkyButton.R))
	}

	@Test
	fun aReleaseIsRecordedWithoutAPosition() {
		input.button(SkyButton.R, true, 1000.0, 0.1f, 0.2f)
		input.button(SkyButton.R, false, 1100.0, 0.3f, 0.4f)
		assertEquals(listOf("BUTTON_DOWN 1 at 1000.0 (0.1, 0.2)", "BUTTON_UP 1 at 1100.0"), events())
		assertFalse(input.isButtonDown(SkyButton.R))
	}

	@Test
	fun aClickLandsAmongTheAimSamplesInTimeOrder() {
		input.frame(1000.0, 0.5f, 0.5f)
		input.frame(1006.0, 0.5f, 0.5f)
		input.frame(1012.0, 0.5f, 0.5f)
		input.button(SkyButton.L, true, 1009.0, 0.5f, 0.5f)
		input.finish()
		val replay = recorder.toReplay()
		assertEquals(listOf(1000.0, 1006.0, 1009.0, 1012.0), (0 until replay.eventCount).map { replay.timeMs(it) })
		assertEquals(ReplayEvent.BUTTON_DOWN, replay.event(2))
	}

	@Test
	fun aClickOlderThanTheHoldBackIsMovedUpAndCounted() {
		input.frame(10100.0, 0.5f, 0.5f)
		input.button(SkyButton.L, true, 10040.0, 0.5f, 0.5f)
		assertEquals(listOf("BUTTON_DOWN 0 at 10050.0 (0.5, 0.5)"), buttonEvents())
		assertEquals(1, input.lateKeyCount)
	}

	@Test
	fun nothingFromTheMouseIsJudgedOrRecordedWhilePaused() {
		input.pause(5000.0)
		input.frame(20000.0, 0.5f, 0.5f)
		input.button(SkyButton.L, true, 10000.0, 0.5f, 0.5f)
		assertEquals(0, recorder.toReplay().eventCount)
		assertTrue(input.isButtonDown(SkyButton.L))
	}

	@Test
	fun aButtonReleasedDuringThePauseIsReleasedAtThePausePoint() {
		input.button(SkyButton.L, true, 1000.0, 0.5f, 0.5f)
		pauseAndComeBack(5000.0)
		input.button(SkyButton.L, false, 3000.0, 0.5f, 0.5f)
		input.frame(5001.0, 0.5f, 0.5f)
		assertEquals(listOf("BUTTON_DOWN 0 at 1000.0 (0.5, 0.5)", "BUTTON_UP 0 at 5000.0"), buttonEvents())
	}

	@Test
	fun aButtonStillDownFromTheCountdownIsPressedAtThePausePointWhereTheCrosshairWas() {
		input.frame(9900.0, 0.1f, 0.1f)
		pauseAndComeBack(10000.0)
		input.frame(9990.0, 0.4f, 0.4f)
		input.button(SkyButton.L, true, 9995.0, 0.9f, 0.9f)
		input.frame(10010.0, 0.6f, 0.6f)
		assertEquals(listOf("BUTTON_DOWN 0 at 10000.0 (0.5, 0.5)"), buttonEvents())
		assertEquals(Judgement.JUSTICE_CRITICAL, recorder.judge.judgement(0))
	}

	@Test
	fun aClickDuringTheCountdownIsForgotten() {
		pauseAndComeBack(5000.0)
		input.button(SkyButton.R, true, 3000.0, 0.5f, 0.5f)
		input.button(SkyButton.R, false, 3100.0, 0.5f, 0.5f)
		input.frame(5001.0, 0.5f, 0.5f)
		assertEquals(emptyList(), buttonEvents())
	}

	@Test
	fun aClickPastThePausePointReopensAndKeepsItsOwnTimeAndPosition() {
		pauseAndComeBack(5000.0)
		input.button(SkyButton.L, true, 5200.0, 0.3f, 0.7f)
		assertTrue(input.isOpen)
		assertEquals(listOf("BUTTON_DOWN 0 at 5200.0 (0.3, 0.7)"), buttonEvents())
	}

	@Test
	fun pausingRecordsWhereTheCrosshairWasAtThePausePoint() {
		input.frame(1000.0, 0.2f, 0.3f)
		input.pause(1012.0)
		assertEquals(listOf("AIM 0 at 1000.0 (0.2, 0.3)", "AIM 0 at 1012.0 (0.2, 0.3)"), events())
	}

	@Test
	fun reopeningOnAFrameRecordsTheCrosshairOnItsWayThere() {
		input.frame(1000.0, 0.2f, 0.3f)
		pauseAndComeBack(1012.0)
		input.frame(1004.0, 0.6f, 0.7f)
		input.frame(1020.0, 0.8f, 0.9f)
		// Halfway in time from the last countdown frame to this one, so its movement keeps its real speed.
		val replay = recorder.toReplay()
		assertEquals(3, replay.eventCount)
		assertEquals(1012.0, replay.timeMs(2))
		assertEquals(0.7f, replay.x(2), 1e-6f)
		assertEquals(0.8f, replay.y(2), 1e-6f)
		input.finish()
		assertEquals("AIM 0 at 1020.0 (0.8, 0.9)", events().last())
	}

	@Test
	fun reopeningOnAKeyRecordsWhereTheCrosshairWasLastSeen() {
		input.frame(1000.0, 0.2f, 0.3f)
		pauseAndComeBack(1012.0)
		input.frame(1004.0, 0.6f, 0.7f)
		input.lane(0, true, 1015.0)
		assertEquals(
			listOf("AIM 0 at 1000.0 (0.2, 0.3)", "AIM 0 at 1012.0 (0.2, 0.3)", "AIM 0 at 1012.0 (0.6, 0.7)", "LANE_DOWN 0 at 1015.0"),
			events(),
		)
	}

	@Test
	fun aButtonHeldBeforeAnyAimIsNotPressedAtAMadeUpPosition() {
		input.pause(1000.0)
		input.button(SkyButton.L, true, 900.0, 0.5f, 0.5f)
		input.resume()
		input.lane(0, true, 1001.0)
		assertEquals(emptyList(), buttonEvents())
		assertTrue(input.isButtonDown(SkyButton.L))
		// It reaches the judge once there's a crosshair, at the next pause point.
		input.frame(1010.0, 0.4f, 0.6f)
		input.pause(1020.0)
		input.resume()
		input.frame(1030.0, 0.4f, 0.6f)
		assertEquals(listOf("BUTTON_DOWN 0 at 1020.0 (0.4, 0.6)"), buttonEvents())
	}

	@Test
	fun aPauseBeforeAnyAimRecordsNoPosition() {
		pauseAndComeBack(1000.0)
		input.lane(0, true, 1001.0)
		assertEquals(listOf("LANE_DOWN 0 at 1001.0"), events())
	}

	/** The flick is the chart's second note. */
	private fun flickResult(): Judgement? {
		val judge = recorder.judge
		for (i in 0 until judge.judgedCount) if (judge.judgedNote(i) == 1) return judge.judgement(i)
		return null
	}

	/**
	 * Moves the crosshair from x 0.2 to 0.9 at mid-height, across the flick, while paused. The
	 * countdown's frames are off the grid of the ones before, so none lands on the pause point.
	 */
	private fun moveAcrossTheFlickWhilePaused(input: PlayInput) {
		var frameMs = 12000.0
		while (frameMs < 12460.0) {
			input.frame(frameMs, 0.2f, 0.5f)
			frameMs += 6.0
		}
		input.pause(12460.0)
		input.resume()
		frameMs = 9463.0
		while (frameMs < 12460.0) {
			input.frame(frameMs, 0.9f, 0.5f)
			frameMs += 6.0
		}
		while (frameMs < 13000.0) {
			input.frame(frameMs, 0.9f, 0.5f)
			frameMs += 6.0
		}
		input.finish()
	}

	@Test
	fun movingTheMouseDuringAPauseDoesNotHitAFlick() {
		moveAcrossTheFlickWhilePaused(input)
		assertEquals(Judgement.MISS, flickResult())
		assertReJudgesTheSame()
	}

	@Test
	fun movingTheMouseDuringAPauseDoesNotHitAFlickWithAJudgementOffset() {
		val offsetRecorder = ReplayRecorder(chart, ByteArray(32), 37.5, 1000)
		moveAcrossTheFlickWhilePaused(PlayInput(offsetRecorder, 4))
		val judge = offsetRecorder.judge
		assertEquals(Judgement.MISS, (0 until judge.judgedCount).first { judge.judgedNote(it) == 1 }.let(judge::judgement))
	}

	@Test
	fun aSwipeAfterReopeningStillHitsAFlick() {
		var frameMs = 12000.0
		while (frameMs < 12400.0) {
			input.frame(frameMs, 0.2f, 0.5f)
			frameMs += 6.0
		}
		pauseAndComeBack(12400.0)
		frameMs = 9403.0
		while (frameMs < 12460.0) {
			input.frame(frameMs, 0.2f, 0.5f)
			frameMs += 6.0
		}
		var x = 0.2f
		while (frameMs < 13000.0) {
			input.frame(frameMs, x, 0.5f)
			x = minOf(x + 0.02f, 1f)
			frameMs += 6.0
		}
		input.finish()
		assertEquals(Judgement.JUSTICE, flickResult())
		assertReJudgesTheSame()
	}

	@Test
	fun aButtonLetGoWhilePausedDoesNotHoldASlideAtThePausePoint() {
		// An L slide sitting mid-screen from 10 s to 12 s, with a tick every 125 ms from 10125.
		val slideChart = JudgeChart.compile(
			DifficultyChart(
				1, Difficulty.EXPERT, 1, 4, 0.0,
				listOf(BpmChange(beat(0), 120.0)),
				listOf(MeterChange(beat(0), 4, 4)),
				listOf(Note.Slide(SkyButton.L, 0.06, listOf(SlidePoint(beat(20), 0.5, 0.5, Ease.LINEAR), SlidePoint(beat(24), 0.5, 0.5, Ease.LINEAR)))),
			),
		)
		val slideRecorder = ReplayRecorder(slideChart, ByteArray(32), 0.0, 2000)
		val play = PlayInput(slideRecorder, 4)
		play.button(SkyButton.L, true, 9000.0, 0.5f, 0.5f)
		var frameMs = 9000.0
		while (frameMs < 10100.0) {
			// On the path until 10 s, then off it.
			play.frame(frameMs, if (frameMs < 10000.0) 0.5f else 0.1f, 0.5f)
			frameMs += 6.0
		}
		play.pause(10100.0)
		play.button(SkyButton.L, false, 8000.0, 0.5f, 0.5f)
		play.resume()
		frameMs = 7103.0
		while (frameMs < 10300.0) {
			play.frame(frameMs, 0.5f, 0.5f)
			frameMs += 6.0
		}
		play.finish()
		val judge = slideRecorder.judge
		// Last held at 9996, so the tick at 10125 is 129 ms on.
		assertEquals(Judgement.ATTACK, judge.judgement(0))
	}

	@Test
	fun aPausedClickAndSwipeReJudgeTheSame() {
		input.frame(9900.0, 0.5f, 0.5f)
		input.button(SkyButton.L, true, 9990.0, 0.5f, 0.5f)
		pauseAndComeBack(10010.0)
		input.button(SkyButton.L, false, 8000.0, 0.5f, 0.5f)
		input.frame(10020.0, 0.4f, 0.5f)
		input.button(SkyButton.R, true, 10030.0, 0.4f, 0.5f)
		input.frame(10040.0, 0.4f, 0.5f)
		assertReJudgesTheSame()
	}

	private fun assertReJudgesTheSame() {
		input.finish()
		val replayed = ReplayJudge.play(chart, ReplayFile.read(ReplayFile.write(recorder.toReplay())).replay!!)
		val live = recorder.judge
		assertEquals(live.score, replayed.score)
		assertEquals(live.judgedCount, replayed.judgedCount)
		for (i in 0 until live.judgedCount) {
			assertEquals(live.judgement(i), replayed.judgement(i))
			assertEquals(live.judgedDeltaMs(i), replayed.judgedDeltaMs(i))
		}
	}
}
