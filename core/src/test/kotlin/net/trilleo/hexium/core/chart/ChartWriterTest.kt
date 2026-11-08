package net.trilleo.hexium.core.chart

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class ChartWriterTest {
	private val chart = DifficultyChart(
		formatVersion = 1,
		difficulty = Difficulty.EXPERT,
		level = 11,
		laneCount = 4,
		offsetMs = 120.0,
		bpm = listOf(BpmChange(Beat(0, 0, 1), 174.0), BpmChange(Beat(64, 0, 1), 87.5)),
		meter = listOf(MeterChange(Beat(0, 0, 1), 4, 4)),
		notes = listOf(
			Note.Tap(Beat(4, 0, 1), 0),
			Note.Hold(Beat(4, 1, 2), Beat(6, 0, 1), 2),
			Note.SkyTap(Beat(5, 0, 1), 0.25, 0.6, SkyButton.L, 1.0),
			Note.Slide(
				SkyButton.R,
				0.06,
				listOf(
					SlidePoint(Beat(6, 0, 1), 0.2, 0.5, Ease.LINEAR),
					SlidePoint(Beat(6, 1, 3), 0.3, 0.6, Ease.IN),
					SlidePoint(Beat(6, 2, 3), 0.4, 0.7, Ease.OUT),
					SlidePoint(Beat(7, 0, 1), 0.7, 0.8, Ease.IN_OUT),
				),
			),
			Note.Flick(Beat(8, 0, 1), 0.5, 0.4, 1.0),
		),
	)

	private val meta = ChartMeta(
		formatVersion = 1,
		id = "example-song",
		title = "Example \"Song\" \\ 歌\n",
		artist = "Example Artist",
		charter = "Example Charter",
		previewStartMs = 30000,
		license = License(LicenseType.PERMITTED, "https://example.com/license"),
		difficulties = listOf(Difficulty.BASIC, Difficulty.MASTER),
	)

	private fun assertRoundTrips(chart: DifficultyChart) {
		val result = ChartParser.parseDifficulty(ChartWriter.writeDifficulty(chart))
		assertEquals(emptyList(), result.issues)
		assertEquals(chart, result.value)
	}

	@Test
	fun writesTheFixedLayout() {
		val small = chart.copy(
			bpm = chart.bpm.take(1),
			notes = listOf(
				Note.Tap(Beat(4, 0, 1), 0),
				Note.Slide(
					SkyButton.L,
					0.06,
					listOf(
						SlidePoint(Beat(6, 0, 1), 0.2, 0.5, Ease.LINEAR),
						SlidePoint(Beat(7, 0, 1), 0.7, 0.8, Ease.IN_OUT),
					),
				),
				Note.Flick(Beat(8, 0, 1), 0.5, 0.4, 1.0),
			),
		)
		assertEquals(
			"""
			{
			  "formatVersion": 1,
			  "difficulty": "EXPERT",
			  "level": 11,
			  "laneCount": 4,
			  "offsetMs": 120,
			  "bpm": [
			    { "t": [0,0,1], "bpm": 174 }
			  ],
			  "meter": [
			    { "t": [0,0,1], "num": 4, "den": 4 }
			  ],
			  "notes": [
			    { "type": "tap", "t": [4,0,1], "lane": 0 },
			    { "type": "slide", "button": "L", "radius": 0.06, "points": [
			      { "t": [6,0,1], "x": 0.2, "y": 0.5, "ease": "linear" },
			      { "t": [7,0,1], "x": 0.7, "y": 0.8, "ease": "inOut" }
			    ] },
			    { "type": "flick", "t": [8,0,1], "x": 0.5, "y": 0.4, "size": 1 }
			  ]
			}
			""".trimIndent() + "\n",
			ChartWriter.writeDifficulty(small),
		)
	}

	@Test
	fun writesTheMetaLayout() {
		assertEquals(
			"""
			{
			  "formatVersion": 1,
			  "id": "song",
			  "title": "T",
			  "artist": "A",
			  "charter": "C",
			  "previewStartMs": 5,
			  "license": { "type": "original" },
			  "difficulties": [ "BASIC", "MASTER" ]
			}
			""".trimIndent() + "\n",
			ChartWriter.writeMeta(
				meta.copy(
					id = "song",
					title = "T",
					artist = "A",
					charter = "C",
					previewStartMs = 5,
					license = License(LicenseType.ORIGINAL, null),
				),
			),
		)
	}

	@Test
	fun everyNoteTypeRoundTrips() {
		assertRoundTrips(chart)
	}

	@Test
	fun metaRoundTripsIncludingEscapedText() {
		val result = ChartParser.parseMeta(ChartWriter.writeMeta(meta))
		assertEquals(emptyList(), result.issues)
		assertEquals(meta, result.value)
	}

	@Test
	fun fractionsStayAsWritten() {
		val chart = chart.copy(notes = listOf(Note.Tap(Beat(12, 2, 4), 0)))
		assertEquals(true, """"t": [12,2,4]""" in ChartWriter.writeDifficulty(chart))
		assertRoundTrips(chart)
	}

	@Test
	fun numbersKeepTheirExactValue() {
		for (value in listOf(0.0, -0.0, -250.0, 0.1, 1.0 / 3.0, 1e-7, 1e21, 123456.789)) {
			assertRoundTrips(chart.copy(offsetMs = value))
		}
	}

	@Test
	fun forbiddenValuesStillRoundTripForTheValidator() {
		assertRoundTrips(
			chart.copy(
				level = 0,
				laneCount = 9,
				bpm = emptyList(),
				notes = listOf(Note.Tap(Beat(-1, 5, 0), 99)),
			),
		)
	}

	@Test
	fun outputIsStable() {
		val once = ChartWriter.writeDifficulty(chart)
		val twice = ChartWriter.writeDifficulty(ChartParser.parseDifficulty(once).value!!)
		assertEquals(once, twice)
	}

	@Test
	fun refusesNumbersJsonCannotHold() {
		assertFailsWith<IllegalArgumentException> { ChartWriter.writeDifficulty(chart.copy(offsetMs = Double.NaN)) }
		assertFailsWith<IllegalArgumentException> {
			ChartWriter.writeDifficulty(chart.copy(offsetMs = Double.POSITIVE_INFINITY))
		}
	}
}
