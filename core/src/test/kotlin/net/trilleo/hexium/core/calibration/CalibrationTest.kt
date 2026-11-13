package net.trilleo.hexium.core.calibration

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import net.trilleo.hexium.core.chart.Beat
import net.trilleo.hexium.core.chart.BpmChange
import net.trilleo.hexium.core.chart.Difficulty
import net.trilleo.hexium.core.chart.DifficultyChart
import net.trilleo.hexium.core.chart.GroundTrack
import net.trilleo.hexium.core.chart.MeterChange
import net.trilleo.hexium.core.chart.Note
import net.trilleo.hexium.core.judge.Judge
import net.trilleo.hexium.core.judge.JudgeChart

class CalibrationTest {
	private val beatMs = 500.0
	private val tolerance = 1e-6

	/** One tap on each beat from beat 4, off by each of [errors] in turn. */
	private fun taps(vararg errors: Double) = DoubleArray(errors.size) { (it + 4) * beatMs + errors[it] }

	private fun estimate(vararg errors: Double): TapEstimate {
		val taps = taps(*errors)
		return assertNotNull(Calibration.estimate(taps, taps.size, beatMs))
	}

	@Test
	fun tapsOnTheBeatHaveNoError() {
		assertEquals(0.0, estimate(0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0).errorMs, tolerance)
	}

	@Test
	fun lateTapsArePositiveAndEarlyTapsNegative() {
		assertEquals(40.0, estimate(40.0, 40.0, 40.0, 40.0, 40.0, 40.0, 40.0, 40.0).errorMs, tolerance)
		assertEquals(-40.0, estimate(-40.0, -40.0, -40.0, -40.0, -40.0, -40.0, -40.0, -40.0).errorMs, tolerance)
	}

	@Test
	fun theMiddleTapCountsSoOneWildTapChangesNothing() {
		val result = estimate(20.0, 22.0, 24.0, 26.0, 28.0, 30.0, 32.0, 34.0, 200.0)
		assertEquals(27.0, result.errorMs, tolerance)
		assertEquals(8, result.tapsUsed)
		assertTrue(result.isSteady)
	}

	@Test
	fun tapsEitherSideOfTheHalfBeatStayTogether() {
		val result = estimate(245.0, 255.0, 245.0, 255.0, 245.0, 255.0, 245.0, 255.0)
		assertEquals(250.0, Math.abs(result.errorMs), 5.0 + tolerance)
		assertTrue(result.spreadMs <= 5.0 + tolerance)
	}

	@Test
	fun tapsDuringALeadInCountToo() {
		val taps = DoubleArray(8) { -2000.0 + it * beatMs + 30.0 }
		assertEquals(30.0, assertNotNull(Calibration.estimate(taps, taps.size, beatMs)).errorMs, tolerance)
	}

	@Test
	fun onlyTheGivenCountIsRead() {
		val taps = DoubleArray(20)
		assertNull(Calibration.estimate(taps, Calibration.MIN_TAPS - 1, beatMs))
		assertNotNull(Calibration.estimate(taps, Calibration.MIN_TAPS, beatMs))
	}

	@Test
	fun unevenTapsAreNotSteady() {
		assertFalse(estimate(-120.0, 120.0, -90.0, 90.0, -120.0, 120.0, -90.0, 90.0, 0.0).isSteady)
	}

	@Test
	fun theJudgementOffsetIsHowLateThePlayerTapsToSound() {
		assertEquals(40, Calibration.judgementOffsetMs(estimate(40.0, 40.0, 40.0, 40.0, 40.0, 40.0, 40.0, 40.0)))
	}

	@Test
	fun theVisualOffsetLinesUpWhatTheEyeSeesWithWhatTheEarHears() {
		// Taps 40 ms late to sound, but only 10 ms late to notes drawn with no offset.
		val visual = estimate(10.0, 10.0, 10.0, 10.0, 10.0, 10.0, 10.0, 10.0)
		assertEquals(30, Calibration.visualOffsetMs(40, visual))
	}

	@Test
	fun theSuggestedOffsetsMakeATapOnTheDrawnNoteJudgeAsZero() {
		val judgementOffset = 40
		val tapLateToDrawnNotes = 10.0
		val visualOffset = Calibration.visualOffsetMs(judgementOffset, estimate(10.0, 10.0, 10.0, 10.0, 10.0, 10.0, 10.0, 10.0))
		val beat = Beat(4, 0, 1)
		val chart = DifficultyChart(
			1, Difficulty.EXPERT, 1, 4, 0.0,
			listOf(BpmChange(Beat(0, 0, 1), 120.0)),
			listOf(MeterChange(Beat(0, 0, 1), 4, 4)),
			listOf(Note.Tap(beat, 0)),
		)
		val noteMs = 2000.0
		// The song time when this note reaches the line on screen, given how it's drawn.
		val onTheLineMs = noteMs + visualOffset
		assertEquals(noteMs, GroundTrack.drawTimeMs(onTheLineMs, visualOffset.toDouble()), tolerance)
		val judge = Judge(JudgeChart.compile(chart), judgementOffset.toDouble())
		judge.pressLane(0, onTheLineMs + tapLateToDrawnNotes)
		assertEquals(0.0, judge.judgedDeltaMs(0), tolerance)
	}

	@Test
	fun tapsMoreThanHalfABeatLateAreLateNotEarly() {
		assertEquals(251.0, estimate(*DoubleArray(8) { 251.0 }).errorMs, tolerance)
		assertEquals(300.0, estimate(*DoubleArray(8) { 300.0 }).errorMs, tolerance)
	}

	@Test
	fun tapsALittleEarlyStayEarly() {
		assertEquals(-100.0, estimate(*DoubleArray(8) { -100.0 }).errorMs, tolerance)
	}

	@Test
	fun tooManyThrownOutTapsAreNotSteady() {
		val result = estimate(*DoubleArray(10) { 0.0 }, 200.0, -200.0, 150.0, -150.0, 180.0, -180.0)
		assertEquals(10, result.tapsUsed)
		assertFalse(result.isSteady)
	}

	@Test
	fun tooFewUsableTapsGiveNothing() {
		val taps = taps(0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 200.0, -200.0, 150.0, -150.0)
		assertNull(Calibration.estimate(taps, 11, beatMs))
	}

	@Test
	fun tapsAtTheirOwnTempoSlideOffTheBeatAndAreNotSteady() {
		val taps = DoubleArray(16) { 2000.0 + it * 505.0 }
		assertFalse(assertNotNull(Calibration.estimate(taps, taps.size, beatMs)).isSteady)
	}
}
