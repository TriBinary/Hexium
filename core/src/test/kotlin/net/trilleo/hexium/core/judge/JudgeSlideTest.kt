package net.trilleo.hexium.core.judge

import kotlin.test.Test
import kotlin.test.assertEquals
import net.trilleo.hexium.core.chart.Ease
import net.trilleo.hexium.core.chart.Note
import net.trilleo.hexium.core.chart.SkyButton
import net.trilleo.hexium.core.chart.SlidePoint

class JudgeSlideTest {
	/** Beat 1 to 2 (1000 to 2000 ms), straight across the middle, with ticks at 1250, 1500 and 1750 ms. */
	private val slide = Note.Slide(
		SkyButton.L,
		0.06,
		listOf(SlidePoint(beat(1), 0.2, 0.5, Ease.LINEAR), SlidePoint(beat(2), 0.8, 0.5, Ease.LINEAR)),
	)

	private fun onPath(ms: Double) = (0.2 + 0.6 * (ms - 1000.0) / 1000.0).toFloat()

	private fun Judge.follow(fromMs: Double, toMs: Double, stepMs: Double = 8.0, offsetY: Float = 0f) {
		var time = fromMs
		while (time <= toMs) {
			aimSample(time, onPath(time), 0.5f + offsetY)
			time += stepMs
		}
	}

	private val allJc = List(3) { Judgement.JUSTICE_CRITICAL }

	@Test
	fun ticksOnlyWithNoHeadOrTail() {
		assertEquals(3, judgeOf(slide).objectCount)
		val judge = judgeOf(slide)
		judge.pressButton(SkyButton.L, 1000.0, onPath(1000.0), 0.5f)
		assertEquals(0, judge.judgedCount, "the click isn't a head")
		judge.follow(1000.0, 2000.0)
		judge.finish()
		assertEquals(allJc, judge.judgements())
	}

	@Test
	fun slideTooShortForATickGetsOneAtItsEnd() {
		val short = Note.Slide(
			SkyButton.L,
			0.06,
			listOf(SlidePoint(beat(1), 0.2, 0.5, Ease.LINEAR), SlidePoint(beat(1, 1, 8), 0.3, 0.5, Ease.LINEAR)),
		)
		val chart = JudgeChart.compile(chartOf(short))
		assertEquals(1, chart.objectCount)
		val judge = Judge(chart)
		judge.advanceTo(1125.0)
		assertEquals(0, judge.judgedCount)
		judge.advanceTo(1125.01)
		assertEquals(1, judge.judgedCount)
	}

	@Test
	fun neverClickedMissesEveryTick() {
		val judge = judgeOf(slide)
		judge.follow(1000.0, 2000.0)
		judge.finish()
		assertEquals(List(3) { Judgement.MISS }, judge.judgements())
	}

	@Test
	fun wrongButtonMissesEveryTick() {
		val judge = judgeOf(slide)
		judge.pressButton(SkyButton.R, 1000.0, onPath(1000.0), 0.5f)
		judge.follow(1000.0, 2000.0)
		judge.finish()
		assertEquals(List(3) { Judgement.MISS }, judge.judgements())
	}

	@Test
	fun crosshairOutsideTheRadiusMissesThoseTicks() {
		val judge = judgeOf(slide)
		judge.pressButton(SkyButton.L, 1000.0, onPath(1000.0), 0.5f)
		judge.follow(1000.0, 2000.0, offsetY = 0.07f)
		judge.finish()
		assertEquals(List(3) { Judgement.MISS }, judge.judgements())
	}

	@Test
	fun crosshairInsideTheRadiusButOffCentreStillCounts() {
		val judge = judgeOf(slide)
		judge.pressButton(SkyButton.L, 1000.0, onPath(1000.0), 0.5f)
		judge.follow(1000.0, 2000.0, offsetY = 0.05f)
		judge.finish()
		assertEquals(allJc, judge.judgements())
	}

	@Test
	fun radiusIsAFractionOfTheHeightOnBothAxes() {
		fun tick(offsetX: Float): Judgement {
			val judge = judgeOf(slide)
			judge.pressButton(SkyButton.L, 1000.0, onPath(1000.0), 0.5f)
			var time = 1000.0
			while (time <= 2000.0) {
				judge.aimSample(time, onPath(time) + offsetX, 0.5f)
				time += 8.0
			}
			judge.finish()
			return judge.judgement(0)
		}
		assertEquals(Judgement.JUSTICE_CRITICAL, tick(0.029f), "x is half as far because the screen is 2:1")
		assertEquals(Judgement.MISS, tick(0.031f))
	}

	/** Follows the path, then stops being held [age] ms before the 1500 ms tick. */
	private fun middleTickAfterRelease(age: Double): Judgement {
		val judge = judgeOf(slide)
		judge.pressButton(SkyButton.L, 1000.0, onPath(1000.0), 0.5f)
		judge.follow(1000.0, 1250.0)
		judge.aimSample(1500.0 - age, onPath(1500.0 - age), 0.5f)
		judge.releaseButton(SkyButton.L, 1500.0 - age)
		judge.aimSample(1500.0, onPath(1500.0), 0.5f)
		judge.advanceTo(1500.01)
		return judge.judgement(1)
	}

	@Test
	fun tickWindowsAreOnTheLastHeldSample() {
		assertEquals(Judgement.JUSTICE_CRITICAL, middleTickAfterRelease(50.0))
		assertEquals(Judgement.JUSTICE, middleTickAfterRelease(50.5))
		assertEquals(Judgement.JUSTICE, middleTickAfterRelease(100.0))
		assertEquals(Judgement.ATTACK, middleTickAfterRelease(100.5))
		assertEquals(Judgement.ATTACK, middleTickAfterRelease(150.0))
		assertEquals(Judgement.MISS, middleTickAfterRelease(150.5))
	}

	@Test
	fun leavingAndComingBackInsideTheWindowKeepsJusticeCritical() {
		val judge = judgeOf(slide)
		judge.pressButton(SkyButton.L, 1000.0, onPath(1000.0), 0.5f)
		judge.follow(1000.0, 1480.0)
		judge.aimSample(1490.0, onPath(1490.0), 0.9f)
		judge.aimSample(1510.0, onPath(1510.0), 0.5f)
		judge.follow(1518.0, 2000.0)
		judge.finish()
		assertEquals(allJc, judge.judgements())
	}

	@Test
	fun comingBackAfterATickDoesNotHelpIt() {
		val judge = judgeOf(slide)
		judge.pressButton(SkyButton.L, 1000.0, onPath(1000.0), 0.5f)
		judge.follow(1000.0, 1300.0, offsetY = 0.3f)
		judge.follow(1304.0, 2000.0)
		judge.finish()
		assertEquals(listOf(Judgement.MISS, Judgement.JUSTICE_CRITICAL, Judgement.JUSTICE_CRITICAL), judge.judgements())
	}

	@Test
	fun theClickItselfCountsAsAnAimSample() {
		val judge = judgeOf(slide)
		judge.pressButton(SkyButton.L, 1250.0, onPath(1250.0), 0.5f)
		judge.advanceTo(1250.01)
		assertEquals(listOf(Judgement.JUSTICE_CRITICAL), judge.judgements())
	}

	@Test
	fun theCrosshairMustFollowAnEasedPath() {
		val eased = Note.Slide(
			SkyButton.L,
			0.03,
			listOf(SlidePoint(beat(1), 0.0, 0.5, Ease.IN), SlidePoint(beat(2), 0.0, 1.0, Ease.LINEAR)),
		)
		fun tick(y: Float): Judgement {
			val judge = judgeOf(eased)
			judge.pressButton(SkyButton.L, 1500.0, 0.0f, y)
			judge.advanceTo(1500.01)
			return judge.judgement(1)
		}
		assertEquals(Judgement.JUSTICE_CRITICAL, tick(0.625f), "eased in, the path is at 0.25 of the way at the halfway tick")
		assertEquals(Judgement.MISS, tick(0.75f), "a linear path would be here")
	}

	@Test
	fun positionsBeforeTheFirstPointUseTheFirstPoint() {
		val judge = judgeOf(
			Note.Slide(
				SkyButton.L,
				0.06,
				listOf(SlidePoint(beat(1), 0.2, 0.5, Ease.LINEAR), SlidePoint(beat(1, 1, 8), 0.2, 0.5, Ease.LINEAR)),
			),
		)
		judge.pressButton(SkyButton.L, 990.0, 0.2f, 0.5f)
		judge.releaseButton(SkyButton.L, 1000.0)
		judge.advanceTo(1125.01)
		assertEquals(listOf(Judgement.ATTACK), judge.judgements(), "the click was on the first point, 135 ms before the tick")
	}

	@Test
	fun clickingASkyTapAlsoHoldsASlide() {
		val judge = judgeOf(slide, Note.SkyTap(beat(1), 0.2, 0.5, SkyButton.L, 1.0))
		judge.pressButton(SkyButton.L, 1000.0, onPath(1000.0), 0.5f)
		assertEquals(1, judge.judgedCount)
		judge.follow(1000.0, 2000.0)
		judge.finish()
		assertEquals(4, judge.judgedCount)
		assertEquals(0, judge.count(Judgement.MISS))
	}

	@Test
	fun twoSlidesAreJudgedSeparately() {
		val second = slide.copy(button = SkyButton.R, points = slide.points.map { it.copy(y = 0.9) })
		val judge = judgeOf(slide, second)
		judge.pressButton(SkyButton.L, 1000.0, onPath(1000.0), 0.5f)
		judge.pressButton(SkyButton.R, 1000.0, onPath(1000.0), 0.5f)
		judge.follow(1000.0, 2000.0)
		judge.finish()
		assertEquals(3, judge.count(Judgement.JUSTICE_CRITICAL))
		assertEquals(3, judge.count(Judgement.MISS))
	}

	@Test
	fun lowFrameRatesStillHoldASlide() {
		for (step in listOf(1000.0 / 30, 1000.0 / 60, 1000.0 / 144, 1000.0 / 360)) {
			val judge = judgeOf(slide)
			judge.pressButton(SkyButton.L, 1000.0, onPath(1000.0), 0.5f)
			judge.follow(1000.0, 2000.0, stepMs = step)
			judge.finish()
			assertEquals(allJc, judge.judgements(), "step $step")
		}
	}
}
