package net.trilleo.hexium.core

import kotlin.test.Test
import kotlin.test.assertEquals

class HexiumCoreTest {
	@Test
	fun chartFormatVersionIsOne() {
		assertEquals(1, HexiumCore.CHART_FORMAT_VERSION)
	}
}
