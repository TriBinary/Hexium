package net.trilleo.hexium.core.chart

import kotlin.test.Test
import kotlin.test.assertEquals

class GroundTrackTest {
	private val tolerance = 1e-9

	private fun beat(whole: Int) = Beat(whole, 0, 1)

	/** At 120 BPM a beat is 500 ms. */
	private fun track(vararg notes: Note, offsetMs: Double = 0.0) = GroundTrack(
		DifficultyChart(1, Difficulty.BASIC, 1, 4, offsetMs, listOf(BpmChange(beat(0), 120.0)), listOf(MeterChange(beat(0), 4, 4)), notes.toList()),
	)

	@Test
	fun aTapStartsAndEndsOnItsOwnTimeInItsOwnLane() {
		val track = track(Note.Tap(beat(2), 3), offsetMs = 100.0)
		assertEquals(1, track.size)
		assertEquals(3, track.lane(0))
		assertEquals(1100.0, track.startMs(0), tolerance)
		assertEquals(1100.0, track.endMs(0), tolerance)
		assertEquals(false, track.isHold(0))
	}

	@Test
	fun aHoldRunsFromItsStartToItsEnd() {
		val track = track(Note.Hold(beat(1), beat(5), 2))
		assertEquals(500.0, track.startMs(0), tolerance)
		assertEquals(2500.0, track.endMs(0), tolerance)
		assertEquals(true, track.isHold(0))
	}

	@Test
	fun notesAreInTimeOrderAndRememberTheirPlaceInTheChart() {
		val track = track(Note.Tap(beat(3), 0), Note.Tap(beat(1), 1), Note.Hold(beat(2), beat(4), 2))
		assertEquals(listOf(500.0, 1000.0, 1500.0), List(track.size) { track.startMs(it) })
		assertEquals(listOf(1, 2, 0), List(track.size) { track.chartNote(it) })
	}

	@Test
	fun skyNotesAreNotOnTheTrackButKeepTheCountOfTheChart() {
		val track = track(Note.SkyTap(beat(1), 0.5, 0.5, SkyButton.L, 1.0), Note.Tap(beat(2), 0))
		assertEquals(1, track.size)
		assertEquals(1, track.chartNote(0))
	}

	@Test
	fun theFirstNoteStillGoingIsFoundFromAnyTime() {
		val track = track(Note.Tap(beat(1), 0), Note.Tap(beat(2), 0), Note.Tap(beat(3), 0))
		assertEquals(0, track.firstEndingFrom(-5000.0))
		assertEquals(0, track.firstEndingFrom(500.0))
		assertEquals(1, track.firstEndingFrom(500.5))
		assertEquals(2, track.firstEndingFrom(1500.0))
		assertEquals(3, track.firstEndingFrom(1500.5))
	}

	@Test
	fun aLongHoldKeepsTheSearchFromSkippingIt() {
		val track = track(Note.Hold(beat(1), beat(100), 0), Note.Tap(beat(2), 1), Note.Tap(beat(3), 1))
		assertEquals(0, track.firstEndingFrom(2000.0))
		assertEquals(0, track.firstEndingFrom(50000.0))
		assertEquals(3, track.firstEndingFrom(50000.5))
	}

	@Test
	fun anEmptyTrackHasNothingToFind() {
		assertEquals(0, track().firstEndingFrom(0.0))
	}

	@Test
	fun aBpmChangeMovesTheNotesAfterIt() {
		val chart = DifficultyChart(
			1, Difficulty.BASIC, 1, 4, 0.0,
			listOf(BpmChange(beat(0), 120.0), BpmChange(beat(2), 60.0)),
			listOf(MeterChange(beat(0), 4, 4)),
			listOf(Note.Hold(beat(1), beat(3), 0)),
		)
		val track = GroundTrack(chart)
		assertEquals(500.0, track.startMs(0), tolerance)
		assertEquals(2000.0, track.endMs(0), tolerance)
	}

	@Test
	fun aPositiveVisualOffsetDrawsANoteLater() {
		val drawMs = GroundTrack.drawTimeMs(1000.0, 100.0)
		assertEquals(900.0, drawMs, tolerance)
		assertEquals(4.0, GroundTrack.distance(1000.0, drawMs, 40.0), tolerance)
	}

	@Test
	fun aNoteIsOnTheLineExactlyAtItsTime() {
		assertEquals(0.0, GroundTrack.distance(1000.0, 1000.0, 40.0), tolerance)
	}

	@Test
	fun aNoteAheadIsFurtherAwayTheFasterTheSpeed() {
		assertEquals(20.0, GroundTrack.distance(1500.0, 1000.0, 40.0), tolerance)
		assertEquals(40.0, GroundTrack.distance(1500.0, 1000.0, 80.0), tolerance)
	}

	@Test
	fun aNotePastItsTimeIsOnThePlayersSideOfTheLine() {
		assertEquals(-4.0, GroundTrack.distance(1000.0, 1100.0, 40.0), tolerance)
	}
}
