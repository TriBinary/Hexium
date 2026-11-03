package net.trilleo.hexium.core.judge

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import net.trilleo.hexium.core.chart.Note
import net.trilleo.hexium.core.chart.SkyButton

class JudgeFlickTest {
	/** At time 0, so window limits are exact. */
	private fun flick(whole: Int = 0, x: Double = 0.5, y: Double = 0.5, dir: Double? = null, size: Double = 1.0) =
		Note.Flick(beat(whole), x, y, dir, size)

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
		assertEquals(miss, swipeFrom(-166.68))
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
	private fun swipeAt(degrees: Double, requiredDir: Double?): Int {
		val judge = judgeOf(flick(dir = requiredDir))
		val radians = Math.toRadians(degrees)
		val dx = Math.cos(radians) * 0.2 / 2.0
		val dy = Math.sin(radians) * 0.2
		judge.aimSample(-5.0, (0.5 - dx).toFloat(), (0.5 - dy).toFloat())
		judge.aimSample(5.0, (0.5 + dx).toFloat(), (0.5 + dy).toFloat())
		return judge.judgedCount
	}

	@Test
	fun leavingOutTheDirectionAcceptsAny() {
		for (degrees in listOf(0.0, 90.0, 180.0, 270.0, 33.0)) assertEquals(1, swipeAt(degrees, null))
	}

	@Test
	fun theDirectionIsMeasuredWith0RightAnd90Up() {
		assertEquals(1, swipeAt(0.0, 0.0))
		assertEquals(1, swipeAt(90.0, 90.0))
		assertEquals(1, swipeAt(180.0, 180.0))
		assertEquals(1, swipeAt(270.0, 270.0))
		assertEquals(0, swipeAt(180.0, 0.0))
		assertEquals(0, swipeAt(270.0, 90.0))
	}

	@Test
	fun theDirectionHas45DegreesOfTolerance() {
		assertEquals(1, swipeAt(134.0, 90.0))
		assertEquals(1, swipeAt(46.0, 90.0))
		assertEquals(0, swipeAt(136.0, 90.0))
		assertEquals(0, swipeAt(44.0, 90.0))
	}

	@Test
	fun theToleranceWrapsAround360() {
		assertEquals(1, swipeAt(10.0, 350.0))
		assertEquals(1, swipeAt(350.0, 10.0))
		assertEquals(0, swipeAt(60.0, 350.0))
	}

	@Test
	fun wrongDirectionCanBeFollowedByARightOne() {
		val judge = judgeOf(flick(dir = 0.0))
		judge.aimSample(-30.0, 0.7f, 0.5f)
		judge.aimSample(-20.0, 0.3f, 0.5f)
		assertEquals(0, judge.judgedCount)
		judge.aimSample(-10.0, 0.7f, 0.5f)
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
