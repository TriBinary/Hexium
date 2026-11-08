package net.trilleo.hexium.core.judge

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import net.trilleo.hexium.core.chart.Ease
import net.trilleo.hexium.core.chart.Note
import net.trilleo.hexium.core.chart.SkyButton
import net.trilleo.hexium.core.chart.SlidePoint

class JudgeFlickTest {
	/** At time 0, so window limits are exact. */
	private fun flick(whole: Int = 0, x: Double = 0.5, y: Double = 0.5, size: Double = 1.0) =
		Note.Flick(beat(whole), x, y, size)

	/** Starts inside the hit area at [ms] and leaves fast, so the crossing time is exactly [ms]. */
	private fun swipeFrom(ms: Double): List<Judgement> {
		val judge = judgeOf(flick())
		judge.aimSample(ms, 0.5f, 0.5f)
		judge.aimSample(ms + 10.0, 0.9f, 0.5f)
		judge.finish()
		return judge.judgements()
	}

	private val jc = listOf(Judgement.JUSTICE_CRITICAL)
	private val justice = listOf(Judgement.JUSTICE)
	private val miss = listOf(Judgement.MISS)

	@Test
	fun windowsAreOnTheCrossingTime() {
		assertEquals(jc, swipeFrom(0.0))
		assertEquals(jc, swipeFrom(83.33))
		assertEquals(jc, swipeFrom(-83.33))
		assertEquals(justice, swipeFrom(83.34))
		assertEquals(justice, swipeFrom(-83.34))
		assertEquals(justice, swipeFrom(166.67))
		assertEquals(justice, swipeFrom(-166.67))
		assertEquals(miss, swipeFrom(166.68))
	}

	@Test
	fun aSwipeStillInsideWhenTheWindowOpensCountsAsCrossingThen() {
		for (fps in listOf(30.0, 60.0, 120.0, 144.0, 240.0, 360.0)) {
			val judge = judgeOf(flick())
			val frame = 1000.0 / fps
			var time = -400.0
			while (time < 100.0) {
				// 6 heights per second, inside the hit area from 13.33 ms before -175 ms to 13.33 ms after it.
				judge.aimSample(time, (0.5 + 0.003 * (time + 175.0)).toFloat(), 0.5f)
				time += frame
			}
			judge.finish()
			assertEquals(justice, judge.judgements(), "$fps FPS")
			assertTrue(judge.judgedDeltaMs(0) >= -166.68, "${judge.judgedDeltaMs(0)} at $fps FPS")
		}
	}

	@Test
	fun theCrossingIsExactlyTheOpeningWhenASingleStepSpansIt() {
		val judge = judgeOf(flick())
		judge.aimSample(-170.0, 0.5f, 0.5f)
		judge.aimSample(-160.0, 0.52f, 0.5f)
		assertEquals(justice, judge.judgements())
		assertEquals(-166.67, judge.judgedDeltaMs(0), 1e-9)
	}

	@Test
	fun aSwipeThatLeavesBeforeTheWindowOpensDoesNothing() {
		for (fps in listOf(30.0, 60.0, 120.0, 360.0)) {
			val judge = judgeOf(flick())
			val frame = 1000.0 / fps
			var time = -400.0
			while (time < -100.0) {
				judge.aimSample(time, (0.5 + 0.003 * (time + 200.0)).toFloat(), 0.5f)
				time += frame
			}
			assertEquals(0, judge.judgedCount, "$fps FPS")
			judge.finish()
			assertEquals(miss, judge.judgements(), "$fps FPS")
		}
		val oneStep = judgeOf(flick())
		oneStep.aimSample(-200.0, 0.5f, 0.5f)
		oneStep.aimSample(-190.0, 0.9f, 0.5f)
		assertEquals(0, oneStep.judgedCount)
	}

	@Test
	fun aPressPastTheDeadlineDoesNotMissAFlickTheNextSampleShowsWasCrossed() {
		val judge = judgeOf(flick(1))
		judge.aimSample(1100.0, 0.3f, 0.5f)
		judge.pressLane(0, 1170.0)
		assertEquals(0, judge.judgedCount)
		judge.aimSample(1180.0, 0.7f, 0.5f)
		assertEquals(justice, judge.judgements())
	}

	@Test
	fun flicksAreRecordedInTheOrderTheyAreCrossed() {
		val judge = judgeOf(flick(1, x = 0.7), Note.Flick(beat(1, 1, 20), 0.3, 0.5, 1.0))
		judge.aimSample(1000.0, 0.1f, 0.5f)
		judge.aimSample(1010.0, 0.9f, 0.5f)
		assertEquals(listOf(1, 0), List(judge.judgedCount) { judge.judgedNote(it) })
	}

	/** A step from 10 ms before the window opens to 4 ms after, passing the given point exactly as it opens. */
	private fun stepThroughOpening(x: Double, y: Double, alongX: Boolean): Int {
		val judge = judgeOf(flick())
		val before = 0.15
		val after = 0.06
		if (alongX) {
			judge.aimSample(-176.67, (x - before / 2).toFloat(), y.toFloat())
			judge.aimSample(-162.67, (x + after / 2).toFloat(), y.toFloat())
		} else {
			judge.aimSample(-176.67, x.toFloat(), (y - before).toFloat())
			judge.aimSample(-162.67, x.toFloat(), (y + after).toFloat())
		}
		return judge.judgedCount
	}

	@Test
	fun theOpeningRuleMeasuresTheHitAreaWhereTheSwipeIsAsItOpens() {
		assertEquals(1, stepThroughOpening(0.5 + 0.075 / 2, 0.5, alongX = false), "0.075 heights to the side")
		assertEquals(0, stepThroughOpening(0.5 + 0.085 / 2, 0.5, alongX = false), "0.085 heights to the side")
		assertEquals(0, stepThroughOpening(0.575, 0.5, alongX = false), "0.075 of the width is 0.15 heights")
		assertEquals(1, stepThroughOpening(0.5, 0.575, alongX = true), "0.075 heights above")
		assertEquals(0, stepThroughOpening(0.5, 0.585, alongX = true), "0.085 heights above")
	}

	@Test
	fun aSlowSwipeInsideWhenTheWindowOpensStillDoesNothing() {
		val judge = judgeOf(flick())
		judge.aimSample(-200.0, 0.5f, 0.5f)
		judge.aimSample(-100.0, 0.5f, 0.5f)
		assertEquals(0, judge.judgedCount)
	}

	@Test
	fun noClickIsNeeded() {
		val judge = judgeOf(flick())
		judge.pressButton(SkyButton.L, 0.0, 0.5f, 0.5f)
		assertEquals(0, judge.judgedCount)
	}

	@Test
	fun fastSwipeThatJumpsOverTheNoteStillHits() {
		val judge = judgeOf(flick(1))
		judge.aimSample(983.0, 0.1f, 0.5f)
		judge.aimSample(1016.0, 0.9f, 0.5f)
		assertEquals(jc, judge.judgements())
	}

	@Test
	fun crossingTimeIsWhereTheSwipeFirstEntersTheHitArea() {
		val judge = judgeOf(flick(1))
		judge.aimSample(1000.0, 0.26f, 0.5f)
		judge.aimSample(1100.0, 0.56f, 0.5f)
		assertEquals(jc, judge.judgements())
		assertEquals(200.0 / 3.0, judge.judgedDeltaMs(0), 0.01, "enters at x = 0.46, two thirds of the way along")
	}

	@Test
	fun swipeThatStartsInsideUsesItsFirstSample() {
		val judge = judgeOf(flick(1))
		judge.aimSample(1020.0, 0.5f, 0.5f)
		judge.aimSample(1030.0, 0.9f, 0.5f)
		assertEquals(20.0, judge.judgedDeltaMs(0), 1e-9)
	}

	@Test
	fun swipeThatMissesTheHitAreaDoesNothing() {
		val judge = judgeOf(flick(1))
		judge.aimSample(990.0, 0.1f, 0.7f)
		judge.aimSample(1010.0, 0.9f, 0.7f)
		assertEquals(0, judge.judgedCount)
		judge.finish()
		assertEquals(miss, judge.judgements())
	}

	@Test
	fun swipeEndingBeforeTheHitAreaDoesNothing() {
		val judge = judgeOf(flick(1))
		judge.aimSample(990.0, 0.1f, 0.5f)
		judge.aimSample(1010.0, 0.3f, 0.5f)
		assertEquals(0, judge.judgedCount)
	}

	@Test
	fun theSwipeMustBeFasterThanTheMinimumSpeed() {
		// Across x, 0.5 of the width is 1 screen height because the screen is 2:1.
		val tooSlow = judgeOf(flick())
		tooSlow.aimSample(-500.0, 0.25f, 0.5f)
		tooSlow.aimSample(500.0, 0.75f, 0.5f)
		assertEquals(miss, tooSlow.judgements(), "exactly 1 height per second isn't faster, and the sample passed the deadline")
		assertTrue(tooSlow.judgedDeltaMs(0).isNaN())

		val fastEnough = judgeOf(flick())
		fastEnough.aimSample(-500.0, 0.25f, 0.5f)
		fastEnough.aimSample(490.0, 0.75f, 0.5f)
		assertEquals(justice, fastEnough.judgements())
	}

	@Test
	fun aParkedCrosshairDoesNotHit() {
		val judge = judgeOf(flick())
		judge.aimSample(-10.0, 0.5f, 0.5f)
		judge.aimSample(0.0, 0.5f, 0.5f)
		judge.aimSample(10.0, 0.5f, 0.5f)
		assertEquals(0, judge.judgedCount)
	}

	@Test
	fun tooSlowThenFastStillHitsInsideTheWindow() {
		val judge = judgeOf(flick())
		judge.aimSample(-100.0, 0.5f, 0.5f)
		judge.aimSample(-50.0, 0.5f, 0.5f)
		judge.aimSample(-40.0, 0.9f, 0.5f)
		assertEquals(jc, judge.judgements())
		assertEquals(-50.0, judge.judgedDeltaMs(0), 1e-9)
	}

	/** Swipes through the note at [degrees], measured in screen heights. */
	private fun swipeAt(degrees: Double): Int {
		val judge = judgeOf(flick())
		val radians = Math.toRadians(degrees)
		val dx = Math.cos(radians) * 0.2 / 2.0
		val dy = Math.sin(radians) * 0.2
		judge.aimSample(-5.0, (0.5 - dx).toFloat(), (0.5 - dy).toFloat())
		judge.aimSample(5.0, (0.5 + dx).toFloat(), (0.5 + dy).toFloat())
		return judge.judgedCount
	}

	@Test
	fun aSwipeFromAnyDirectionHits() {
		for (degrees in listOf(0.0, 33.0, 45.0, 90.0, 135.0, 180.0, 225.0, 270.0, 315.0, 359.0)) assertEquals(1, swipeAt(degrees), "$degrees degrees")
	}

	@Test
	fun aSwipeBackAfterMissingCanStillHit() {
		val judge = judgeOf(flick())
		judge.aimSample(-30.0, 0.9f, 0.9f)
		judge.aimSample(-20.0, 0.9f, 0.1f)
		assertEquals(0, judge.judgedCount, "the first swipe passed beside the note")
		// Back across the middle, through the note.
		judge.aimSample(-10.0, 0.1f, 0.9f)
		assertEquals(jc, judge.judgements())
	}

	@Test
	fun oneSwipeCanHitSeveralFlicks() {
		val judge = judgeOf(flick(1, x = 0.3), flick(1, x = 0.7))
		judge.aimSample(990.0, 0.1f, 0.5f)
		judge.aimSample(1010.0, 0.9f, 0.5f)
		assertEquals(jc + jc, judge.judgements())
		assertEquals(0, judge.judgedNote(0), "the one crossed first is recorded first")
		assertEquals(1, judge.judgedNote(1))
	}

	@Test
	fun aCrossingBeforeTheDeadlineCountsEvenIfTheNextSampleIsAfterIt() {
		val judge = judgeOf(flick())
		judge.aimSample(165.0, 0.5f, 0.5f)
		judge.advanceTo(170.0)
		assertEquals(0, judge.judgedCount, "a swipe may still be under way")
		judge.aimSample(175.0, 0.9f, 0.5f)
		assertEquals(justice, judge.judgements())
	}

	@Test
	fun advancingBetweenSamplesDoesNotChangeTheOrderOfResults() {
		fun play(advance: Boolean): List<Int> {
			val judge = judgeOf(flick(1), Note.Tap(beat(1, 1, 20), 0))
			judge.aimSample(900.0, 0.1f, 0.5f)
			if (advance) judge.advanceTo(1140.0)
			judge.aimSample(1150.0, 0.9f, 0.5f)
			judge.finish()
			return List(judge.judgedCount) { judge.judgedNote(it) }
		}
		assertEquals(listOf(0, 1), play(advance = false), "crossed at about 1012 ms, before the tap's deadline")
		assertEquals(play(advance = false), play(advance = true))
	}

	@Test
	fun notesWaitForTheNextSampleOnlyOnceAFlickWindowHasOpened() {
		val judge = judgeOf(flick(1), Note.Tap(beat(0), 0))
		judge.advanceTo(833.0)
		assertEquals(1, judge.judgedCount, "the flick's window hasn't opened, so the tap's Miss needn't wait")
	}

	// An open flick window must not hold back other notes when an input arrives.

	@Test
	fun aPressDuringAFlickWindowDoesNotHelpATickThatAlreadyPassed() {
		fun play(samples: List<Double>): Judge {
			val judge = judgeOf(flick(1), Note.Hold(beat(0, 1, 2), beat(1, 1, 2), 0))
			judge.pressLane(0, 500.0)
			judge.releaseLane(0, 700.0)
			for (ms in samples.filter { it < 1005.0 }) judge.aimSample(ms, 0.1f, 0.1f)
			judge.pressLane(0, 1005.0)
			for (ms in samples.filter { it >= 1005.0 }) judge.aimSample(ms, 0.1f, 0.1f)
			judge.finish()
			return judge
		}
		for (samples in listOf(listOf(995.0, 1011.0), List(600) { 500.0 + it * 2.0 })) {
			val judge = play(samples)
			assertEquals(3, judge.count(Judgement.JUSTICE_CRITICAL), "head, tick at 750, tick at 1250")
			assertEquals(2, judge.count(Judgement.MISS), "the tick at 1000 and the flick")
			assertEquals(606_000, judge.score)
		}
	}

	@Test
	fun aPressDuringAFlickWindowGoesToTheRightTap() {
		val judge = judgeOf(flick(1), Note.Tap(beat(1), 0), Note.Tap(beat(1, 1, 10), 0))
		judge.aimSample(1080.0, 0.1f, 0.1f)
		judge.pressLane(0, 1090.0)
		judge.aimSample(1096.0, 0.1f, 0.1f)
		judge.finish()
		assertEquals(1, judge.count(Judgement.JUSTICE_CRITICAL), "the second tap, 10 ms early")
		assertEquals(2, judge.count(Judgement.MISS), "the first tap and the flick")
		assertEquals(336_666, judge.score)
	}

	@Test
	fun aClickDuringAFlickWindowGoesToTheRightSkyTap() {
		val judge = judgeOf(
			flick(1, x = 0.9),
			Note.SkyTap(beat(1), 0.5, 0.5, SkyButton.L, 1.0),
			Note.SkyTap(beat(1, 1, 10), 0.5, 0.5, SkyButton.L, 1.0),
		)
		judge.aimSample(1080.0, 0.1f, 0.1f)
		judge.pressButton(SkyButton.L, 1090.0, 0.5f, 0.5f)
		judge.finish()
		assertEquals(1, judge.count(Judgement.JUSTICE_CRITICAL))
		assertEquals(2, judge.count(Judgement.MISS))
	}

	@Test
	fun aClickDuringAFlickWindowDoesNotHelpASlideTickThatAlreadyPassed() {
		val slide = Note.Slide(
			SkyButton.L,
			0.1,
			listOf(SlidePoint(beat(0), 0.5, 0.5, Ease.LINEAR), SlidePoint(beat(1, 1, 8), 0.5, 0.5, Ease.LINEAR)),
		)
		val judge = judgeOf(flick(1, x = 0.9), slide)
		judge.aimSample(995.0, 0.5f, 0.5f)
		judge.pressButton(SkyButton.L, 1005.0, 0.5f, 0.5f)
		judge.finish()
		assertEquals(5, judge.count(Judgement.MISS), "the flick and every tick up to 1000 ms")
	}

	@Test
	fun aReplayWithNoAimSamplesGetsNoFreeTicks() {
		val judge = judgeOf(flick(1), Note.Hold(beat(2), beat(10), 0))
		judge.pressLane(0, 500_000.0)
		judge.finish()
		assertEquals(0, judge.score)
		assertEquals(judge.objectCount, judge.count(Judgement.MISS))
	}

	@Test
	fun minimumSpeedAppliesOnTheYAxisToo() {
		val tooSlow = judgeOf(flick())
		tooSlow.aimSample(-500.0, 0.5f, 0f)
		tooSlow.aimSample(500.0, 0.5f, 1f)
		assertEquals(miss, tooSlow.judgements(), "exactly 1 height per second isn't faster")

		val fastEnough = judgeOf(flick())
		fastEnough.aimSample(-500.0, 0.5f, 0f)
		fastEnough.aimSample(490.0, 0.5f, 1f)
		assertEquals(justice, fastEnough.judgements())
	}

	@Test
	fun noSwipeMissesOnceASamplePassesTheDeadline() {
		val judge = judgeOf(flick())
		judge.aimSample(166.0, 0.1f, 0.1f)
		assertEquals(0, judge.judgedCount)
		judge.aimSample(167.0, 0.1f, 0.1f)
		assertEquals(miss, judge.judgements())
		assertTrue(judge.judgedDeltaMs(0).isNaN())
	}

	@Test
	fun finishMissesAnUnswipedFlick() {
		val judge = judgeOf(flick())
		judge.finish()
		assertEquals(miss, judge.judgements())
	}

	@Test
	fun sizeMultipliesTheHitArea() {
		val judge = judgeOf(flick(size = 2.0))
		judge.aimSample(-5.0, 0.1f, 0.65f)
		judge.aimSample(5.0, 0.9f, 0.65f)
		assertEquals(1, judge.judgedCount)
	}

	@Test
	fun frameRateBarelyChangesTheCrossingTime() {
		val deltas = ArrayList<Double>()
		for (fps in listOf(60.0, 144.0, 360.0)) {
			val judge = judgeOf(flick(1))
			val step = 1000.0 / fps
			var time = 950.0
			while (judge.judgedCount == 0 && time < 1100.0) {
				judge.aimSample(time, (0.2 + (time - 950.0) * 0.006).toFloat(), 0.5f)
				time += step
			}
			deltas += judge.judgedDeltaMs(0)
		}
		assertTrue(deltas.max() - deltas.min() < 0.01, deltas.toString())
	}

	@Test
	fun flicksCountTowardsScore() {
		val judge = judgeOf(flick(1))
		assertEquals(1, judge.objectCount)
		judge.aimSample(1000.0, 0.5f, 0.5f)
		judge.aimSample(1010.0, 0.9f, 0.5f)
		judge.finish()
		assertEquals(1_010_000, judge.score)
		assertEquals(ClearLamp.ALL_JUSTICE_CRITICAL, judge.clearLamp)
	}
}
