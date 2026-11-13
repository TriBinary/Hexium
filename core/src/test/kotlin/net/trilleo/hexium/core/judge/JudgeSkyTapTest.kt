package net.trilleo.hexium.core.judge

import kotlin.test.Test
import kotlin.test.assertEquals
import net.trilleo.hexium.core.chart.Difficulty
import net.trilleo.hexium.core.chart.Note
import net.trilleo.hexium.core.chart.SkyButton

class JudgeSkyTapTest {
	private val jc = listOf(Judgement.JUSTICE_CRITICAL)
	private val justice = listOf(Judgement.JUSTICE)
	private val attack = listOf(Judgement.ATTACK)
	private val miss = listOf(Judgement.MISS)

	private fun tap(whole: Int = 0, x: Double = 0.5, y: Double = 0.5, button: SkyButton = SkyButton.L, size: Double = 1.0) =
		Note.SkyTap(beat(whole), x, y, button, size)

	private fun clickAt(ms: Double, difficulty: Difficulty = Difficulty.EXPERT): List<Judgement> {
		val judge = judgeOf(tap(), difficulty = difficulty)
		judge.pressButton(SkyButton.L, ms, 0.5f, 0.5f)
		judge.finish()
		return judge.judgements()
	}

	@Test
	fun windowsMatchGroundTaps() {
		assertEquals(jc, clickAt(33.33))
		assertEquals(jc, clickAt(-33.33))
		assertEquals(justice, clickAt(66.67))
		assertEquals(justice, clickAt(-66.67))
		assertEquals(attack, clickAt(83.33))
		assertEquals(attack, clickAt(-83.33))
		assertEquals(miss, clickAt(83.34))
	}

	@Test
	fun easyChartsUseTheEasyAttackWindow() {
		assertEquals(attack, clickAt(100.0, Difficulty.BASIC))
		assertEquals(attack, clickAt(-100.0, Difficulty.ADVANCED))
		assertEquals(miss, clickAt(100.01, Difficulty.BASIC))
	}

	@Test
	fun earlyBeyondAttackUsesUpTheNoteUpTo150Ms() {
		val judge = judgeOf(tap())
		judge.pressButton(SkyButton.L, -150.0, 0.5f, 0.5f)
		assertEquals(miss, judge.judgements())

		val tooEarly = judgeOf(tap())
		tooEarly.pressButton(SkyButton.L, -150.01, 0.5f, 0.5f)
		assertEquals(0, tooEarly.judgedCount)
	}

	@Test
	fun unclickedNoteMissesOnceItsWindowHasPassed() {
		val judge = judgeOf(tap())
		judge.advanceTo(83.33)
		assertEquals(0, judge.judgedCount)
		judge.advanceTo(83.34)
		assertEquals(miss, judge.judgements())
	}

	@Test
	fun wrongButtonIsIgnoredAndTheNoteStaysHittable() {
		for ((note, wrong, right) in listOf(
			Triple(tap(button = SkyButton.L), SkyButton.R, SkyButton.L),
			Triple(tap(button = SkyButton.R), SkyButton.L, SkyButton.R),
		)) {
			val judge = judgeOf(note)
			judge.pressButton(wrong, 0.0, 0.5f, 0.5f)
			assertEquals(0, judge.judgedCount)
			judge.pressButton(right, 10.0, 0.5f, 0.5f)
			assertEquals(jc, judge.judgements())
		}
	}

	@Test
	fun clickOutsideTheHitAreaIsIgnoredAndCanTryAgain() {
		val judge = judgeOf(tap())
		judge.pressButton(SkyButton.L, 0.0, 0.5f, 0.59f)
		assertEquals(0, judge.judgedCount)
		judge.releaseButton(SkyButton.L, 5.0)
		judge.pressButton(SkyButton.L, 10.0, 0.5f, 0.5f)
		assertEquals(jc, judge.judgements())
	}

	@Test
	fun hitAreaIsACircleMeasuredInScreenHeights() {
		fun hits(x: Float, y: Float, size: Double = 1.0): Boolean {
			val judge = judgeOf(tap(size = size))
			judge.pressButton(SkyButton.L, 0.0, x, y)
			return judge.judgedCount == 1
		}
		assertEquals(true, hits(0.5f, 0.579f), "base radius is 0.08 of the height")
		assertEquals(false, hits(0.5f, 0.581f))
		assertEquals(true, hits(0.539f, 0.5f), "x is half as far because the screen is 2:1")
		assertEquals(false, hits(0.541f, 0.5f))
		assertEquals(false, hits(0.535f, 0.57f), "inside the box, outside the circle")
		assertEquals(true, hits(0.5f, 0.65f, size = 2.0), "size multiplies the radius")
		assertEquals(false, hits(0.5f, 0.55f, size = 0.5))
	}

	@Test
	fun theEdgeOfTheHitAreaCounts() {
		val config = JudgementConfig(baseHitRadius = 0.25)
		fun judge() = Judge(JudgeChart.compile(chartOf(tap()), config))
		val inside = judge()
		inside.pressButton(SkyButton.L, 0.0, 0.5f, 0.75f)
		assertEquals(1, inside.judgedCount)
		val outside = judge()
		outside.pressButton(SkyButton.L, 0.0, 0.5f, 0.7501f)
		assertEquals(0, outside.judgedCount)
	}

	@Test
	fun clickResolvesOnlyTheEarliestNoteUnderIt() {
		val judge = judgeOf(Note.SkyTap(beat(1), 0.5, 0.5, SkyButton.L, 1.0), Note.SkyTap(beat(1, 1, 20), 0.5, 0.5, SkyButton.L, 1.0))
		judge.pressButton(SkyButton.L, 1000.0, 0.5f, 0.5f)
		assertEquals(jc, judge.judgements())
		assertEquals(0, judge.judgedNote(0))
		judge.releaseButton(SkyButton.L, 1010.0)
		judge.pressButton(SkyButton.L, 1050.0, 0.5f, 0.5f)
		assertEquals(jc + jc, judge.judgements())
	}

	@Test
	fun clickSkipsAnEarlierNoteElsewhereOnScreen() {
		val judge = judgeOf(Note.SkyTap(beat(1), 0.1, 0.1, SkyButton.L, 1.0), Note.SkyTap(beat(1, 1, 20), 0.9, 0.9, SkyButton.L, 1.0))
		judge.pressButton(SkyButton.L, 1040.0, 0.9f, 0.9f)
		assertEquals(1, judge.judgedCount)
		assertEquals(1, judge.judgedNote(0))
		judge.releaseButton(SkyButton.L, 1041.0)
		judge.pressButton(SkyButton.L, 1042.0, 0.1f, 0.1f)
		assertEquals(0, judge.judgedNote(1))
	}

	@Test
	fun eachButtonHasItsOwnNotes() {
		val judge = judgeOf(tap(1, button = SkyButton.L), tap(1, button = SkyButton.R))
		judge.pressButton(SkyButton.R, 1000.0, 0.5f, 0.5f)
		assertEquals(1, judge.judgedNote(0))
		judge.pressButton(SkyButton.L, 1000.0, 0.5f, 0.5f)
		assertEquals(0, judge.judgedNote(1))
	}

	@Test
	fun buttonAlreadyDownDoesNotClickAgain() {
		val judge = judgeOf(tap(1))
		judge.pressButton(SkyButton.L, 500.0, 0.5f, 0.5f)
		judge.pressButton(SkyButton.L, 1000.0, 0.5f, 0.5f)
		assertEquals(0, judge.judgedCount)
	}

	@Test
	fun skyTapsCountTowardsScore() {
		val judge = judgeOf(tap(1), Note.Tap(beat(2), 0))
		assertEquals(2, judge.objectCount)
		judge.pressButton(SkyButton.L, 1000.0, 0.5f, 0.5f)
		judge.pressLane(0, 2000.0)
		judge.finish()
		assertEquals(1_010_000, judge.score)
	}
}
