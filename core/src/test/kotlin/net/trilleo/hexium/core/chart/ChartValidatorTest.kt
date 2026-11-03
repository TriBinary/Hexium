package net.trilleo.hexium.core.chart

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Every rule has a passing and a failing case. A failing case must give exactly one issue, with no follow-on errors. */
class ChartValidatorTest {
	private fun b(whole: Int, num: Int = 0, den: Int = 1) = Beat(whole, num, den)

	private val chart = DifficultyChart(
		formatVersion = 1,
		difficulty = Difficulty.EXPERT,
		level = 1,
		laneCount = 4,
		offsetMs = 0.0,
		bpm = listOf(BpmChange(b(0), 120.0)),
		meter = listOf(MeterChange(b(0), 4, 4)),
		notes = emptyList(),
	)

	private fun withNotes(vararg notes: Note) = chart.copy(notes = notes.toList())

	private fun slide(vararg points: SlidePoint, radius: Double = 0.1) = Note.Slide(SkyButton.L, radius, points.toList())

	private fun point(t: Beat, x: Double = 0.5, y: Double = 0.5) = SlidePoint(t, x, y, Ease.LINEAR)

	private fun skyTap(t: Beat, x: Double = 0.5, y: Double = 0.5, size: Double = 1.0) =
		Note.SkyTap(t, x, y, SkyButton.L, size)

	private fun flick(t: Beat, x: Double = 0.5, y: Double = 0.5, dir: Double? = null, size: Double = 1.0) =
		Note.Flick(t, x, y, dir, size)

	private fun assertValid(chart: DifficultyChart, audioDurationMs: Double? = null) {
		assertEquals(emptyList(), ChartValidator.validateChart(chart, audioDurationMs))
	}

	private fun assertOnlyIssue(
		issues: List<ChartIssue>,
		code: IssueCode,
		path: String,
		severity: Severity = Severity.ERROR,
	) {
		assertEquals(1, issues.size, issues.toString())
		assertEquals(code, issues[0].code)
		assertEquals(path, issues[0].path)
		assertEquals(severity, issues[0].severity)
		assertTrue(issues[0].message.isNotBlank())
	}

	private fun assertOnlyError(chart: DifficultyChart, code: IssueCode, path: String, audioDurationMs: Double? = null) {
		assertOnlyIssue(ChartValidator.validateChart(chart, audioDurationMs), code, path)
	}

	@Test
	fun emptyChartIsValid() = assertValid(chart)

	// formatVersion

	@Test
	fun knownFormatVersionPasses() = assertValid(chart.copy(formatVersion = 1))

	@Test
	fun unknownFormatVersionFails() =
		assertOnlyError(chart.copy(formatVersion = 2), IssueCode.UNKNOWN_FORMAT_VERSION, "formatVersion")

	// laneCount and level

	@Test
	fun fourLanesPass() = assertValid(chart.copy(laneCount = 4))

	@Test
	fun otherLaneCountsFail() {
		assertOnlyError(chart.copy(laneCount = 6), IssueCode.BAD_LANE_COUNT, "laneCount")
		assertOnlyError(chart.copy(laneCount = 0), IssueCode.BAD_LANE_COUNT, "laneCount")
	}

	@Test
	fun levelOneAndUpPasses() {
		assertValid(chart.copy(level = 1))
		assertValid(chart.copy(level = 15))
	}

	@Test
	fun levelBelowOneFails() = assertOnlyError(chart.copy(level = 0), IssueCode.BAD_LEVEL, "level")

	// Fractions

	@Test
	fun unreducedFractionsPass() = assertValid(withNotes(Note.Tap(b(12, 2, 4), 0)))

	@Test
	fun invalidFractionsFailOnceEverywhere() {
		val bad = b(12, 5, 4)
		assertOnlyError(withNotes(Note.Tap(bad, 0)), IssueCode.INVALID_BEAT, "notes[0].t")
		assertOnlyError(withNotes(Note.Hold(bad, b(13), 0)), IssueCode.INVALID_BEAT, "notes[0].t")
		assertOnlyError(withNotes(Note.Hold(b(1), bad, 0)), IssueCode.INVALID_BEAT, "notes[0].end")
		assertOnlyError(withNotes(skyTap(bad)), IssueCode.INVALID_BEAT, "notes[0].t")
		assertOnlyError(withNotes(flick(bad)), IssueCode.INVALID_BEAT, "notes[0].t")
		assertOnlyError(
			withNotes(slide(point(b(1)), point(bad), point(b(2)))),
			IssueCode.INVALID_BEAT,
			"notes[0].points[1].t",
		)
		assertOnlyError(
			chart.copy(bpm = chart.bpm + BpmChange(bad, 90.0)),
			IssueCode.INVALID_BEAT,
			"bpm[1].t",
		)
		assertOnlyError(
			chart.copy(meter = chart.meter + MeterChange(bad, 3, 4)),
			IssueCode.INVALID_BEAT,
			"meter[1].t",
		)
	}

	@Test
	fun invalidFractionKindsAreAllCaught() {
		for (bad in listOf(b(-1), b(1, 4, 4), b(1, -1, 4), b(1, 0, 0), b(1, 0, -4))) {
			assertOnlyError(withNotes(Note.Tap(bad, 0)), IssueCode.INVALID_BEAT, "notes[0].t")
		}
	}

	@Test
	fun audioRuleSkipsNotesWithInvalidBeats() {
		assertOnlyError(withNotes(Note.Tap(b(1, 0, 0), 0)), IssueCode.INVALID_BEAT, "notes[0].t", audioDurationMs = 1.0)
	}

	// BPM

	@Test
	fun bpmAboveZeroPasses() = assertValid(chart.copy(bpm = listOf(BpmChange(b(0), 0.001))))

	@Test
	fun bpmAtOrBelowZeroFails() {
		for (bad in listOf(0.0, -120.0, Double.NaN)) {
			assertOnlyError(chart.copy(bpm = listOf(BpmChange(b(0), bad))), IssueCode.BPM_NOT_POSITIVE, "bpm[0].bpm")
		}
	}

	@Test
	fun firstBpmAtBeatZeroPasses() = assertValid(chart.copy(bpm = listOf(BpmChange(b(0, 0, 4), 120.0))))

	@Test
	fun firstBpmElsewhereFails() {
		assertOnlyError(chart.copy(bpm = listOf(BpmChange(b(1), 120.0))), IssueCode.FIRST_BPM_NOT_AT_ZERO, "bpm[0].t")
		assertOnlyError(chart.copy(bpm = emptyList()), IssueCode.FIRST_BPM_NOT_AT_ZERO, "bpm")
	}

	@Test
	fun risingBpmPasses() = assertValid(chart.copy(bpm = chart.bpm + BpmChange(b(0, 1, 2), 90.0) + BpmChange(b(1), 60.0)))

	@Test
	fun bpmNotRisingFails() {
		val same = chart.bpm + BpmChange(b(0, 0, 3), 90.0)
		assertOnlyError(chart.copy(bpm = same), IssueCode.BPM_NOT_RISING, "bpm[1].t")
		val back = chart.bpm + BpmChange(b(4), 90.0) + BpmChange(b(2), 60.0)
		assertOnlyError(chart.copy(bpm = back), IssueCode.BPM_NOT_RISING, "bpm[2].t")
	}

	// Meter

	@Test
	fun firstMeterAtBeatZeroPasses() = assertValid(chart.copy(meter = listOf(MeterChange(b(0, 0, 2), 3, 4))))

	@Test
	fun firstMeterElsewhereFails() {
		assertOnlyError(chart.copy(meter = listOf(MeterChange(b(1), 4, 4))), IssueCode.FIRST_METER_NOT_AT_ZERO, "meter[0].t")
		assertOnlyError(chart.copy(meter = emptyList()), IssueCode.FIRST_METER_NOT_AT_ZERO, "meter")
	}

	@Test
	fun risingMeterPasses() = assertValid(chart.copy(meter = chart.meter + MeterChange(b(4), 3, 4)))

	@Test
	fun meterNotRisingFails() {
		assertOnlyError(chart.copy(meter = chart.meter + MeterChange(b(0), 3, 4)), IssueCode.METER_NOT_RISING, "meter[1].t")
	}

	@Test
	fun meterAboveZeroPasses() = assertValid(chart.copy(meter = listOf(MeterChange(b(0), 7, 8))))

	@Test
	fun meterAtOrBelowZeroFails() {
		assertOnlyError(chart.copy(meter = listOf(MeterChange(b(0), 0, 4))), IssueCode.METER_NOT_POSITIVE, "meter[0].num")
		assertOnlyError(chart.copy(meter = listOf(MeterChange(b(0), 4, -4))), IssueCode.METER_NOT_POSITIVE, "meter[0].den")
	}

	// Lanes and holds

	@Test
	fun lanesInRangePass() = assertValid(withNotes(Note.Tap(b(1), 0), Note.Tap(b(1), 3), Note.Hold(b(2), b(3), 3)))

	@Test
	fun lanesOutOfRangeFail() {
		assertOnlyError(withNotes(Note.Tap(b(1), 4)), IssueCode.LANE_OUT_OF_RANGE, "notes[0].lane")
		assertOnlyError(withNotes(Note.Tap(b(1), -1)), IssueCode.LANE_OUT_OF_RANGE, "notes[0].lane")
		assertOnlyError(withNotes(Note.Hold(b(1), b(2), 4)), IssueCode.LANE_OUT_OF_RANGE, "notes[0].lane")
	}

	@Test
	fun holdEndingAfterItsStartPasses() = assertValid(withNotes(Note.Hold(b(1), b(1, 1, 32), 0)))

	@Test
	fun holdNotEndingAfterItsStartFails() {
		assertOnlyError(withNotes(Note.Hold(b(1), b(1), 0)), IssueCode.HOLD_END_NOT_AFTER_START, "notes[0].end")
		assertOnlyError(withNotes(Note.Hold(b(1, 2, 4), b(1, 1, 2), 0)), IssueCode.HOLD_END_NOT_AFTER_START, "notes[0].end")
		assertOnlyError(withNotes(Note.Hold(b(2), b(1), 0)), IssueCode.HOLD_END_NOT_AFTER_START, "notes[0].end")
	}

	@Test
	fun holdsThatDoNotOverlapPass() {
		assertValid(withNotes(Note.Hold(b(1), b(2), 0), Note.Hold(b(3), b(4), 0)))
		assertValid(withNotes(Note.Hold(b(1), b(2), 0), Note.Hold(b(1), b(2), 1)))
	}

	@Test
	fun holdMayStartWhereAnotherEnds() = assertValid(withNotes(Note.Hold(b(1), b(2), 0), Note.Hold(b(2), b(3), 0)))

	@Test
	fun overlappingHoldsFail() {
		assertOnlyError(
			withNotes(Note.Hold(b(1), b(3), 0), Note.Hold(b(2), b(4), 0)),
			IssueCode.HOLDS_OVERLAP,
			"notes[1]",
		)
	}

	@Test
	fun holdsOverlapWhateverTheFileOrder() {
		assertOnlyError(
			withNotes(Note.Hold(b(2), b(4), 0), Note.Hold(b(1), b(3), 0)),
			IssueCode.HOLDS_OVERLAP,
			"notes[0]",
		)
	}

	@Test
	fun holdInsideALongerHoldFailsEvenAfterAShortOne() {
		val issues = ChartValidator.validateChart(
			withNotes(Note.Hold(b(1), b(10), 0), Note.Hold(b(2), b(3), 0), Note.Hold(b(4), b(5), 0)),
		)
		assertEquals(listOf("notes[1]", "notes[2]"), issues.map { it.path })
		assertEquals(setOf(IssueCode.HOLDS_OVERLAP), issues.map { it.code }.toSet())
	}

	@Test
	fun holdsSharingAHeadFailOnce() {
		assertOnlyError(withNotes(Note.Hold(b(1), b(2), 0), Note.Hold(b(1), b(3), 0)), IssueCode.HOLDS_OVERLAP, "notes[1]")
	}

	@Test
	fun tapOutsideAHoldPasses() {
		assertValid(withNotes(Note.Hold(b(2), b(3), 0), Note.Tap(b(1), 0), Note.Tap(b(4), 0)))
		assertValid(withNotes(Note.Hold(b(2), b(3), 0), Note.Tap(b(2, 1, 2), 1)))
	}

	@Test
	fun tapOnAHoldsEndPasses() = assertValid(withNotes(Note.Hold(b(2), b(3), 0), Note.Tap(b(3), 0)))

	@Test
	fun tapInsideAHoldFails() {
		assertOnlyError(withNotes(Note.Hold(b(2), b(3), 0), Note.Tap(b(2, 1, 2), 0)), IssueCode.TAP_INSIDE_HOLD, "notes[1]")
	}

	@Test
	fun tapOnAHoldsHeadFailsOnce() {
		assertOnlyError(withNotes(Note.Tap(b(2), 0), Note.Hold(b(2), b(3), 0)), IssueCode.TAP_INSIDE_HOLD, "notes[0]")
	}

	@Test
	fun tapInsideALongHoldAfterAShortOneFails() {
		val issues = ChartValidator.validateChart(
			withNotes(Note.Hold(b(1), b(10), 0), Note.Hold(b(2), b(3), 0), Note.Tap(b(5), 0)),
		)
		assertEquals(
			listOf(IssueCode.HOLDS_OVERLAP to "notes[1]", IssueCode.TAP_INSIDE_HOLD to "notes[2]"),
			issues.map { it.code to it.path },
		)
	}

	@Test
	fun tapsAtDifferentTimesOrLanesPass() {
		assertValid(withNotes(Note.Tap(b(1), 0), Note.Tap(b(1), 1), Note.Tap(b(2), 0)))
	}

	@Test
	fun twoTapsAtTheSameTimeAndLaneFail() {
		assertOnlyError(withNotes(Note.Tap(b(1, 1, 2), 0), Note.Tap(b(1, 2, 4), 0)), IssueCode.DUPLICATE_TAP, "notes[1]")
	}

	// Slides, positions, sizes

	@Test
	fun slideWithRisingPointsPasses() = assertValid(withNotes(slide(point(b(1)), point(b(1, 1, 8)), point(b(2)))))

	@Test
	fun slideWithTooFewPointsFails() {
		assertOnlyError(withNotes(slide(point(b(1)))), IssueCode.SLIDE_TOO_FEW_POINTS, "notes[0].points")
		assertOnlyError(withNotes(slide()), IssueCode.SLIDE_TOO_FEW_POINTS, "notes[0].points")
	}

	@Test
	fun slidePointsNotRisingFail() {
		assertOnlyError(
			withNotes(slide(point(b(1)), point(b(1, 0, 2)))),
			IssueCode.SLIDE_POINTS_NOT_RISING,
			"notes[0].points[1].t",
		)
		assertOnlyError(
			withNotes(slide(point(b(2)), point(b(1)))),
			IssueCode.SLIDE_POINTS_NOT_RISING,
			"notes[0].points[1].t",
		)
	}

	@Test
	fun positionsInsideTheScreenPass() {
		assertValid(
			withNotes(
				skyTap(b(1), x = 0.0, y = 1.0),
				flick(b(2), x = 1.0, y = 0.0),
				slide(point(b(3), 0.0, 0.0), point(b(4), 1.0, 1.0)),
			),
		)
	}

	@Test
	fun positionsOutsideTheScreenFail() {
		assertOnlyError(withNotes(skyTap(b(1), x = -0.01)), IssueCode.POSITION_OUT_OF_RANGE, "notes[0].x")
		assertOnlyError(withNotes(skyTap(b(1), y = 1.01)), IssueCode.POSITION_OUT_OF_RANGE, "notes[0].y")
		assertOnlyError(withNotes(flick(b(1), x = 1.5)), IssueCode.POSITION_OUT_OF_RANGE, "notes[0].x")
		assertOnlyError(withNotes(flick(b(1), y = Double.NaN)), IssueCode.POSITION_OUT_OF_RANGE, "notes[0].y")
		assertOnlyError(
			withNotes(slide(point(b(1)), point(b(2), x = 2.0))),
			IssueCode.POSITION_OUT_OF_RANGE,
			"notes[0].points[1].x",
		)
		assertOnlyError(
			withNotes(slide(point(b(1), y = -1.0), point(b(2)))),
			IssueCode.POSITION_OUT_OF_RANGE,
			"notes[0].points[0].y",
		)
	}

	@Test
	fun sizeAndRadiusAboveZeroPass() {
		assertValid(withNotes(skyTap(b(1), size = 0.1), flick(b(2), size = 0.1), slide(point(b(3)), point(b(4)), radius = 0.01)))
	}

	@Test
	fun sizeAndRadiusAtOrBelowZeroFail() {
		assertOnlyError(withNotes(skyTap(b(1), size = 0.0)), IssueCode.SIZE_NOT_POSITIVE, "notes[0].size")
		assertOnlyError(withNotes(flick(b(1), size = -1.0)), IssueCode.SIZE_NOT_POSITIVE, "notes[0].size")
		assertOnlyError(withNotes(slide(point(b(1)), point(b(2)), radius = 0.0)), IssueCode.RADIUS_NOT_POSITIVE, "notes[0].radius")
	}

	@Test
	fun dirInRangeOrLeftOutPasses() {
		assertValid(withNotes(flick(b(1), dir = 0.0), flick(b(2), dir = 359.9), flick(b(3), dir = null)))
	}

	@Test
	fun dirOutOfRangeFails() {
		assertOnlyError(withNotes(flick(b(1), dir = 360.0)), IssueCode.DIR_OUT_OF_RANGE, "notes[0].dir")
		assertOnlyError(withNotes(flick(b(1), dir = -1.0)), IssueCode.DIR_OUT_OF_RANGE, "notes[0].dir")
	}

	// The last note against the audio. At 120 BPM, beat 4 is 2000 ms.

	@Test
	fun notesEndingBeforeTheAudioPass() {
		assertValid(withNotes(Note.Tap(b(4), 0)), audioDurationMs = 2000.5)
	}

	@Test
	fun audioRuleIsSkippedWithoutALength() = assertValid(withNotes(Note.Tap(b(4000), 0)), audioDurationMs = null)

	@Test
	fun lastNoteAtOrPastTheAudioEndFails() {
		assertOnlyError(withNotes(Note.Tap(b(4), 0)), IssueCode.NOTE_AFTER_AUDIO, "notes[0]", audioDurationMs = 2000.0)
		assertOnlyError(withNotes(Note.Tap(b(5), 0)), IssueCode.NOTE_AFTER_AUDIO, "notes[0]", audioDurationMs = 2000.0)
	}

	@Test
	fun audioRuleUsesWhereANoteEnds() {
		assertOnlyError(withNotes(Note.Hold(b(1), b(4), 0)), IssueCode.NOTE_AFTER_AUDIO, "notes[0]", audioDurationMs = 2000.0)
		assertOnlyError(withNotes(slide(point(b(1)), point(b(4)))), IssueCode.NOTE_AFTER_AUDIO, "notes[0]", audioDurationMs = 2000.0)
		assertOnlyError(withNotes(skyTap(b(4))), IssueCode.NOTE_AFTER_AUDIO, "notes[0]", audioDurationMs = 2000.0)
		assertOnlyError(withNotes(flick(b(4))), IssueCode.NOTE_AFTER_AUDIO, "notes[0]", audioDurationMs = 2000.0)
	}

	@Test
	fun audioRuleReportsOnlyTheLastNoteWhateverTheFileOrder() {
		assertOnlyError(
			withNotes(Note.Tap(b(5), 0), Note.Tap(b(6), 0), Note.Tap(b(1), 0)),
			IssueCode.NOTE_AFTER_AUDIO,
			"notes[1]",
			audioDurationMs = 2000.0,
		)
	}

	@Test
	fun audioRuleUsesOffsetAndBpmChanges() {
		val timed = chart.copy(offsetMs = 500.0, bpm = chart.bpm + BpmChange(b(2), 60.0))
		val note = Note.Tap(b(3), 0)
		assertValid(timed.copy(notes = listOf(note)), audioDurationMs = 2500.5)
		assertOnlyError(timed.copy(notes = listOf(note)), IssueCode.NOTE_AFTER_AUDIO, "notes[0]", audioDurationMs = 2500.0)
	}

	@Test
	fun audioRuleIsSkippedWhenBpmIsBroken() {
		val broken = chart.copy(bpm = listOf(BpmChange(b(0), 0.0)), notes = listOf(Note.Tap(b(4), 0)))
		assertOnlyError(broken, IssueCode.BPM_NOT_POSITIVE, "bpm[0].bpm", audioDurationMs = 1.0)
	}

	// The density warning

	private fun fourLanes(t: Beat) = arrayOf<Note>(Note.Tap(t, 0), Note.Tap(t, 1), Note.Tap(t, 2), Note.Tap(t, 3))

	private fun assertDensityWarning(chart: DifficultyChart, path: String = "notes[0]") {
		assertOnlyIssue(ChartValidator.validateChart(chart), IssueCode.TOO_DENSE, path, Severity.WARNING)
	}

	@Test
	fun fourGroundPlusSkyWarns() = assertDensityWarning(withNotes(*fourLanes(b(1)), skyTap(b(1))))

	@Test
	fun threeGroundPlusSkyPasses() {
		assertValid(withNotes(Note.Tap(b(1), 0), Note.Tap(b(1), 1), Note.Tap(b(1), 2), skyTap(b(1))))
	}

	@Test
	fun fourGroundAlonePasses() = assertValid(withNotes(*fourLanes(b(1))))

	@Test
	fun holdHeadsCountAsGround() {
		assertDensityWarning(
			withNotes(Note.Hold(b(1), b(2), 0), Note.Tap(b(1), 1), Note.Tap(b(1), 2), Note.Tap(b(1), 3), skyTap(b(1))),
		)
	}

	@Test
	fun heldKeysDoNotCount() {
		assertValid(withNotes(Note.Hold(b(0), b(2), 0), Note.Tap(b(1), 1), Note.Tap(b(1), 2), Note.Tap(b(1), 3), skyTap(b(1))))
	}

	@Test
	fun flicksAndSlideStartsCountAsSky() {
		assertDensityWarning(withNotes(*fourLanes(b(1)), flick(b(1))))
		assertDensityWarning(withNotes(*fourLanes(b(1)), slide(point(b(1)), point(b(2)))))
	}

	@Test
	fun laterSlidePointsDoNotCount() = assertValid(withNotes(*fourLanes(b(2)), slide(point(b(1)), point(b(2)), point(b(3)))))

	@Test
	fun notesCloserThanASixteenthWarn() {
		assertDensityWarning(withNotes(*fourLanes(b(1)), skyTap(b(1, 1, 32))))
	}

	@Test
	fun notesExactlyASixteenthApartPass() = assertValid(withNotes(*fourLanes(b(1)), skyTap(b(1, 1, 16))))

	@Test
	fun theWindowSlidesAcrossSixteenthBoundaries() {
		assertDensityWarning(withNotes(*fourLanes(b(1, 31, 64)), skyTap(b(1, 33, 64))))
	}

	@Test
	fun theWindowIsIndependentOfFileOrder() {
		assertDensityWarning(withNotes(skyTap(b(1, 1, 32)), *fourLanes(b(1))), path = "notes[1]")
	}

	@Test
	fun theWindowComparesHugeDenominatorsExactly() {
		val max = Int.MAX_VALUE
		assertDensityWarning(withNotes(*fourLanes(b(0, max - 1, max)), skyTap(b(1, 1, max))))
		assertValid(withNotes(*fourLanes(b(0, max - 1, max)), skyTap(b(2))))
	}

	@Test
	fun oneClusterWarnsOnce() {
		val notes = fourLanes(b(1)) + skyTap(b(1)) + skyTap(b(1, 1, 64)) + skyTap(b(1, 2, 64))
		assertDensityWarning(chart.copy(notes = notes.toList()))
	}

	@Test
	fun separateClustersWarnSeparately() {
		val notes = fourLanes(b(1)) + skyTap(b(1)) + fourLanes(b(2)) + skyTap(b(2))
		val issues = ChartValidator.validateChart(chart.copy(notes = notes.toList()))
		assertEquals(listOf("notes[0]", "notes[5]"), issues.map { it.path })
	}

	@Test
	fun manyProblemsAreAllReported() {
		val issues = ChartValidator.validateChart(
			chart.copy(level = 0, laneCount = 6, notes = listOf(Note.Tap(b(1), 9), skyTap(b(1), x = 2.0))),
		)
		assertEquals(
			listOf(IssueCode.BAD_LEVEL, IssueCode.BAD_LANE_COUNT, IssueCode.LANE_OUT_OF_RANGE, IssueCode.POSITION_OUT_OF_RANGE),
			issues.map { it.code },
		)
	}
}
