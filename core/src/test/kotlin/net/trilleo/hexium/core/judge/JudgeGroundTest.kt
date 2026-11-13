package net.trilleo.hexium.core.judge

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import net.trilleo.hexium.core.chart.BpmChange
import net.trilleo.hexium.core.chart.Difficulty
import net.trilleo.hexium.core.chart.Note

class JudgeGroundTest {
	/** A note at time 0, so window limits are exact in floating point. */
	private fun tapAt(pressMs: Double, difficulty: Difficulty = Difficulty.EXPERT): List<Judgement> {
		val judge = judgeOf(Note.Tap(beat(0), 0), difficulty = difficulty)
		judge.pressLane(0, pressMs)
		judge.finish()
		return judge.judgements()
	}

	private val jc = listOf(Judgement.JUSTICE_CRITICAL)
	private val justice = listOf(Judgement.JUSTICE)
	private val attack = listOf(Judgement.ATTACK)
	private val miss = listOf(Judgement.MISS)

	@Test
	fun tapWindowsAreInclusiveOnBothSides() {
		assertEquals(jc, tapAt(0.0))
		assertEquals(jc, tapAt(20.0))
		assertEquals(jc, tapAt(33.33))
		assertEquals(jc, tapAt(-33.33))
		assertEquals(justice, tapAt(33.34))
		assertEquals(justice, tapAt(-33.34))
		assertEquals(justice, tapAt(66.67))
		assertEquals(justice, tapAt(-66.67))
		assertEquals(attack, tapAt(66.68))
		assertEquals(attack, tapAt(-66.68))
		assertEquals(attack, tapAt(83.33))
		assertEquals(attack, tapAt(-83.33))
	}

	@Test
	fun lateBeyondAttackIsAMissAndThePressDoesNothing() {
		val judge = judgeOf(Note.Tap(beat(0), 0))
		judge.pressLane(0, 83.34)
		assertEquals(miss, judge.judgements())
		judge.finish()
		assertEquals(1, judge.judgedCount)
	}

	@Test
	fun earlyBeyondAttackUsesUpTheNoteAsAMissUpTo150Ms() {
		assertEquals(miss, tapAt(-83.34))
		assertEquals(miss, tapAt(-150.0))
		val judge = judgeOf(Note.Tap(beat(0), 0))
		judge.pressLane(0, -100.0)
		assertEquals(miss, judge.judgements())
		judge.releaseLane(0, -90.0)
		judge.pressLane(0, 0.0)
		assertEquals(1, judge.judgedCount, "the note is already used up")
	}

	@Test
	fun pressEarlierThan150MsDoesNothing() {
		val judge = judgeOf(Note.Tap(beat(0), 0))
		judge.pressLane(0, -150.01)
		assertEquals(0, judge.judgedCount)
		judge.releaseLane(0, -100.0)
		judge.pressLane(0, 10.0)
		assertEquals(jc, judge.judgements())
	}

	@Test
	fun basicAndAdvancedUseTheEasyAttackWindow() {
		for (difficulty in listOf(Difficulty.BASIC, Difficulty.ADVANCED)) {
			assertEquals(attack, tapAt(100.0, difficulty))
			assertEquals(attack, tapAt(-100.0, difficulty))
			assertEquals(miss, tapAt(100.01, difficulty))
			assertEquals(miss, tapAt(-100.01, difficulty))
			assertEquals(jc, tapAt(33.33, difficulty))
			assertEquals(justice, tapAt(66.67, difficulty))
		}
	}

	@Test
	fun expertAndMasterUseTheNormalAttackWindow() {
		for (difficulty in listOf(Difficulty.EXPERT, Difficulty.MASTER)) {
			assertEquals(attack, tapAt(83.33, difficulty))
			assertEquals(miss, tapAt(83.34, difficulty))
		}
	}

	@Test
	fun unpressedNoteMissesOnceItsWindowHasPassed() {
		val judge = judgeOf(Note.Tap(beat(0), 0))
		judge.advanceTo(83.33)
		assertEquals(0, judge.judgedCount)
		judge.advanceTo(83.34)
		assertEquals(miss, judge.judgements())
	}

	@Test
	fun pressWithNoNoteNearbyDoesNothing() {
		val judge = judgeOf(Note.Tap(beat(5), 0))
		judge.pressLane(0, 1000.0)
		assertEquals(0, judge.judgedCount)
		assertEquals(1, judgeOf(Note.Tap(beat(5), 0)).objectCount)
	}

	@Test
	fun otherLanesDoNotHitTheNote() {
		val judge = judgeOf(Note.Tap(beat(1), 2))
		judge.pressLane(1, 1000.0)
		assertEquals(0, judge.judgedCount)
		judge.pressLane(2, 1000.0)
		assertEquals(jc, judge.judgements())
	}

	@Test
	fun lanesOutsideTheChartAreIgnored() {
		val judge = judgeOf(Note.Tap(beat(1), 0))
		judge.pressLane(-1, 1000.0)
		judge.pressLane(4, 1000.0)
		judge.releaseLane(9, 1000.0)
		assertEquals(0, judge.judgedCount)
	}

	@Test
	fun onePressResolvesOnlyTheEarliestNote() {
		val judge = judgeOf(Note.Tap(beat(1), 0), Note.Tap(beat(1, 1, 10), 0))
		judge.pressLane(0, 1050.0)
		assertEquals(justice, judge.judgements())
		judge.releaseLane(0, 1060.0)
		judge.pressLane(0, 1100.0)
		assertEquals(justice + jc, judge.judgements())
	}

	@Test
	fun notesAreResolvedWhateverTheFileOrder() {
		val judge = judgeOf(Note.Tap(beat(2), 0), Note.Tap(beat(1), 0))
		judge.pressLane(0, 1000.0)
		judge.releaseLane(0, 1010.0)
		judge.pressLane(0, 2000.0)
		assertEquals(jc + jc, judge.judgements())
		assertEquals(1, judge.judgedNote(0))
		assertEquals(0, judge.judgedNote(1))
	}

	@Test
	fun keyAlreadyDownDoesNotPressAgain() {
		val judge = judgeOf(Note.Tap(beat(1), 0))
		judge.pressLane(0, 500.0)
		judge.pressLane(0, 1000.0)
		assertEquals(0, judge.judgedCount)
	}

	@Test
	fun missedNoteBeforeAPressGoesFirst() {
		val judge = judgeOf(Note.Tap(beat(1), 0), Note.Tap(beat(2), 1))
		judge.pressLane(1, 2000.0)
		assertEquals(miss + jc, judge.judgements())
	}

	@Test
	fun recordsThePressOffset() {
		val judge = judgeOf(Note.Tap(beat(1), 0))
		judge.pressLane(0, 980.0)
		assertEquals(-20.0, judge.judgedDeltaMs(0), 1e-9)
		val missed = judgeOf(Note.Tap(beat(1), 0))
		missed.finish()
		assertTrue(missed.judgedDeltaMs(0).isNaN())
	}

	// Holds. Beat 1 is 1000 ms, so ticks every 1/4 beat are 250 ms apart.

	@Test
	fun holdHeadIsJudgedLikeATap() {
		val judge = judgeOf(Note.Hold(beat(1), beat(2), 0))
		judge.pressLane(0, 1040.0)
		assertEquals(justice, judge.judgements())
	}

	@Test
	fun ticksFallEveryQuarterBeatExcludingStartAndEnd() {
		val chart = JudgeChart.compile(chartOf(Note.Hold(beat(1), beat(2), 0)))
		assertEquals(4, chart.objectCount, "head plus ticks at 1250, 1500 and 1750")
		val judge = Judge(chart)
		judge.pressLane(0, 1000.0)
		judge.advanceTo(1250.0)
		assertEquals(1, judge.judgedCount)
		judge.advanceTo(1250.01)
		assertEquals(2, judge.judgedCount)
		judge.advanceTo(1750.01)
		assertEquals(4, judge.judgedCount)
		judge.finish()
		assertEquals(4, judge.judgedCount, "no tail")
		assertEquals(jc + jc + jc + jc, judge.judgements())
	}

	@Test
	fun ticksFromAnOffGridStartAreCountedFromIt() {
		val judge = judgeOf(Note.Hold(beat(1, 1, 3), beat(2), 0))
		judge.pressLane(0, 1000.0 + 1000.0 / 3.0)
		judge.advanceTo(1583.33)
		assertEquals(1, judge.judgedCount, "first tick at 1583.33...")
		judge.advanceTo(1583.34)
		assertEquals(2, judge.judgedCount)
		judge.advanceTo(1833.34)
		assertEquals(3, judge.judgedCount)
		judge.finish()
		assertEquals(3, judge.judgedCount, "the next would be past the end")
	}

	@Test
	fun holdHeadsUseTheEasyWindowsAndTheEarlyMissToo() {
		fun head(pressMs: Double, difficulty: Difficulty): Judgement {
			val judge = judgeOf(Note.Hold(beat(0), beat(1), 0), difficulty = difficulty)
			judge.pressLane(0, pressMs)
			return judge.judgement(0)
		}
		assertEquals(Judgement.ATTACK, head(100.0, Difficulty.BASIC))
		assertEquals(Judgement.MISS, head(100.01, Difficulty.BASIC))
		assertEquals(Judgement.MISS, head(83.34, Difficulty.EXPERT))
		assertEquals(Judgement.MISS, head(-150.0, Difficulty.EXPERT))
		val tooEarly = judgeOf(Note.Hold(beat(0), beat(1), 0))
		tooEarly.pressLane(0, -150.01)
		assertEquals(0, tooEarly.judgedCount)
	}

	@Test
	fun tickOnAnExactFractionOfTheEndIsLeftOut() {
		val chart = JudgeChart.compile(chartOf(Note.Hold(beat(0, 1, 3), beat(0, 5, 6), 0)))
		assertEquals(2, chart.objectCount, "1/3 + 1/2 beat long: head plus one tick, none on the end")
		val longer = JudgeChart.compile(chartOf(Note.Hold(beat(0, 1, 3), beat(0, 6, 7), 0)))
		assertEquals(3, longer.objectCount)
	}

	@Test
	fun holdTooShortForATickGetsOneAtItsEnd() {
		val chart = JudgeChart.compile(chartOf(Note.Hold(beat(1), beat(1, 1, 8), 0)))
		assertEquals(2, chart.objectCount)
		val judge = Judge(chart)
		judge.pressLane(0, 1000.0)
		judge.advanceTo(1125.0)
		assertEquals(1, judge.judgedCount)
		judge.advanceTo(1125.01)
		assertEquals(2, judge.judgedCount)
		val exact = JudgeChart.compile(chartOf(Note.Hold(beat(1), beat(1, 1, 4), 0)))
		assertEquals(2, exact.objectCount, "exactly 1/4 beat long also has no tick between")
	}

	@Test
	fun ticksFollowBpmChanges() {
		val chart = chartOf(Note.Hold(beat(0), beat(2), 0))
		val compiled = JudgeChart.compile(chart.copy(bpm = chart.bpm + BpmChange(beat(1), 120.0)))
		val judge = Judge(compiled)
		judge.pressLane(0, 0.0)
		judge.advanceTo(1125.0)
		assertEquals(1 + 3 + 1, judge.judgedCount, "ticks at 250, 500, 750 and 1000 ms")
		judge.advanceTo(1125.01)
		assertEquals(6, judge.judgedCount)
	}

	private fun tickFor(age: Double): Judgement {
		val judge = judgeOf(Note.Hold(beat(0), beat(0, 1, 8), 0))
		judge.pressLane(0, 0.0)
		judge.releaseLane(0, 125.0 - age)
		judge.finish()
		assertEquals(2, judge.judgedCount)
		return judge.judgement(1)
	}

	@Test
	fun tickWindowsAreOnHowLongAgoTheKeyWasDown() {
		assertEquals(Judgement.JUSTICE_CRITICAL, tickFor(0.0))
		assertEquals(Judgement.JUSTICE_CRITICAL, tickFor(50.0))
		assertEquals(Judgement.JUSTICE, tickFor(50.5))
		assertEquals(Judgement.JUSTICE, tickFor(100.0))
		assertEquals(Judgement.ATTACK, tickFor(100.5))
		assertEquals(Judgement.ATTACK, tickFor(120.0))
	}

	@Test
	fun tickWindowsEndAt150Ms() {
		val attackJudge = judgeOf(Note.Hold(beat(1), beat(1, 1, 8), 0))
		attackJudge.pressLane(0, 975.0)
		attackJudge.releaseLane(0, 975.0)
		attackJudge.finish()
		assertEquals(Judgement.ATTACK, attackJudge.judgement(1), "released exactly 150 ms before the tick")

		val missJudge = judgeOf(Note.Hold(beat(1), beat(1, 1, 8), 0))
		missJudge.pressLane(0, 974.5)
		missJudge.releaseLane(0, 974.5)
		missJudge.finish()
		assertEquals(Judgement.MISS, missJudge.judgement(1))
	}

	@Test
	fun tickWithTheKeyDownIsJusticeCritical() {
		val judge = judgeOf(Note.Hold(beat(0), beat(1), 0))
		judge.pressLane(0, 0.0)
		judge.finish()
		assertEquals(List(4) { Judgement.JUSTICE_CRITICAL }, judge.judgements())
	}

	@Test
	fun releasingAndPressingAgainInsideTheWindowKeepsJusticeCritical() {
		val judge = judgeOf(Note.Hold(beat(0), beat(1), 0))
		judge.pressLane(0, 0.0)
		judge.releaseLane(0, 230.0)
		judge.pressLane(0, 260.0)
		judge.finish()
		assertEquals(List(4) { Judgement.JUSTICE_CRITICAL }, judge.judgements())
	}

	@Test
	fun pressAfterATickDoesNotHelpIt() {
		val judge = judgeOf(Note.Hold(beat(0), beat(1), 0))
		judge.pressLane(0, -1000.0)
		judge.releaseLane(0, -1000.0)
		judge.pressLane(0, 251.0)
		judge.finish()
		assertEquals(
			listOf(Judgement.MISS, Judgement.JUSTICE_CRITICAL, Judgement.JUSTICE_CRITICAL),
			judge.judgements().drop(1),
		)
	}

	@Test
	fun missedHeadDoesNotFailTheTicks() {
		val judge = judgeOf(Note.Hold(beat(1), beat(2), 0))
		judge.pressLane(0, 1200.0)
		judge.finish()
		assertEquals(listOf(Judgement.MISS) + List(3) { Judgement.JUSTICE_CRITICAL }, judge.judgements())
	}

	@Test
	fun neverPressedHoldMissesEverything() {
		val judge = judgeOf(Note.Hold(beat(1), beat(2), 0))
		judge.finish()
		assertEquals(List(4) { Judgement.MISS }, judge.judgements())
	}

	@Test
	fun keyDownBeforeTheHoldCountsForItsTicks() {
		val judge = judgeOf(Note.Hold(beat(1), beat(2), 0))
		judge.pressLane(0, 500.0)
		judge.finish()
		assertEquals(listOf(Judgement.MISS) + List(3) { Judgement.JUSTICE_CRITICAL }, judge.judgements())
	}

	@Test
	fun holdingPastTheEndIsNeverPenalised() {
		val judge = judgeOf(Note.Hold(beat(0), beat(1), 0))
		judge.pressLane(0, 0.0)
		judge.advanceTo(100_000.0)
		judge.finish()
		assertEquals(List(4) { Judgement.JUSTICE_CRITICAL }, judge.judgements())
	}

	@Test
	fun skippingAStepDoesNotChangeTheOrderOrResult() {
		val notes = arrayOf(
			Note.Hold(beat(1), beat(2), 0),
			Note.Tap(beat(1, 1, 2), 1),
			Note.Tap(beat(1, 3, 4), 2),
			Note.Hold(beat(2), beat(3), 3),
		)
		fun play(step: Double): List<Pair<Int, Judgement>> {
			val judge = judgeOf(*notes)
			var time = 0.0
			fun advance(to: Double) {
				while (time + step < to) {
					time += step
					judge.advanceTo(time)
				}
				time = to
				judge.advanceTo(to)
			}
			advance(1000.0)
			judge.pressLane(0, 1000.0)
			advance(1750.0)
			judge.pressLane(2, 1750.0)
			advance(1900.0)
			judge.releaseLane(0, 1900.0)
			advance(2600.0)
			judge.pressLane(3, 2600.0)
			judge.finish()
			return List(judge.judgedCount) { judge.judgedNote(it) to judge.judgement(it) }
		}
		val baseline = play(1000.0)
		assertEquals(baseline, play(4.0))
		assertEquals(baseline, play(16.67))
		assertEquals(chartOf(*notes).let { JudgeChart.compile(it).objectCount }, baseline.size)
	}

	@Test
	fun inputTimesCannotGoBackwards() {
		val judge = judgeOf(Note.Tap(beat(1), 0))
		judge.advanceTo(1020.0)
		judge.pressLane(0, 1000.0)
		assertEquals(20.0, judge.judgedDeltaMs(0), 1e-9)
	}
}
