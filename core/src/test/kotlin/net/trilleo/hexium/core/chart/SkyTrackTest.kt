package net.trilleo.hexium.core.chart

import java.io.File
import java.lang.management.ManagementFactory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import net.trilleo.hexium.core.judge.JudgeChart
import net.trilleo.hexium.core.judge.JudgementConfig

class SkyTrackTest {
	private val tolerance = 1e-9

	private fun beat(whole: Int, numerator: Int = 0, denominator: Int = 1) = Beat(whole, numerator, denominator)

	private fun chart(vararg notes: Note, offsetMs: Double = 0.0, bpm: List<BpmChange> = listOf(BpmChange(beat(0), 120.0))) =
		DifficultyChart(1, Difficulty.EXPERT, 1, 4, offsetMs, bpm, listOf(MeterChange(beat(0), 4, 4)), notes.toList())

	/** At 120 BPM a beat is 500 ms. */
	private fun track(vararg notes: Note, offsetMs: Double = 0.0) = SkyTrack(chart(*notes, offsetMs = offsetMs))

	private fun slide(button: SkyButton, radius: Double, vararg points: SlidePoint) = Note.Slide(button, radius, points.toList())

	private fun point(t: Beat, x: Double, y: Double, ease: Ease = Ease.LINEAR) = SlidePoint(t, x, y, ease)

	@Test
	fun aSkyTapSitsOnItsSpotAtItsTime() {
		val track = track(Note.SkyTap(beat(2), 0.25, 0.75, SkyButton.R, 1.5), offsetMs = 100.0)
		assertEquals(1, track.size)
		assertEquals(SkyTrack.Kind.TAP, track.kind(0))
		assertEquals(SkyButton.R, track.button(0))
		assertEquals(1100.0, track.startMs(0), tolerance)
		assertEquals(1100.0, track.endMs(0), tolerance)
		assertEquals(0.25, track.x(0, 0.0), tolerance)
		assertEquals(0.75, track.y(0, 5000.0), tolerance)
	}

	/** The judge's hit circle, so what's drawn is what counts. */
	@Test
	fun aSkyTapsRadiusIsTheBaseHitRadiusTimesItsSize() {
		val track = track(Note.SkyTap(beat(2), 0.5, 0.5, SkyButton.L, 1.5))
		assertEquals(JudgementConfig.DEFAULT.baseHitRadius * 1.5, track.radius(0), tolerance)
	}

	@Test
	fun theRadiusFollowsTheConfigItIsGiven() {
		val track = SkyTrack(chart(Note.Flick(beat(1), 0.5, 0.5, 2.0)), JudgementConfig(baseHitRadius = 0.1))
		assertEquals(0.2, track.radius(0), tolerance)
	}

	@Test
	fun aFlickNeedsNoButton() {
		val track = track(Note.Flick(beat(4), 0.3, 0.4, 1.0))
		assertEquals(SkyTrack.Kind.FLICK, track.kind(0))
		assertNull(track.button(0))
		assertEquals(2000.0, track.startMs(0), tolerance)
		assertEquals(2000.0, track.endMs(0), tolerance)
		assertEquals(0.3, track.x(0, 2000.0), tolerance)
		assertEquals(0.4, track.y(0, 2000.0), tolerance)
	}

	@Test
	fun aSlideRunsFromItsFirstPointToItsLastWithItsOwnRadius() {
		val track = track(slide(SkyButton.L, 0.06, point(beat(2), 0.2, 0.5), point(beat(4), 0.6, 0.9), point(beat(6), 0.6, 0.1)))
		assertEquals(SkyTrack.Kind.SLIDE, track.kind(0))
		assertEquals(SkyButton.L, track.button(0))
		assertEquals(1000.0, track.startMs(0), tolerance)
		assertEquals(3000.0, track.endMs(0), tolerance)
		assertEquals(0.06, track.radius(0), tolerance)
	}

	@Test
	fun aSlideIsWhereItsPathIsAtThatTime() {
		val track = track(slide(SkyButton.L, 0.06, point(beat(2), 0.2, 0.5), point(beat(4), 0.6, 0.9, Ease.IN), point(beat(6), 0.6, 0.1)))
		// Halfway along a linear segment.
		assertEquals(0.4, track.x(0, 1500.0), tolerance)
		assertEquals(0.7, track.y(0, 1500.0), tolerance)
		// Halfway along an `in` segment has gone a quarter of the way.
		assertEquals(0.7, track.y(0, 2500.0), tolerance)
	}

	@Test
	fun aSlideStaysOnItsEndsBeforeAndAfterItsPath() {
		val track = track(slide(SkyButton.R, 0.06, point(beat(2), 0.2, 0.5), point(beat(4), 0.6, 0.9)))
		assertEquals(0.2, track.x(0, -5000.0), tolerance)
		assertEquals(0.5, track.y(0, 999.0), tolerance)
		assertEquals(0.6, track.x(0, 2001.0), tolerance)
		assertEquals(0.9, track.y(0, 99999.0), tolerance)
	}

	/** Eases are measured in beats, so a BPM change part way along doesn't bend the path. */
	@Test
	fun aSlideAcrossABpmChangeMovesEvenlyInBeats() {
		val bpm = listOf(BpmChange(beat(0), 120.0), BpmChange(beat(3), 60.0))
		val track = SkyTrack(chart(slide(SkyButton.L, 0.06, point(beat(2), 0.0, 0.0), point(beat(4), 1.0, 1.0)), bpm = bpm))
		// Beat 3 is at 1500 ms, the middle of the path in beats.
		assertEquals(0.5, track.x(0, 1500.0), tolerance)
		// Beat 3.5 is 500 ms after it at 60 BPM.
		assertEquals(0.75, track.x(0, 2000.0), tolerance)
	}

	@Test
	fun notesAreInTimeOrderAndRememberTheirPlaceInTheChart() {
		val track = track(
			Note.Flick(beat(3), 0.5, 0.5, 1.0),
			Note.Tap(beat(1), 0),
			Note.SkyTap(beat(1), 0.5, 0.5, SkyButton.L, 1.0),
			slide(SkyButton.R, 0.06, point(beat(2), 0.5, 0.5), point(beat(5), 0.5, 0.5)),
			Note.Hold(beat(2), beat(3), 1),
		)
		assertEquals(3, track.size)
		assertEquals(listOf(500.0, 1000.0, 1500.0), List(track.size) { track.startMs(it) })
		assertEquals(listOf(2, 3, 0), List(track.size) { track.chartNote(it) })
		assertEquals(listOf(SkyTrack.Kind.TAP, SkyTrack.Kind.SLIDE, SkyTrack.Kind.FLICK), List(track.size) { track.kind(it) })
	}

	@Test
	fun notesAtTheSameTimeKeepTheChartsOrder() {
		val track = track(Note.SkyTap(beat(1), 0.2, 0.5, SkyButton.L, 1.0), Note.SkyTap(beat(1), 0.8, 0.5, SkyButton.R, 1.0))
		assertEquals(listOf(0, 1), List(track.size) { track.chartNote(it) })
	}

	@Test
	fun theFirstNoteStillGoingIsFoundFromAnyTime() {
		val track = track(
			Note.SkyTap(beat(1), 0.5, 0.5, SkyButton.L, 1.0),
			Note.SkyTap(beat(2), 0.5, 0.5, SkyButton.L, 1.0),
			Note.Flick(beat(3), 0.5, 0.5, 1.0),
		)
		assertEquals(0, track.firstEndingFrom(-5000.0))
		assertEquals(0, track.firstEndingFrom(500.0))
		assertEquals(1, track.firstEndingFrom(500.5))
		assertEquals(2, track.firstEndingFrom(1500.0))
		assertEquals(3, track.firstEndingFrom(1500.5))
	}

	@Test
	fun aLongSlideKeepsTheSearchFromSkippingIt() {
		val track = track(
			slide(SkyButton.L, 0.06, point(beat(1), 0.5, 0.5), point(beat(100), 0.5, 0.5)),
			Note.SkyTap(beat(2), 0.5, 0.5, SkyButton.L, 1.0),
			Note.SkyTap(beat(3), 0.5, 0.5, SkyButton.L, 1.0),
		)
		assertEquals(0, track.firstEndingFrom(2000.0))
		assertEquals(0, track.firstEndingFrom(50000.0))
		assertEquals(3, track.firstEndingFrom(50000.5))
	}

	@Test
	fun theAspectComesFromTheConfig() {
		assertEquals(JudgementConfig.DEFAULT.screenAspect, track().screenAspect, tolerance)
		assertEquals(3.0, SkyTrack(chart(), JudgementConfig(screenAspect = 3.0)).screenAspect, tolerance)
	}

	/** What's drawn must be what's judged: every time, spot, radius and path matches the judge's own chart. */
	@Test
	fun everyNoteMatchesWhatTheJudgeUses() {
		val files = listOf(File("../docs/examples/expert.json"), File("../mod/src/client/resources/hexium_packs/test-pattern/expert.json"))
		for (file in files) {
			val chart = assertNotNull(ChartParser.parseDifficulty(file.readText()).value, file.path)
			val judge = JudgeChart.compile(chart)
			val sky = SkyTrack(chart)
			var slide = 0
			var checked = 0
			for (i in 0 until sky.size) {
				val note = sky.chartNote(i)
				when (sky.kind(i)) {
					SkyTrack.Kind.TAP -> {
						val b = sky.button(i)!!.ordinal
						val k = judge.skyObject[b].indexOfFirst { judge.objectNote[it] == note }
						assertTrue(k >= 0, "note $note in ${file.path}")
						assertEquals(judge.skyMs[b][k], sky.startMs(i), tolerance)
						assertEquals(judge.skyX[b][k], sky.x(i, 0.0), tolerance)
						assertEquals(judge.skyY[b][k], sky.y(i, 0.0), tolerance)
						assertEquals(judge.skyRadius[b][k], sky.radius(i), tolerance)
					}
					SkyTrack.Kind.FLICK -> {
						val k = judge.flickObject.indexOfFirst { judge.objectNote[it] == note }
						assertTrue(k >= 0, "note $note in ${file.path}")
						assertEquals(judge.flickMs[k], sky.startMs(i), tolerance)
						assertEquals(judge.flickX[k], sky.x(i, 0.0), tolerance)
						assertEquals(judge.flickY[k], sky.y(i, 0.0), tolerance)
						assertEquals(judge.flickRadius[k], sky.radius(i), tolerance)
					}
					SkyTrack.Kind.SLIDE -> {
						// Both keep slides in start order.
						assertEquals(judge.slideStartMs[slide], sky.startMs(i), tolerance)
						assertEquals(judge.slideEndMs[slide], sky.endMs(i), tolerance)
						assertEquals(judge.slideRadius[slide], sky.radius(i), tolerance)
						assertEquals(judge.slideButton[slide], sky.button(i)!!.ordinal)
						for (step in 0..8) {
							val ms = sky.startMs(i) + (sky.endMs(i) - sky.startMs(i)) * step / 8.0
							val beat = judge.timing.msToBeat(ms)
							assertEquals(judge.slidePath[slide].x(beat), sky.x(i, ms), tolerance)
							assertEquals(judge.slidePath[slide].y(beat), sky.y(i, ms), tolerance)
						}
						slide++
					}
				}
				checked++
			}
			assertEquals(judge.slideCount, slide, file.path)
			assertTrue(checked >= 7, file.path)
		}
	}

	@Test
	fun aGroundChartHasNothingInTheSky() {
		val track = track(Note.Tap(beat(1), 0), Note.Hold(beat(2), beat(3), 1))
		assertEquals(0, track.size)
		assertEquals(0, track.firstEndingFrom(0.0))
	}

	@Test
	fun lookupsDoNotAllocate() {
		val track = track(
			Note.SkyTap(beat(1), 0.5, 0.5, SkyButton.L, 1.0),
			slide(SkyButton.R, 0.06, point(beat(2), 0.2, 0.5), point(beat(4), 0.6, 0.9, Ease.IN_OUT), point(beat(6), 0.6, 0.1)),
			Note.Flick(beat(7), 0.5, 0.5, 1.0),
		)
		val threads = ManagementFactory.getThreadMXBean() as com.sun.management.ThreadMXBean
		val thread = Thread.currentThread().threadId()
		var sum = 0.0
		val before = threads.getThreadAllocatedBytes(thread)
		for (i in 0 until 100_000) {
			val ms = (i % 4000).toDouble()
			var n = track.firstEndingFrom(ms)
			while (n < track.size) {
				sum += track.x(n, ms) + track.y(n, ms) + track.radius(n) + track.startMs(n) + track.endMs(n)
				if (track.button(n) == SkyButton.L) sum += track.chartNote(n)
				n++
			}
		}
		val allocated = threads.getThreadAllocatedBytes(thread) - before
		assertTrue(sum.isFinite())
		assertTrue(allocated < 16 * 1024, "allocated $allocated bytes")
	}
}
