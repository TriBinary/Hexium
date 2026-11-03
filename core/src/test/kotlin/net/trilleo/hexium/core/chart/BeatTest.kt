package net.trilleo.hexium.core.chart

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BeatTest {
	@Test
	fun fractionsWithoutReducingAreTheSameTime() {
		assertEquals(0, Beat(12, 2, 4).compareTo(Beat(12, 1, 2)))
	}

	@Test
	fun ordersByWholeThenFraction() {
		assertTrue(Beat(1, 3, 4) < Beat(2, 0, 1))
		assertTrue(Beat(2, 1, 3) < Beat(2, 1, 2))
		assertTrue(Beat(2, 1, 2) > Beat(2, 1, 3))
	}

	@Test
	fun comparesHugeDenominatorsWithoutOverflow() {
		val max = Int.MAX_VALUE
		assertTrue(Beat(0, max - 2, max - 1) < Beat(0, max - 1, max))
		assertTrue(Beat(max, 1, max) < Beat(max, 2, max))
	}

	@Test
	fun convertsToDouble() {
		assertEquals(12.75, Beat(12, 3, 4).toDouble())
		assertEquals(0.0, Beat(0, 0, 1).toDouble())
	}

	@Test
	fun acceptsValidFractions() {
		assertTrue(Beat(0, 0, 1).isValid)
		assertTrue(Beat(12, 3, 4).isValid)
		assertTrue(Beat(12, 2, 4).isValid)
	}

	@Test
	fun rejectsInvalidFractions() {
		assertFalse(Beat(12, 5, 4).isValid, "numerator above denominator")
		assertFalse(Beat(12, 4, 4).isValid, "numerator equal to denominator")
		assertFalse(Beat(-1, 0, 1).isValid, "negative whole")
		assertFalse(Beat(1, -1, 4).isValid, "negative numerator")
		assertFalse(Beat(1, 0, 0).isValid, "zero denominator")
		assertFalse(Beat(1, 0, -4).isValid, "negative denominator")
	}
}
