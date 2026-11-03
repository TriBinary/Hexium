package net.trilleo.hexium.core.judge

import java.lang.management.ManagementFactory
import kotlin.test.Test
import kotlin.test.assertTrue
import net.trilleo.hexium.core.chart.Ease
import net.trilleo.hexium.core.chart.Note
import net.trilleo.hexium.core.chart.SkyButton
import net.trilleo.hexium.core.chart.SlidePoint

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

	@Test
	fun playingSkyNotesDoesNotAllocate() {
		val notes = Array<Note>(30_000) {
			when (it % 3) {
				0 -> Note.SkyTap(beat(it + 1), 0.5, 0.5, SkyButton.L, 1.0)
				1 -> Note.Flick(beat(it + 1), 0.5, 0.5, 90.0, 1.0)
				else -> Note.Slide(
					SkyButton.R,
					0.1,
					listOf(SlidePoint(beat(it + 1), 0.5, 0.5, Ease.IN_OUT), SlidePoint(beat(it + 1, 1, 2), 0.5, 0.5, Ease.LINEAR)),
				)
			}
		}
		val judge = Judge(JudgeChart.compile(chartOf(*notes)))
		val thread = Thread.currentThread().threadId()
		val before = threads.getThreadAllocatedBytes(thread)
		var i = 0
		while (i < notes.size) {
			val time = (i + 1) * 1000.0
			judge.aimSample(time - 10.0, 0.5f, 0.4f)
			judge.aimSample(time, 0.5f, 0.6f)
			if (i % 3 == 0) {
				judge.pressButton(SkyButton.L, time, 0.5f, 0.5f)
				judge.releaseButton(SkyButton.L, time + 5.0)
			} else if (i % 3 == 2) {
				judge.pressButton(SkyButton.R, time, 0.5f, 0.5f)
				judge.aimSample(time + 300.0, 0.5f, 0.5f)
				judge.releaseButton(SkyButton.R, time + 600.0)
			}
			judge.advanceTo(time + 700.0)
			i++
		}
		judge.finish()
		val allocated = threads.getThreadAllocatedBytes(thread) - before
		assertTrue(allocated < 64 * 1024, "allocated $allocated bytes over ${notes.size} notes")
		assertTrue(judge.judgedCount == judge.objectCount)
	}
}
