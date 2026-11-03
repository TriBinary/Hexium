package net.trilleo.hexium.core.judge

import java.lang.management.ManagementFactory
import kotlin.test.Test
import kotlin.test.assertTrue
import net.trilleo.hexium.core.chart.Note

/** The judge runs every frame in the mod, so playing a chart must not allocate. */
class JudgeAllocationTest {
	private val threads = ManagementFactory.getThreadMXBean() as com.sun.management.ThreadMXBean

	@Test
	fun playingAChartDoesNotAllocate() {
		val notes = Array<Note>(40_000) {
			if (it % 2 == 0) Note.Tap(beat(it + 1), it % 4) else Note.Hold(beat(it + 1), beat(it + 1, 1, 2), it % 4)
		}
		val chart = JudgeChart.compile(chartOf(*notes))

		val judge = Judge(chart)
		val thread = Thread.currentThread().threadId()
		val before = threads.getThreadAllocatedBytes(thread)
		var i = 0
		while (i < notes.size) {
			val time = (i + 1) * 1000.0
			judge.advanceTo(time)
			judge.pressLane(i % 4, time)
			judge.advanceTo(time + 10.0)
			judge.releaseLane(i % 4, time + 600.0)
			judge.advanceTo(time + 700.0)
			i++
		}
		judge.finish()
		val allocated = threads.getThreadAllocatedBytes(thread) - before
		assertTrue(allocated < 64 * 1024, "allocated $allocated bytes over ${notes.size} notes")
		assertTrue(judge.judgedCount == judge.objectCount)
	}
}
