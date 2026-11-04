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
		assertEquals(1, recorder.toReplay().eventCount)
		assertEquals(Judgement.MISS, recorder.judge.judgement(0))
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
		recorder.finish()
		val judge = recorder.judge
		val again = ReplayJudge.play(chart, ReplayFile.read(ReplayFile.write(recorder.toReplay())).replay!!)
		assertEquals(judge.score, again.score)
		assertEquals(judge.maxCombo, again.maxCombo)
		assertEquals(judge.judgedCount, again.judgedCount)
		for (i in 0 until judge.judgedCount) {
			assertEquals(judge.judgement(i), again.judgement(i))
			assertEquals(judge.judgedDeltaMs(i), again.judgedDeltaMs(i))
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
	fun anEventJustBeforeThePausePointAfterReopeningCountsAsThePoint() {
		pauseAndComeBack(5000.0)
		input.frame(5010.0, 0.5f, 0.5f)
		input.lane(2, true, 5005.0)
		assertEquals(listOf("LANE_DOWN 2 at 5010.0"), laneEvents())
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
}
