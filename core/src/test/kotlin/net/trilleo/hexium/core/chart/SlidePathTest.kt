package net.trilleo.hexium.core.chart

import kotlin.test.Test
import kotlin.test.assertEquals

class SlidePathTest {
	private val tolerance = 1e-9

	@Test
	fun easesFollowTheSpec() {
		for (t in listOf(0.0, 0.25, 0.5, 0.75, 1.0)) {
			assertEquals(t, Ease.LINEAR.progress(t), tolerance)
			assertEquals(t * t, Ease.IN.progress(t), tolerance)
			assertEquals(1 - (1 - t) * (1 - t), Ease.OUT.progress(t), tolerance)
			assertEquals(3 * t * t - 2 * t * t * t, Ease.IN_OUT.progress(t), tolerance)
		}
	}

	private fun beat(whole: Int, num: Int = 0, den: Int = 1) = Beat(whole, num, den)

	private val path = SlidePath(
		listOf(
			SlidePoint(beat(1), 0.2, 0.4, Ease.LINEAR),
			SlidePoint(beat(2), 0.6, 0.8, Ease.IN),
			SlidePoint(beat(4), 1.0, 0.0, Ease.OUT),
		),
	)

	@Test
	fun positionFollowsEachSegmentsEase() {
		assertEquals(0.4, path.x(1.5), tolerance)
		assertEquals(0.6, path.y(1.5), tolerance)
		assertEquals(0.6 + 0.4 * 0.25, path.x(3.0), tolerance)
		assertEquals(0.8 - 0.8 * 0.25, path.y(3.0), tolerance)
	}

	@Test
	fun pointsAreExact() {
		assertEquals(0.2, path.x(1.0), tolerance)
		assertEquals(0.6, path.x(2.0), tolerance)
		assertEquals(1.0, path.x(4.0), tolerance)
	}

	@Test
	fun positionStopsAtBothEnds() {
		assertEquals(0.2, path.x(0.0), tolerance)
		assertEquals(0.4, path.y(-5.0), tolerance)
		assertEquals(1.0, path.x(9.0), tolerance)
		assertEquals(0.0, path.y(9.0), tolerance)
	}

	@Test
	fun lastPointsEaseIsIgnored() {
		val a = SlidePath(listOf(SlidePoint(beat(0), 0.0, 0.0, Ease.LINEAR), SlidePoint(beat(1), 1.0, 1.0, Ease.IN)))
		val b = SlidePath(listOf(SlidePoint(beat(0), 0.0, 0.0, Ease.LINEAR), SlidePoint(beat(1), 1.0, 1.0, Ease.OUT)))
		assertEquals(a.x(0.5), b.x(0.5), tolerance)
	}

	@Test
	fun progressIsInBeatsSoABpmChangeDoesNotBendThePath() {
		val straight = SlidePath(listOf(SlidePoint(beat(0), 0.0, 0.0, Ease.LINEAR), SlidePoint(beat(2), 1.0, 1.0, Ease.LINEAR)))
		val timing = TimingMap(0.0, listOf(BpmChange(beat(0), 60.0), BpmChange(beat(1), 120.0)))
		assertEquals(0.5, straight.x(timing.msToBeat(1000.0)), tolerance)
		assertEquals(0.75, straight.x(timing.msToBeat(1250.0)), tolerance)
	}
}
