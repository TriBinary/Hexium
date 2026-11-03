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
	}
}
