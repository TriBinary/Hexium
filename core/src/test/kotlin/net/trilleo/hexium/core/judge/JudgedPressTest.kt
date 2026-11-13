package net.trilleo.hexium.core.judge

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import net.trilleo.hexium.core.chart.Beat
import net.trilleo.hexium.core.chart.BpmChange
import net.trilleo.hexium.core.chart.Difficulty
import net.trilleo.hexium.core.chart.DifficultyChart
import net.trilleo.hexium.core.chart.Note

class JudgedPressTest {
	private val chart = DifficultyChart(
		1,
		Difficulty.BASIC,
		1,
		4,
		0.0,
		listOf(BpmChange(Beat(0, 0, 1), 60.0)),
		emptyList(),
		listOf(Note.Tap(Beat(1, 0, 1), 0), Note.Hold(Beat(2, 0, 1), Beat(3, 0, 1), 1)),
	)

	@Test
	fun tellsPressesFromHoldTicks() {
		val judge = Judge(JudgeChart.compile(chart))
		judge.pressLane(0, 1000.0)
		judge.releaseLane(0, 1010.0)
		judge.pressLane(1, 2000.0)
		judge.finish()
		assertEquals(5, judge.judgedCount)
		assertTrue(judge.judgedIsPress(0), "the tap")
		assertTrue(judge.judgedIsPress(1), "the hold's head")
		for (i in 2 until judge.judgedCount) assertFalse(judge.judgedIsPress(i), "tick $i")
	}
}
