package net.trilleo.hexium.core.chart

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class TimingMapTest {
	private val tolerance = 1e-9

	private fun bpm(whole: Int, bpm: Double) = BpmChange(Beat(whole, 0, 1), bpm)

	@Test
	fun beatZeroFallsOnTheOffset() {
		assertEquals(120.0, TimingMap(120.0, listOf(bpm(0, 174.0))).beatToMs(Beat(0, 0, 1)), tolerance)
	}

	@Test
	fun negativeOffsetPutsBeatZeroBeforeTheAudio() {
		assertEquals(-250.0, TimingMap(-250.0, listOf(bpm(0, 120.0))).beatToMs(Beat(0, 0, 1)), tolerance)
		assertEquals(250.0, TimingMap(-250.0, listOf(bpm(0, 120.0))).beatToMs(Beat(1, 0, 1)), tolerance)
	}

	@Test
	fun constantBpm() {
		val map = TimingMap(0.0, listOf(bpm(0, 120.0)))
		assertEquals(500.0, map.beatToMs(Beat(1, 0, 1)), tolerance)
		assertEquals(6375.0, map.beatToMs(Beat(12, 3, 4)), tolerance)
	}

	@Test
	fun thirdsOfABeatDoNotDrift() {
		val map = TimingMap(0.0, listOf(bpm(0, 120.0)))
		assertEquals(1000.0 * 1000.0 / 2.0 + 500.0 / 3.0, map.beatToMs(Beat(1000, 1, 3)), tolerance)
	}

	@Test
	fun bpmChangeUsesTheNewTempoFromItsBeat() {
		val map = TimingMap(120.0, listOf(bpm(0, 174.0), bpm(64, 87.0)))
		val atChange = 120.0 + 64 * 60000.0 / 174.0
		assertEquals(atChange - 60000.0 / 174.0, map.beatToMs(Beat(63, 0, 1)), tolerance)
		assertEquals(atChange, map.beatToMs(Beat(64, 0, 1)), tolerance)
		assertEquals(atChange + 60000.0 / 87.0, map.beatToMs(Beat(65, 0, 1)), tolerance)
	}

	@Test
	fun bpmChangeOnAFraction() {
		val map = TimingMap(0.0, listOf(bpm(0, 120.0), BpmChange(Beat(2, 1, 2), 60.0)))
		assertEquals(1250.0, map.beatToMs(Beat(2, 1, 2)), tolerance)
		assertEquals(1750.0, map.beatToMs(Beat(3, 0, 1)), tolerance)
	}

	@Test
	fun manyChangesAddUp() {
		val map = TimingMap(0.0, listOf(bpm(0, 120.0), bpm(1, 60.0), bpm(2, 240.0), bpm(3, 120.0)))
		assertEquals(500.0, map.beatToMs(Beat(1, 0, 1)), tolerance)
		assertEquals(1500.0, map.beatToMs(Beat(2, 0, 1)), tolerance)
		assertEquals(1750.0, map.beatToMs(Beat(3, 0, 1)), tolerance)
		assertEquals(2250.0, map.beatToMs(Beat(4, 0, 1)), tolerance)
	}

	@Test
	fun lookupsFindTheRightSegmentInALongList() {
		val changes = List(1001) { bpm(it, 120.0) }
		val map = TimingMap(0.0, changes)
		for (whole in listOf(0, 1, 2, 499, 500, 501, 999, 1000, 1001, 5000)) {
			assertEquals(whole * 500.0, map.beatToMs(Beat(whole, 0, 1)), tolerance)
			assertEquals(whole * 500.0 + 250.0, map.beatToMs(Beat(whole, 1, 2)), tolerance)
		}
	}

	@Test
	fun beatsBeforeZeroContinueTheFirstTempo() {
		val map = TimingMap(1000.0, listOf(bpm(0, 120.0), bpm(4, 60.0)))
		assertEquals(500.0, map.beatToMs(-1.0), tolerance)
	}

	@Test
	fun msToBeatUndoesBeatToMs() {
		val map = TimingMap(120.0, listOf(bpm(0, 174.0), bpm(64, 87.0), BpmChange(Beat(70, 1, 3), 200.0)))
		for (beat in listOf(0.0, 0.5, 63.9, 64.0, 65.25, 70.0, 70.5, 500.0, -2.0)) {
			assertEquals(beat, map.msToBeat(map.beatToMs(beat)), 1e-9)
		}
	}

		@Test
	fun doubleBeatMatchesExactBeat() {
		val map = TimingMap(120.0, listOf(bpm(0, 174.0), bpm(64, 87.0)))
		assertEquals(map.beatToMs(Beat(65, 1, 2)), map.beatToMs(65.5), tolerance)
	}

	@Test
	fun refusesBpmDataTheValidatorWouldReject() {
		assertFailsWith<IllegalArgumentException> { TimingMap(0.0, emptyList()) }
		assertFailsWith<IllegalArgumentException> { TimingMap(0.0, listOf(bpm(1, 120.0))) }
		assertFailsWith<IllegalArgumentException> { TimingMap(0.0, listOf(bpm(0, 0.0))) }
		assertFailsWith<IllegalArgumentException> { TimingMap(0.0, listOf(bpm(0, 120.0), bpm(0, 90.0))) }
		assertFailsWith<IllegalArgumentException> { TimingMap(0.0, listOf(BpmChange(Beat(0, 0, 0), 120.0))) }
		assertFailsWith<IllegalArgumentException> { TimingMap(0.0, listOf(bpm(0, 1e-320))) }
		assertFailsWith<IllegalArgumentException> { TimingMap(0.0, listOf(bpm(0, Double.POSITIVE_INFINITY))) }
		assertFailsWith<IllegalArgumentException> { TimingMap(Double.NaN, listOf(bpm(0, 120.0))) }
	}
}
