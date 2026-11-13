package net.trilleo.hexium.core.judge

import kotlin.test.Test
import kotlin.test.assertEquals
import net.trilleo.hexium.core.chart.Note

class ScoreTest {
	private fun play(presses: List<Double?>): Judge {
		val notes = Array<Note>(presses.size) { Note.Tap(beat(it + 1), 0) }
		val judge = judgeOf(*notes)
		for (i in presses.indices) {
			val offset = presses[i] ?: continue
			val time = (i + 1) * 1000.0 + offset
			judge.pressLane(0, time)
			judge.releaseLane(0, time + 1.0)
		}
		judge.finish()
		return judge
	}

	private val jc = 0.0
	private val justice = 50.0
	private val attack = 70.0

	@Test
	fun perfectPlayScores1010000() {
		val judge = play(listOf(jc, jc, jc))
		assertEquals(1_010_000, judge.score)
		assertEquals(ClearLamp.ALL_JUSTICE_CRITICAL, judge.clearLamp)
	}

	@Test
	fun weightsAre101And100And50And0() {
		assertEquals(1_010_000, play(listOf(jc)).score)
		assertEquals(1_000_000, play(listOf(justice)).score)
		assertEquals(500_000, play(listOf(attack)).score)
		assertEquals(0, play(listOf(null)).score)
	}

	@Test
	fun scoreIsRoundedDown() {
		assertEquals(333_333, play(listOf(justice, null, null)).score)
		assertEquals(673_333, play(listOf(jc, jc, null)).score)
	}

	@Test
	fun everyObjectCountsTowardsTheTotal() {
		val judge = judgeOf(Note.Hold(beat(1), beat(2), 0), Note.Tap(beat(3), 1))
		assertEquals(5, judge.objectCount)
		judge.pressLane(0, 1000.0)
		judge.finish()
		assertEquals(4 * 1_010_000 / 5, judge.score, "head and three ticks out of five")
	}

	@Test
	fun scoreBuildsUpDuringPlay() {
		val judge = judgeOf(Note.Tap(beat(1), 0), Note.Tap(beat(2), 0))
		judge.pressLane(0, 1000.0)
		assertEquals(505_000, judge.score)
	}

	@Test
	fun comboGrowsWithEveryObjectAndAMissBreaksIt() {
		val judge = judgeOf(Note.Tap(beat(1), 0), Note.Tap(beat(2), 0), Note.Tap(beat(3), 0), Note.Tap(beat(4), 0))
		judge.pressLane(0, 1000.0)
		judge.releaseLane(0, 1001.0)
		judge.pressLane(0, 2070.0)
		judge.releaseLane(0, 2071.0)
		assertEquals(2, judge.combo)
		judge.advanceTo(3100.0)
		assertEquals(0, judge.combo)
		judge.pressLane(0, 4000.0)
		assertEquals(1, judge.combo)
		assertEquals(2, judge.maxCombo)
	}

	@Test
	fun ticksAddToCombo() {
		val judge = judgeOf(Note.Hold(beat(1), beat(2), 0))
		judge.pressLane(0, 1000.0)
		judge.finish()
		assertEquals(4, judge.combo)
		assertEquals(4, judge.maxCombo)
	}

	@Test
	fun countsEachJudgement() {
		val judge = play(listOf(jc, jc, justice, attack, null))
		assertEquals(2, judge.count(Judgement.JUSTICE_CRITICAL))
		assertEquals(1, judge.count(Judgement.JUSTICE))
		assertEquals(1, judge.count(Judgement.ATTACK))
		assertEquals(1, judge.count(Judgement.MISS))
	}

	@Test
	fun lampsFollowTheWorstJudgement() {
		assertEquals(ClearLamp.ALL_JUSTICE_CRITICAL, play(listOf(jc, jc)).clearLamp)
		assertEquals(ClearLamp.ALL_JUSTICE, play(listOf(jc, justice)).clearLamp)
		assertEquals(ClearLamp.FULL_COMBO, play(listOf(jc, attack)).clearLamp)
		assertEquals(ClearLamp.NONE, play(listOf(jc, null)).clearLamp)
	}

	@Test
	fun noLampBeforeTheEnd() {
		val judge = judgeOf(Note.Tap(beat(1), 0), Note.Tap(beat(2), 0))
		judge.pressLane(0, 1000.0)
		assertEquals(ClearLamp.NONE, judge.clearLamp)
	}

	@Test
	fun chartWithNoObjectsScoresZeroWithNoLamp() {
		val judge = judgeOf()
		judge.finish()
		assertEquals(0, judge.score)
		assertEquals(ClearLamp.NONE, judge.clearLamp)
	}

	@Test
	fun largeChartsDoNotOverflow() {
		val judge = judgeOf(*Array<Note>(200_000) { Note.Tap(beat(it + 1), it % 4) })
		for (i in 0 until 200_000) {
			val time = (i + 1) * 1000.0
			judge.pressLane(i % 4, time)
			judge.releaseLane(i % 4, time + 1.0)
		}
		judge.finish()
		assertEquals(1_010_000, judge.score)
	}
}
