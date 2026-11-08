package net.trilleo.hexium.core.replay

import java.lang.management.ManagementFactory
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import net.trilleo.hexium.core.chart.ChartParser
import net.trilleo.hexium.core.chart.ChartWriter
import net.trilleo.hexium.core.chart.DifficultyChart
import net.trilleo.hexium.core.chart.Ease
import net.trilleo.hexium.core.chart.Note
import net.trilleo.hexium.core.chart.SkyButton
import net.trilleo.hexium.core.chart.SlidePoint
import net.trilleo.hexium.core.judge.ClearLamp
import net.trilleo.hexium.core.judge.Judge
import net.trilleo.hexium.core.judge.JudgeChart
import net.trilleo.hexium.core.judge.Judgement
import net.trilleo.hexium.core.judge.JudgementConfig
import net.trilleo.hexium.core.judge.beat
import net.trilleo.hexium.core.judge.chartOf

class ReplayPlayTest {
	private val chart: DifficultyChart = chartOf(
		Note.Tap(beat(1), 0),
		Note.Hold(beat(2), beat(3), 1),
		Note.SkyTap(beat(3), 0.25, 0.5, SkyButton.R, 1.0),
		Note.Slide(
			SkyButton.L,
			0.1,
			listOf(SlidePoint(beat(4), 0.2, 0.5, Ease.IN_OUT), SlidePoint(beat(5), 0.8, 0.5, Ease.LINEAR)),
		),
		Note.Flick(beat(6), 0.5, 0.5, 1.0),
		Note.Tap(beat(7), 3),
	)
	private val chartFile = ChartWriter.writeDifficulty(chart).toByteArray()

	private fun results(judge: Judge) = List(judge.judgedCount) {
		Triple(judge.judgedNote(it), judge.judgement(it), judge.judgedDeltaMs(it))
	}

	/** Plays the chart at a frame rate, a little off perfect, with [offsetMs] of input lag. */
	private fun play(recorder: ReplayRecorder, offsetMs: Double, stepMs: Double) {
		fun aim(ms: Double): Float = when {
			ms < 4000.0 -> 0.2f
			ms < 5000.0 -> {
				val t = (ms - 4000.0) / 1000.0
				(0.2 + 0.6 * t * t * (3 - 2 * t)).toFloat()
			}
			else -> (0.1 + (ms - 5900.0) * 0.004).toFloat()
		}
		val inputs = sortedMapOf(
			1010.0 to 0, 1050.0 to 1, 2000.0 to 2, 3000.0 to 3, 3050.0 to 4,
			3990.0 to 5, 5000.0 to 6, 7070.0 to 7,
		)
		var time = 900.0
		while (time < 7400.0) {
			val raw = time + offsetMs
			for ((inputMs, id) in inputs.subMap(time, time + stepMs)) {
				val at = inputMs + offsetMs
				when (id) {
					0 -> recorder.pressLane(0, at)
					1 -> recorder.releaseLane(0, at)
					2 -> recorder.pressLane(1, at)
					3 -> {
						recorder.releaseLane(1, at)
						recorder.pressButton(SkyButton.R, at, 0.25f, 0.5f)
					}
					4 -> recorder.releaseButton(SkyButton.R, at)
					5 -> recorder.pressButton(SkyButton.L, at, 0.2f, 0.5f)
					6 -> recorder.releaseButton(SkyButton.L, at)
					7 -> recorder.pressLane(3, at)
				}
			}
			recorder.aimSample(raw, aim(time), 0.5f)
			recorder.advanceTo(raw)
			time += stepMs
		}
		recorder.finish()
	}

	private fun recorded(offsetMs: Double = 0.0, stepMs: Double = 1000.0 / 240): ReplayRecorder {
		val recorder = ReplayRecorder(JudgeChart.compile(chart), Replay.hashOf(chartFile), offsetMs, 2_000)
		play(recorder, offsetMs, stepMs)
		return recorder
	}

	@Test
	fun theScriptedPlayIsAFairTest() {
		val judge = recorded().judge
		assertEquals(judge.objectCount, judge.judgedCount)
		assertEquals(0, judge.count(Judgement.MISS), results(judge).toString())
		assertEquals(1, judge.count(Judgement.ATTACK), "the last tap is 70 ms late")
		assertEquals(ClearLamp.FULL_COMBO, judge.clearLamp)
	}

	@Test
	fun rePlayingGivesExactlyTheSameResults() {
		val recorder = recorded()
		val replayed = ReplayJudge.play(JudgeChart.compile(chart), recorder.toReplay())
		assertEquals(results(recorder.judge), results(replayed))
		assertEquals(recorder.judge.score, replayed.score)
		assertEquals(recorder.judge.maxCombo, replayed.maxCombo)
		assertEquals(recorder.judge.clearLamp, replayed.clearLamp)
	}

	@Test
	fun aReplayThroughTheFileGivesExactlyTheSameResults() {
		val recorder = recorded(offsetMs = 17.25)
		val read = assertNotNull(ReplayFile.read(ReplayFile.write(recorder.toReplay())).replay)
		val replayed = ReplayJudge.play(JudgeChart.compile(chart), read)
		assertEquals(results(recorder.judge), results(replayed))
	}

	@Test
	fun offsetBShiftsEveryInputAndLeavesTheResultAlone() {
		val plain = recorded().judge
		val shifted = recorded(offsetMs = 40.0).judge
		assertEquals(plain.score, shifted.score)
		assertEquals(plain.judgements(), shifted.judgements())
		val replayed = ReplayJudge.play(JudgeChart.compile(chart), recorded(offsetMs = 40.0).toReplay())
		assertEquals(plain.score, replayed.score)
	}

	private fun Judge.judgements() = List(judgedCount) { judgement(it) }

	@Test
	fun offsetBAppliesToTapsDirectly() {
		val judge = Judge(JudgeChart.compile(chartOf(Note.Tap(beat(1), 0))), 30.0)
		judge.pressLane(0, 1030.0)
		assertEquals(0.0, judge.judgedDeltaMs(0))
		val expiring = Judge(JudgeChart.compile(chartOf(Note.Tap(beat(1), 0))), 30.0)
		expiring.advanceTo(1113.0)
		assertEquals(0, expiring.judgedCount)
		expiring.advanceTo(1114.0)
		assertEquals(1, expiring.judgedCount)
	}

	@Test
	fun recorderKeepsHeaderFields() {
		val replay = recorded(offsetMs = 5.0).toReplay()
		assertEquals(JudgementConfig.JUDGE_VERSION, replay.judgeVersion)
		assertContentEquals(Replay.hashOf(chartFile), replay.chartHash)
		assertEquals(5.0, replay.judgementOffsetMs)
		assertTrue(replay.eventCount > 1000)
	}

	@Test
	fun anEventOlderThanTheCurrentTimeIsRecordedAsNow() {
		val chart = chartOf(Note.Tap(beat(1), 0))
		val recorder = ReplayRecorder(JudgeChart.compile(chart), Replay.hashOf(ByteArray(0)), 0.0, 4)
		recorder.advanceTo(1020.0)
		recorder.pressLane(0, 1000.0)
		recorder.aimSample(900.0, 0f, 0f)
		val replay = recorder.toReplay()
		assertEquals(1020.0, replay.timeMs(0))
		assertEquals(1020.0, replay.timeMs(1))
		val replayed = ReplayJudge.play(JudgeChart.compile(chart), replay)
		assertEquals(recorder.judge.judgedDeltaMs(0), replayed.judgedDeltaMs(0))
	}

	@Test
	fun theRecorderGrowsPastItsExpectedSize() {
		val recorder = ReplayRecorder(JudgeChart.compile(chartOf()), Replay.hashOf(ByteArray(0)), 0.0, 1)
		for (i in 0 until 1000) recorder.aimSample(i.toDouble(), 0f, 0f)
		assertEquals(1000, recorder.toReplay().eventCount)
		assertEquals(999.0, recorder.toReplay().timeMs(999))
	}

	@Test
	fun theRecorderStopsAtTheEventLimitAndStillJudges() {
		val chart = chartOf(Note.Tap(beat(1), 0))
		val recorder = ReplayRecorder(JudgeChart.compile(chart), Replay.hashOf(ByteArray(0)), 0.0, 4)
		for (i in 0 until Replay.MAX_EVENTS + 5) recorder.aimSample(0.0, 0f, 0f)
		assertEquals(true, recorder.isFull)
		recorder.pressLane(0, 1000.0)
		assertEquals(1, recorder.judge.judgedCount)
		assertEquals(Replay.MAX_EVENTS, recorder.toReplay().eventCount)
	}

	@Test
	fun recordingWithinItsExpectedSizeDoesNotAllocate() {
		val threads = ManagementFactory.getThreadMXBean() as com.sun.management.ThreadMXBean
		val chart = chartOf(Note.Tap(beat(1), 0))
		val recorder = ReplayRecorder(JudgeChart.compile(chart), Replay.hashOf(ByteArray(0)), 0.0, 400_000)
		val thread = Thread.currentThread().threadId()
		val before = threads.getThreadAllocatedBytes(thread)
		var i = 0
		while (i < 100_000) {
			val time = i.toDouble()
			recorder.pressLane(0, time)
			recorder.aimSample(time, 0.5f, 0.5f)
			recorder.releaseLane(0, time)
			recorder.advanceTo(time)
			i++
		}
		val allocated = threads.getThreadAllocatedBytes(thread) - before
		assertTrue(allocated < 64 * 1024, "allocated $allocated bytes")
	}

	// Re-judging from the files the server would hold.

	@Test
	fun rejudgeGivesTheRecordedResult() {
		val recorder = recorded()
		val result = ReplayJudge.rejudge(chartFile, recorder.toReplay())
		assertNull(result.error)
		val judge = assertNotNull(result.judge)
		assertEquals(recorder.judge.score, judge.score)
		assertEquals(results(recorder.judge), results(judge))
	}

	@Test
	fun rejudgeRejectsAReplayOfADifferentChart() {
		val other = ChartWriter.writeDifficulty(chart.copy(level = 2)).toByteArray()
		val result = ReplayJudge.rejudge(other, recorded().toReplay())
		assertNull(result.judge)
		assertTrue("different chart" in result.error.orEmpty(), result.error)
	}

	@Test
	fun rejudgeRejectsAnotherJudgeVersion() {
		val bytes = fileBytes(judgeVersion = JudgementConfig.JUDGE_VERSION + 1, hash = Replay.hashOf(chartFile))
		val replay = assertNotNull(ReplayFile.read(gzip(bytes)).replay)
		val result = ReplayJudge.rejudge(chartFile, replay)
		assertNull(result.judge)
		assertTrue("judge version" in result.error.orEmpty(), result.error)
	}

	@Test
	fun rejudgeRejectsABrokenChartFile() {
		val broken = "{ not json".toByteArray()
		val replay = assertNotNull(ReplayFile.read(gzip(fileBytes(hash = Replay.hashOf(broken), judgeVersion = JudgementConfig.JUDGE_VERSION))).replay)
		val result = ReplayJudge.rejudge(broken, replay)
		assertNull(result.judge)
		assertTrue("chart" in result.error.orEmpty(), result.error)
	}

	@Test
	fun rejudgeRejectsAChartTheValidatorRejects() {
		val invalid = ChartWriter.writeDifficulty(chart.copy(level = 0)).toByteArray()
		assertNotNull(ChartParser.parseDifficulty(String(invalid)).value)
		val replay = assertNotNull(ReplayFile.read(gzip(fileBytes(hash = Replay.hashOf(invalid), judgeVersion = JudgementConfig.JUDGE_VERSION))).replay)
		val result = ReplayJudge.rejudge(invalid, replay)
		assertNull(result.judge)
		assertTrue("level" in result.error.orEmpty(), result.error)
	}
}
