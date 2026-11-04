package net.trilleo.hexium.core.replay

import java.lang.management.ManagementFactory
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
import net.trilleo.hexium.core.judge.Judge
import net.trilleo.hexium.core.judge.JudgeChart
import net.trilleo.hexium.core.judge.Judgement

class PlayInputTest {
	private fun beat(whole: Int) = Beat(whole, 0, 1)

	/** At 120 BPM a beat is 500 ms, so beat 20 is at 10 s. */
	private val chart = JudgeChart.compile(
		DifficultyChart(
			1, Difficulty.EXPERT, 1, 4, 0.0,
			listOf(BpmChange(beat(0), 120.0)),
			listOf(MeterChange(beat(0), 4, 4)),
			listOf(Note.Tap(beat(20), 0), Note.Hold(beat(20), beat(24), 1), Note.Tap(beat(30), 2)),
		),
	)
	private val recorder = ReplayRecorder(chart, ByteArray(32), 0.0, 100)
	private val input = PlayInput(recorder, 4)

	private fun laneEvents(): List<String> {
		val replay = recorder.toReplay()
		return (0 until replay.eventCount).filter { replay.event(it) != ReplayEvent.AIM }
			.map { "${replay.event(it)} ${replay.index(it)} at ${replay.timeMs(it)}" }
	}

	private fun pauseAndComeBack(atMs: Double): Double {
		input.pause(atMs)
		return input.resume()
	}

	@Test
	fun beforeAnyPauseInputGoesStraightToTheJudge() {
		assertTrue(input.isOpen)
		input.lane(0, true, 10000.0)
		assertEquals(Judgement.JUSTICE_CRITICAL, recorder.judge.judgement(0))
		assertEquals(listOf("LANE_DOWN 0 at 10000.0"), laneEvents())
		assertTrue(input.isLaneDown(0))
	}

	@Test
	fun aFrameSendsAnAimSampleAndMovesTheJudgeOn() {
		input.frame(20000.0, 0.5f, 0.5f)
		assertEquals(Judgement.MISS, recorder.judge.judgement(0))
		assertEquals(0, recorder.toReplay().eventCount)
		input.frame(20000.0 + PlayInput.HOLD_BACK_MS, 0.5f, 0.5f)
		assertEquals(1, recorder.toReplay().eventCount)
	}

	@Test
	fun nothingIsJudgedOrRecordedWhilePaused() {
		input.pause(5000.0)
		assertFalse(input.isOpen)
		input.frame(20000.0, 0.5f, 0.5f)
		input.lane(0, true, 10000.0)
		assertEquals(0, recorder.toReplay().eventCount)
		assertEquals(0, recorder.judge.judgedCount)
		assertTrue(input.isLaneDown(0))
	}

	@Test
	fun resumingRewindsThreeSecondsFromThePausePoint() {
		assertEquals(2000.0, pauseAndComeBack(5000.0))
		assertEquals(5000.0, input.playedUntilMs)
	}

	@Test
	fun theRewindCanGoBeforeTheSong() {
		assertEquals(-3500.0, pauseAndComeBack(-500.0))
	}

	@Test
	fun nothingIsJudgedOrRecordedUntilThePausePointComesBack() {
		pauseAndComeBack(5000.0)
		input.frame(4999.0, 0.5f, 0.5f)
		assertFalse(input.isOpen)
		assertEquals(0, recorder.toReplay().eventCount)
		input.frame(5000.0, 0.5f, 0.5f)
		assertTrue(input.isOpen)
		input.frame(5000.0 + PlayInput.HOLD_BACK_MS, 0.5f, 0.5f)
		assertEquals(1, recorder.toReplay().eventCount)
	}

	@Test
	fun aKeyReleasedDuringThePauseIsReleasedAtThePausePoint() {
		input.lane(0, true, 1000.0)
		pauseAndComeBack(5000.0)
		input.lane(0, false, 3000.0)
		input.frame(5001.0, 0.5f, 0.5f)
		assertEquals(listOf("LANE_DOWN 0 at 1000.0", "LANE_UP 0 at 5000.0"), laneEvents())
	}

	@Test
	fun aKeyStillDownFromTheCountdownIsPressedAtThePausePoint() {
		input.pause(10000.0)
		input.lane(0, true, 10000.0)
		input.resume()
		input.frame(10001.0, 0.5f, 0.5f)
		assertEquals(listOf("LANE_DOWN 0 at 10000.0"), laneEvents())
		assertEquals(Judgement.JUSTICE_CRITICAL, recorder.judge.judgement(0))
	}

	@Test
	fun aTapDuringTheCountdownIsForgotten() {
		pauseAndComeBack(5000.0)
		input.lane(0, true, 3000.0)
		input.lane(0, false, 3100.0)
		input.frame(5001.0, 0.5f, 0.5f)
		assertEquals(emptyList(), laneEvents())
		assertFalse(input.isLaneDown(0))
	}

	@Test
	fun aKeyLetGoAndPressedAgainBeforeThePausePointNeverLeftTheJudgesView() {
		input.lane(1, true, 9990.0)
		pauseAndComeBack(10100.0)
		input.lane(1, false, 8000.0)
		input.lane(1, true, 8100.0)
		input.frame(10500.0, 0.5f, 0.5f)
		assertEquals(listOf("LANE_DOWN 1 at 9990.0"), laneEvents())
		assertTrue(recorder.judge.combo > 0)
	}

	@Test
	fun anEventPastThePausePointSyncsTheKeysFirstAndKeepsItsOwnTime() {
		input.lane(0, true, 1000.0)
		pauseAndComeBack(5000.0)
		input.lane(0, false, 3000.0)
		input.lane(2, true, 5200.0)
		assertTrue(input.isOpen)
		assertEquals(listOf("LANE_DOWN 0 at 1000.0", "LANE_UP 0 at 5000.0", "LANE_DOWN 2 at 5200.0"), laneEvents())
	}

	@Test
	fun pausingAgainDuringTheCountdownKeepsTheFirstPausePoint() {
		pauseAndComeBack(5000.0)
		input.frame(3000.0, 0.5f, 0.5f)
		input.pause(3000.0)
		assertEquals(2000.0, input.resume())
		assertEquals(5000.0, input.playedUntilMs)
	}

	@Test
	fun pausingAgainAfterTheCountdownMovesThePausePoint() {
		pauseAndComeBack(5000.0)
		input.frame(6000.0, 0.5f, 0.5f)
		assertEquals(4000.0, pauseAndComeBack(7000.0))
	}

	@Test
	fun aPausedPlayReJudgesToTheSameResult() {
		input.lane(0, true, 10010.0)
		input.lane(0, false, 10050.0)
		input.lane(1, true, 10020.0)
		input.frame(10500.0, 0.5f, 0.5f)
		pauseAndComeBack(10600.0)
		input.lane(1, false, 8000.0)
		input.frame(9000.0, 0.5f, 0.5f)
		input.frame(10600.0, 0.5f, 0.5f)
		input.lane(1, true, 10700.0)
		input.frame(15500.0, 0.5f, 0.5f)
		assertReJudgesTheSame()
	}

	/** A re-judge always runs to the end of the chart, so the live play must have too. */
	private fun assertReJudgesTheSame() {
		input.finish()
		assertSameResults(recorder.judge, ReplayJudge.play(chart, ReplayFile.read(ReplayFile.write(recorder.toReplay())).replay!!))
	}

	private fun assertSameResults(expected: Judge, actual: Judge) {
		assertEquals(expected.score, actual.score)
		assertEquals(expected.maxCombo, actual.maxCombo)
		assertEquals(expected.judgedCount, actual.judgedCount)
		for (i in 0 until expected.judgedCount) {
			assertEquals(expected.judgement(i), actual.judgement(i))
			assertEquals(expected.judgedDeltaMs(i), actual.judgedDeltaMs(i))
		}
	}

	@Test
	fun anEventExactlyOnThePausePointReopensAndCounts() {
		pauseAndComeBack(5000.0)
		input.lane(2, true, 5000.0)
		assertTrue(input.isOpen)
		assertEquals(listOf("LANE_DOWN 2 at 5000.0"), laneEvents())
	}

	@Test
	fun anEventPastThePausePointKeepsItsTimeEvenIfAFrameHasPassedIt() {
		pauseAndComeBack(5000.0)
		input.frame(5010.0, 0.5f, 0.5f)
		input.lane(2, true, 5005.0)
		assertEquals(listOf("LANE_DOWN 2 at 5005.0"), laneEvents())
		assertEquals(0, input.lateKeyCount)
	}

	@Test
	fun aStampFromBeforeThePausePointCountsAsThePoint() {
		pauseAndComeBack(5000.0)
		input.frame(5010.0, 0.5f, 0.5f)
		input.lane(2, true, 4990.0)
		assertEquals(listOf("LANE_DOWN 2 at 5000.0"), laneEvents())
		assertEquals(1, input.lateKeyCount)
	}

	@Test
	fun aPauseDuringTheLeadInReopensBeforeTheSong() {
		pauseAndComeBack(-1000.0)
		input.lane(0, true, -2000.0)
		input.frame(-1000.0, 0.5f, 0.5f)
		assertTrue(input.isOpen)
		assertEquals(listOf("LANE_DOWN 0 at -1000.0"), laneEvents())
		assertReJudgesTheSame()
	}

	@Test
	fun severalKeysSyncTogetherAcrossTwoPauses() {
		input.lane(0, true, 1000.0)
		input.lane(1, true, 1000.0)
		pauseAndComeBack(5000.0)
		input.lane(0, false, 3000.0)
		input.lane(2, true, 3100.0)
		input.frame(3500.0, 0.5f, 0.5f)
		input.pause(3500.0)
		input.lane(1, false, 3500.0)
		input.resume()
		input.frame(5000.0, 0.5f, 0.5f)
		assertEquals(
			listOf("LANE_DOWN 0 at 1000.0", "LANE_DOWN 1 at 1000.0", "LANE_UP 0 at 5000.0", "LANE_UP 1 at 5000.0", "LANE_DOWN 2 at 5000.0"),
			laneEvents(),
		)
	}

	@Test
	fun aHoldLetGoDuringThePauseIsTreatedAsLetGoAtThePausePoint() {
		input.lane(1, true, 10000.0)
		input.frame(10300.0, 0.5f, 0.5f)
		pauseAndComeBack(10300.0)
		input.lane(1, false, 8000.0)
		input.frame(11500.0, 0.5f, 0.5f)
		assertEquals(listOf("LANE_DOWN 1 at 10000.0", "LANE_UP 1 at 10300.0"), laneEvents())
		assertTrue(recorder.judge.count(Judgement.MISS) > 0)
		assertReJudgesTheSame()
	}

	private fun allEventTimes(): List<Double> {
		val replay = recorder.toReplay()
		return (0 until replay.eventCount).map { replay.timeMs(it) }
	}

	@Test
	fun aKeyOlderThanTheLastFrameKeepsItsOwnTime() {
		input.frame(10000.0, 0.5f, 0.5f)
		input.frame(10006.0, 0.5f, 0.5f)
		input.lane(0, true, 10003.0)
		assertEquals(listOf("LANE_DOWN 0 at 10003.0"), laneEvents())
		assertEquals(3.0, recorder.judge.judgedDeltaMs(0))
		assertEquals(0, input.lateKeyCount)
	}

	@Test
	fun aKeyIsJudgedStraightAwayEvenThoughSamplesAreHeldBack() {
		input.frame(10000.0, 0.5f, 0.5f)
		input.lane(0, true, 10000.0)
		assertEquals(Judgement.JUSTICE_CRITICAL, recorder.judge.judgement(0))
	}

	@Test
	fun aKeyLandsAmongTheAimSamplesInTimeOrder() {
		input.frame(1000.0, 0.5f, 0.5f)
		input.frame(1006.0, 0.5f, 0.5f)
		input.frame(1012.0, 0.5f, 0.5f)
		input.lane(3, true, 1009.0)
		input.finish()
		assertEquals(listOf(1000.0, 1006.0, 1009.0, 1012.0), allEventTimes())
		assertEquals(listOf("LANE_DOWN 3 at 1009.0"), laneEvents())
	}

	@Test
	fun aKeyArrivingLateNearTheEndOfItsWindowIsNotMissed() {
		input.frame(10120.0, 0.5f, 0.5f)
		input.lane(0, true, 10080.0)
		assertEquals(Judgement.ATTACK, recorder.judge.judgement(0))
		assertEquals(80.0, recorder.judge.judgedDeltaMs(0))
	}

	@Test
	fun aKeyExactlyAtTheHoldBackKeepsItsTime() {
		input.frame(10100.0, 0.5f, 0.5f)
		input.lane(0, true, 10100.0 - PlayInput.HOLD_BACK_MS)
		assertEquals(listOf("LANE_DOWN 0 at 10050.0"), laneEvents())
		assertEquals(0, input.lateKeyCount)
	}

	@Test
	fun aKeyOlderThanTheHoldBackIsMovedUpAndCounted() {
		input.frame(10100.0, 0.5f, 0.5f)
		input.lane(0, true, 10040.0)
		assertEquals(listOf("LANE_DOWN 0 at 10050.0"), laneEvents())
		assertEquals(1, input.lateKeyCount)
	}

	@Test
	fun keysSyncedAfterAPauseAreNotCountedAsLate() {
		input.lane(0, true, 1000.0)
		pauseAndComeBack(5000.0)
		input.lane(0, false, 3000.0)
		input.frame(5001.0, 0.5f, 0.5f)
		assertEquals(0, input.lateKeyCount)
	}

	@Test
	fun pausingSendsEverySampleStillWaiting() {
		input.frame(1000.0, 0.5f, 0.5f)
		input.frame(1010.0, 0.5f, 0.5f)
		assertEquals(0, recorder.toReplay().eventCount)
		input.pause(1012.0)
		assertEquals(listOf(1000.0, 1010.0), allEventTimes())
	}

	@Test
	fun pausingJudgesEverythingDueBeforeThePausePoint() {
		input.frame(10080.0, 0.5f, 0.5f)
		assertEquals(0, recorder.judge.judgedCount)
		input.pause(10100.0)
		assertEquals(2, recorder.judge.count(Judgement.MISS))
	}

	@Test
	fun finishingSendsEverySampleStillWaiting() {
		input.frame(1000.0, 0.5f, 0.5f)
		input.finish()
		assertEquals(listOf(1000.0), allEventTimes())
	}

	@Test
	fun moreSamplesThanTheHoldBackHasRoomForAreAllKeptInOrder() {
		val frames = 1000
		for (i in 0 until frames) input.frame(1000.0 + i * 0.01, 0.5f, 0.5f)
		input.finish()
		assertEquals(List(frames) { 1000.0 + it * 0.01 }, allEventTimes())
	}

	@Test
	fun aTimeThatIsNotFiniteIsIgnoredAndBlocksNothing() {
		input.frame(Double.NaN, 0.5f, 0.5f)
		input.frame(1000.0, 0.5f, 0.5f)
		input.finish()
		assertEquals(listOf(1000.0), allEventTimes())
	}

	/** Keys reach the frame after they happened, as on a real machine, and still give what perfect delivery would. */
	@Test
	fun keysArrivingAFrameLateGiveTheSameResultAsInputInTimeOrder() {
		// The release lands just before a hold tick at 11875, where a moved time would change that tick's result.
		val keys = listOf(Triple(0, true, 10003.0), Triple(1, true, 10011.0), Triple(0, false, 10049.0), Triple(1, false, 11874.0), Triple(2, true, 14995.0))
		for (frameLength in doubleArrayOf(6.0, 16.7, 33.3)) {
			val inOrder = ReplayRecorder(chart, ByteArray(32), 0.0, 100)
			var next = 0
			var frameMs = 9900.0
			while (frameMs < 15500.0) {
				while (next < keys.size && keys[next].third <= frameMs) {
					val (lane, down, time) = keys[next++]
					if (down) inOrder.pressLane(lane, time) else inOrder.releaseLane(lane, time)
				}
				inOrder.aimSample(frameMs, 0.5f, 0.5f)
				frameMs += frameLength
			}
			inOrder.finish()

			val liveRecorder = ReplayRecorder(chart, ByteArray(32), 0.0, 100)
			val live = PlayInput(liveRecorder, 4)
			next = 0
			frameMs = 9900.0
			while (frameMs < 15500.0) {
				// A key stamped in the frame before this one is only seen now, after that frame's sample.
				while (next < keys.size && keys[next].third <= frameMs - frameLength) {
					val (lane, down, time) = keys[next++]
					live.lane(lane, down, time)
				}
				live.frame(frameMs, 0.5f, 0.5f)
				frameMs += frameLength
			}
			live.finish()

			assertEquals(0, live.lateKeyCount)
			assertSameResults(inOrder.judge, liveRecorder.judge)
			assertSameResults(liveRecorder.judge, ReplayJudge.play(chart, ReplayFile.read(ReplayFile.write(liveRecorder.toReplay())).replay!!))
		}
	}

	@Test
	fun samplesKeepTheirOwnTimesAndPositions() {
		input.frame(1000.0, 0.1f, 0.2f)
		input.frame(1006.0, 0.3f, 0.4f)
		input.frame(1060.0, 0.5f, 0.6f)
		val replay = recorder.toReplay()
		assertEquals(2, replay.eventCount)
		assertEquals(listOf(1000.0, 1006.0), allEventTimes())
		assertEquals(listOf(0.1f, 0.3f), listOf(replay.x(0), replay.x(1)))
		assertEquals(listOf(0.2f, 0.4f), listOf(replay.y(0), replay.y(1)))
	}

	@Test
	fun aMovedUpKeyReJudgesTheSame() {
		input.frame(10100.0, 0.5f, 0.5f)
		input.lane(0, true, 10040.0)
		assertEquals(1, input.lateKeyCount)
		assertReJudgesTheSame()
	}

	/** A swipe across a flick, with a key arriving late, must re-judge exactly and hit the flick at its crossing. */
	@Test
	fun aMovingAimAndALateKeyReJudgeTheSame() {
		val skyChart = JudgeChart.compile(
			DifficultyChart(
				1, Difficulty.EXPERT, 1, 4, 0.0,
				listOf(BpmChange(beat(0), 120.0)),
				listOf(MeterChange(beat(0), 4, 4)),
				listOf(Note.Tap(beat(20), 0), Note.Flick(beat(20), 0.5, 0.5, null, 1.0)),
			),
		)
		val liveRecorder = ReplayRecorder(skyChart, ByteArray(32), 0.0, 100)
		val live = PlayInput(liveRecorder, 4)
		var frameMs = 9700.0
		var pressed = false
		while (frameMs < 10500.0) {
			if (!pressed && frameMs >= 10010.0) {
				live.lane(0, true, frameMs - 8.0)
				pressed = true
			}
			live.frame(frameMs, (0.1 + (frameMs - 9700.0) / 750.0).toFloat(), 0.5f)
			frameMs += 6.0
		}
		live.finish()
		assertEquals(0, liveRecorder.judge.count(Judgement.MISS))
		assertSameResults(liveRecorder.judge, ReplayJudge.play(skyChart, ReplayFile.read(ReplayFile.write(liveRecorder.toReplay())).replay!!))
	}

	@Test
	fun playingDoesNotAllocate() {
		val threads = ManagementFactory.getThreadMXBean() as com.sun.management.ThreadMXBean
		val bigRecorder = ReplayRecorder(chart, ByteArray(32), 0.0, 500_000)
		val play = PlayInput(bigRecorder, 4)
		val thread = Thread.currentThread().threadId()
		val before = threads.getThreadAllocatedBytes(thread)
		var i = 0
		while (i < 200_000) {
			val time = i * 2.0
			if (i % 50 == 0) play.lane(i % 4, i % 100 == 0, time - 3.0)
			play.frame(time, 0.5f, 0.5f)
			if (i % 10_000 == 9_999) {
				play.pause(time)
				play.resume()
				play.frame(time, 0.5f, 0.5f)
			}
			i++
		}
		play.finish()
		val allocated = threads.getThreadAllocatedBytes(thread) - before
		assertTrue(allocated < 64 * 1024, "allocated $allocated bytes")
	}
}
