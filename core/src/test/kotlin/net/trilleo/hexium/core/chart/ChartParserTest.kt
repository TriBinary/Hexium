package net.trilleo.hexium.core.chart

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ChartParserTest {
	/** The example from docs/CHART_FORMAT.md. */
	private val specExample = """
		{
		  "formatVersion": 1,
		  "difficulty": "EXPERT",
		  "level": 11,
		  "laneCount": 4,
		  "offsetMs": 120,
		  "bpm": [ { "t": [0,0,1], "bpm": 174 }, { "t": [64,0,1], "bpm": 87 } ],
		  "meter": [ { "t": [0,0,1], "num": 4, "den": 4 } ],
		  "notes": [
		    { "type": "tap",   "t": [4,0,1], "lane": 0 },
		    { "type": "hold",  "t": [4,1,2], "end": [6,0,1], "lane": 2 },
		    { "type": "skyTap", "t": [5,0,1], "x": 0.25, "y": 0.60, "button": "L", "size": 1.0 },
		    { "type": "slide", "button": "L", "radius": 0.06, "points": [
		        { "t": [6,0,1], "x": 0.20, "y": 0.50, "ease": "linear" },
		        { "t": [7,0,1], "x": 0.70, "y": 0.80, "ease": "inOut" } ] },
		    { "type": "flick", "t": [8,0,1], "x": 0.50, "y": 0.40, "dir": 90, "size": 1.0 }
		  ]
		}
	""".trimIndent()

	private val metaExample = """
		{
		  "formatVersion": 1,
		  "id": "example-song",
		  "title": "Example Song",
		  "artist": "Example Artist",
		  "charter": "Example Charter",
		  "previewStartMs": 30000,
		  "license": { "type": "permitted", "source": "https://example.com/license" },
		  "difficulties": [ "BASIC", "EXPERT" ]
		}
	""".trimIndent()

	/** A chart around one note, for testing note fields. */
	private fun chartWithNote(note: String) = """
		{ "formatVersion": 1, "difficulty": "BASIC", "level": 1, "laneCount": 4, "offsetMs": 0,
		  "bpm": [], "meter": [], "notes": [ $note ] }
	""".trimIndent()

	private fun assertSingleError(result: ParseResult<*>, code: IssueCode, path: String) {
		assertNull(result.value)
		assertEquals(1, result.issues.size, result.issues.toString())
		val issue = result.issues[0]
		assertEquals(Severity.ERROR, issue.severity)
		assertEquals(code, issue.code)
		assertEquals(path, issue.path)
		assertTrue(issue.message.isNotBlank())
	}

	@Test
	fun readsTheSpecExample() {
		val result = ChartParser.parseDifficulty(specExample)
		assertEquals(emptyList(), result.issues)
		assertEquals(
			DifficultyChart(
				formatVersion = 1,
				difficulty = Difficulty.EXPERT,
				level = 11,
				laneCount = 4,
				offsetMs = 120.0,
				bpm = listOf(BpmChange(Beat(0, 0, 1), 174.0), BpmChange(Beat(64, 0, 1), 87.0)),
				meter = listOf(MeterChange(Beat(0, 0, 1), 4, 4)),
				notes = listOf(
					Note.Tap(Beat(4, 0, 1), 0),
					Note.Hold(Beat(4, 1, 2), Beat(6, 0, 1), 2),
					Note.SkyTap(Beat(5, 0, 1), 0.25, 0.60, SkyButton.L, 1.0),
					Note.Slide(
						SkyButton.L,
						0.06,
						listOf(
							SlidePoint(Beat(6, 0, 1), 0.20, 0.50, Ease.LINEAR),
							SlidePoint(Beat(7, 0, 1), 0.70, 0.80, Ease.IN_OUT),
						),
					),
					Note.Flick(Beat(8, 0, 1), 0.50, 0.40, 90.0, 1.0),
				),
			),
			result.value,
		)
	}

	@Test
	fun readsTheMetaExample() {
		val result = ChartParser.parseMeta(metaExample)
		assertEquals(emptyList(), result.issues)
		assertEquals(
			ChartMeta(
				formatVersion = 1,
				id = "example-song",
				title = "Example Song",
				artist = "Example Artist",
				charter = "Example Charter",
				previewStartMs = 30000,
				license = License(LicenseType.PERMITTED, "https://example.com/license"),
				difficulties = listOf(Difficulty.BASIC, Difficulty.EXPERT),
			),
			result.value,
		)
	}

	@Test
	fun optionalFieldsCanBeLeftOut() {
		val flick = """{ "type": "flick", "t": [8,0,1], "x": 0.5, "y": 0.4, "size": 1.0 }"""
		val note = ChartParser.parseDifficulty(chartWithNote(flick)).value?.notes?.single()
		assertEquals(Note.Flick(Beat(8, 0, 1), 0.5, 0.4, null, 1.0), note)

		val meta = ChartParser.parseMeta(metaExample.replace(""", "source": "https://example.com/license"""", ""))
		assertEquals(License(LicenseType.PERMITTED, null), meta.value?.license)
	}

	@Test
	fun everyEaseAndButtonIsRead() {
		val slide = """{ "type": "slide", "button": "R", "radius": 0.1, "points": [
			{ "t": [0,0,1], "x": 0, "y": 0, "ease": "linear" },
			{ "t": [1,0,1], "x": 0, "y": 0, "ease": "in" },
			{ "t": [2,0,1], "x": 0, "y": 0, "ease": "out" },
			{ "t": [3,0,1], "x": 0, "y": 0, "ease": "inOut" } ] }"""
		val note = ChartParser.parseDifficulty(chartWithNote(slide)).value?.notes?.single() as Note.Slide
		assertEquals(SkyButton.R, note.button)
		assertEquals(listOf(Ease.LINEAR, Ease.IN, Ease.OUT, Ease.IN_OUT), note.points.map { it.ease })
	}

	@Test
	fun valuesTheFormatForbidsAreLeftForTheValidator() {
		val result = ChartParser.parseDifficulty(chartWithNote("""{ "type": "tap", "t": [1,5,0], "lane": 99 }"""))
		assertEquals(emptyList(), result.issues)
		assertEquals(Note.Tap(Beat(1, 5, 0), 99), result.value?.notes?.single())
	}

	@Test
	fun brokenJsonIsReportedNotThrown() {
		assertSingleError(ChartParser.parseDifficulty("{ not json"), IssueCode.INVALID_JSON, "$")
		assertSingleError(ChartParser.parseDifficulty(""), IssueCode.INVALID_JSON, "$")
		assertSingleError(ChartParser.parseDifficulty("{} trailing"), IssueCode.INVALID_JSON, "$")
		assertSingleError(ChartParser.parseMeta("{ not json"), IssueCode.INVALID_JSON, "$")
	}

	@Test
	fun veryDeepNestingIsNotACrash() {
		val result = ChartParser.parseDifficulty("[".repeat(100_000))
		assertNull(result.value)
		assertEquals(IssueCode.INVALID_JSON, result.issues.single().code)
	}

	@Test
	fun nestingLimitIgnoresBracketsInsideText() {
		val brackets = "[".repeat(100)
		val result = ChartParser.parseMeta(metaExample.replace("Example Song", brackets))
		assertEquals(emptyList(), result.issues)
		assertEquals(brackets, result.value?.title)

		val escapedQuote = """\\\"""" + brackets
		val escaped = ChartParser.parseMeta(metaExample.replace("Example Song", escapedQuote))
		assertEquals(emptyList(), escaped.issues)
	}

	@Test
	fun nestingStopsAtTheLimit() {
		fun nested(levels: Int) = "[".repeat(levels) + "]".repeat(levels)
		assertSingleError(ChartParser.parseDifficulty(nested(32)), IssueCode.WRONG_TYPE, "$")
		assertSingleError(ChartParser.parseDifficulty(nested(33)), IssueCode.INVALID_JSON, "$")
	}

	@Test
	fun closingBracketsFirstDoNotHideDeepNesting() {
		val text = "]".repeat(100) + "[".repeat(100)
		assertSingleError(ChartParser.parseDifficulty(text), IssueCode.INVALID_JSON, "$")
	}

	@Test
	fun aLeadingByteOrderMarkIsIgnored() {
		val result = ChartParser.parseDifficulty("\uFEFF" + specExample)
		assertEquals(emptyList(), result.issues)
		assertNotNull(result.value)
	}

	@Test
	fun numbersOutsideJsonsGrammarAreRejected() {
		for (bad in listOf("0.5f", "0.5D", "0x1p-1", ".5", "1.", "+0.5", "00.5")) {
			assertSingleError(
				ChartParser.parseDifficulty(chartWithNote("""{ "type": "flick", "t": [1,0,1], "x": $bad, "y": 0, "size": 1 }""")),
				IssueCode.WRONG_TYPE,
				"notes[0].x",
			)
		}
		for (bad in listOf("01", "+1", "0x1")) {
			assertSingleError(
				ChartParser.parseDifficulty(chartWithNote("""{ "type": "tap", "t": [1,0,1], "lane": $bad }""")),
				IssueCode.WRONG_TYPE,
				"notes[0].lane",
			)
			assertSingleError(
				ChartParser.parseDifficulty(chartWithNote("""{ "type": "tap", "t": [$bad,0,1], "lane": 0 }""")),
				IssueCode.WRONG_TYPE,
				"notes[0].t",
			)
		}
	}

	@Test
	fun numbersInJsonsGrammarAreRead() {
		val values = listOf("0" to 0.0, "1.5" to 1.5, "-0.5" to -0.5, "1e-2" to 0.01, "1E2" to 100.0, "2.5e+1" to 25.0)
		for ((text, value) in values) {
			val note = ChartParser.parseDifficulty(
				chartWithNote("""{ "type": "flick", "t": [1,0,1], "x": $text, "y": 0, "size": 1 }"""),
			).value?.notes?.single() as Note.Flick
			assertEquals(value, note.x, text)
		}
	}

	@Test
	fun wholeNumbersAreDigitsOnly() {
		for (bad in listOf("1e0", "1E0", "1.0")) {
			assertSingleError(
				ChartParser.parseDifficulty(chartWithNote("""{ "type": "tap", "t": [1,0,1], "lane": $bad }""")),
				IssueCode.WRONG_TYPE,
				"notes[0].lane",
			)
			assertSingleError(
				ChartParser.parseDifficulty(chartWithNote("""{ "type": "tap", "t": [$bad,0,1], "lane": 0 }""")),
				IssueCode.WRONG_TYPE,
				"notes[0].t",
			)
		}
		assertSingleError(ChartParser.parseDifficulty("""{ "formatVersion": 1e0 }"""), IssueCode.WRONG_TYPE, "formatVersion")
		assertEquals(
			Note.Tap(Beat(1, 0, 1), -1),
			ChartParser.parseDifficulty(chartWithNote("""{ "type": "tap", "t": [1,0,1], "lane": -1 }""")).value?.notes?.single(),
		)
	}

	@Test
	fun theLastRepeatedKeyWins() {
		val note = ChartParser.parseDifficulty(chartWithNote("""{ "type": "tap", "t": [1,0,1], "lane": 0, "lane": 3 }"""))
		assertEquals(emptyList(), note.issues)
		assertEquals(Note.Tap(Beat(1, 0, 1), 3), note.value?.notes?.single())
	}

	@Test
	fun filesOverTheSizeLimitAreRejectedBeforeReading() {
		val limit = ChartLimits.MAX_JSON_FILE_CHARS
		assertEquals(2 * 1024 * 1024, limit)
		assertSingleError(ChartParser.parseDifficulty(" ".repeat(limit + 1)), IssueCode.FILE_TOO_LARGE, "$")
		assertSingleError(ChartParser.parseMeta(" ".repeat(limit + 1)), IssueCode.FILE_TOO_LARGE, "$")
		assertSingleError(ChartParser.parseDifficulty("[".repeat(limit + 1)), IssueCode.FILE_TOO_LARGE, "$")
	}

	@Test
	fun fileAtTheSizeLimitIsRead() {
		val padded = specExample + " ".repeat(ChartLimits.MAX_JSON_FILE_CHARS - specExample.length)
		assertEquals(emptyList(), ChartParser.parseDifficulty(padded).issues)
	}

	@Test
	fun theWorstFileUnderTheLimitStaysBounded() {
		val start = """{ "formatVersion": 1, "difficulty": "BASIC", "level": 1, "laneCount": 4, "offsetMs": 0, "bpm": [], "meter": [], "notes": ["""
		val end = "1] }"
		val items = (ChartLimits.MAX_JSON_FILE_CHARS - start.length - end.length) / 2
		val result = ChartParser.parseDifficulty(start + "1,".repeat(items) + end)
		assertNull(result.value)
		assertEquals(ChartLimits.MAX_PARSE_ISSUES + 1, result.issues.size)
	}

	@Test
	fun theHeaviestNestingUnderTheLimitIsStillReported() {
		val start = """{ "formatVersion": 1, "extra": ["""
		val end = "1] }"
		val item = "[".repeat(30) + "1" + "]".repeat(30) + ","
		val items = (ChartLimits.MAX_JSON_FILE_CHARS - start.length - end.length) / item.length
		val result = ChartParser.parseDifficulty(start + item.repeat(items) + end)
		assertNull(result.value)
		assertTrue(result.issues.any { it.code == IssueCode.MISSING_FIELD })
	}

	private fun notesText(count: Int, note: String) = chartWithNote(List(count) { note }.joinToString())

	@Test
	fun issuesStopAtTheCapWithAFinalIssue() {
		val limit = ChartLimits.MAX_PARSE_ISSUES
		assertEquals(100, limit)

		val atCap = ChartParser.parseDifficulty(notesText(limit, "1"))
		assertEquals(limit, atCap.issues.size)
		assertTrue(atCap.issues.none { it.code == IssueCode.TOO_MANY_PROBLEMS })

		val over = ChartParser.parseDifficulty(notesText(limit + 50, "1"))
		assertNull(over.value)
		assertEquals(limit + 1, over.issues.size)
		assertEquals(IssueCode.WRONG_TYPE, over.issues[limit - 1].code)
		assertEquals(IssueCode.TOO_MANY_PROBLEMS, over.issues[limit].code)
		assertEquals(Severity.ERROR, over.issues[limit].severity)
	}

	@Test
	fun tooManyWarningsStillLoadTheChart() {
		val fields = List(ChartLimits.MAX_PARSE_ISSUES + 5) { """"extra$it": 0""" }.joinToString()
		val result = ChartParser.parseDifficulty(chartWithNote("""{ "type": "tap", "t": [1,0,1], "lane": 0, $fields }"""))
		assertNotNull(result.value)
		assertEquals(ChartLimits.MAX_PARSE_ISSUES + 1, result.issues.size)
		assertEquals(IssueCode.TOO_MANY_PROBLEMS, result.issues.last().code)
		assertEquals(Severity.WARNING, result.issues.last().severity)
	}

	@Test
	fun anErrorPastTheCapStillFailsAndSaysSo() {
		val fields = List(ChartLimits.MAX_PARSE_ISSUES + 5) { """"extra$it": 0""" }.joinToString()
		val result = ChartParser.parseDifficulty(chartWithNote("""{ "type": "tap", "t": [1,0,1], "lane": 0, $fields }, 1"""))
		assertNull(result.value)
		assertEquals(ChartLimits.MAX_PARSE_ISSUES + 1, result.issues.size)
		assertTrue(result.issues.any { it.severity == Severity.ERROR })
		assertEquals(Severity.ERROR, result.issues.last().severity)
	}

	@Test
	fun longTextInIssuesIsShortened() {
		val long = "x".repeat(10_000)
		val limit = ChartLimits.MAX_ISSUE_TEXT_CHARS
		val type = ChartParser.parseDifficulty(chartWithNote("""{ "type": "$long" }""")).issues.single()
		assertTrue(type.message.length < limit * 2, type.message.length.toString())

		val difficulty = ChartParser.parseDifficulty(specExample.replace("EXPERT", long)).issues.single()
		assertTrue(difficulty.message.length < limit * 2, difficulty.message.length.toString())

		val field = ChartParser.parseDifficulty(specExample.replace(""""level": 11,""", """"level": 11, "$long": 0,""")).issues.single()
		assertTrue(field.path.length < limit * 2, field.path.length.toString())

		val broken = ChartParser.parseDifficulty("{ \"$long").issues.single()
		assertTrue(broken.message.length < limit * 2, broken.message.length.toString())
	}

	@Test
	fun rootMustBeAnObject() {
		assertSingleError(ChartParser.parseDifficulty("[]"), IssueCode.WRONG_TYPE, "$")
	}

	@Test
	fun unknownFormatVersionStopsParsing() {
		val text = """{ "formatVersion": 2, "somethingNew": true }"""
		assertSingleError(ChartParser.parseDifficulty(text), IssueCode.UNKNOWN_FORMAT_VERSION, "formatVersion")
		assertSingleError(ChartParser.parseMeta(text), IssueCode.UNKNOWN_FORMAT_VERSION, "formatVersion")
	}

	@Test
	fun missingFormatVersionStopsParsing() {
		assertSingleError(ChartParser.parseDifficulty("{}"), IssueCode.MISSING_FIELD, "formatVersion")
		assertSingleError(
			ChartParser.parseDifficulty("""{ "formatVersion": "1" }"""),
			IssueCode.WRONG_TYPE,
			"formatVersion",
		)
	}

	@Test
	fun missingFieldsNameTheirPath() {
		assertSingleError(
			ChartParser.parseDifficulty(specExample.replace(""""level": 11,""", "")),
			IssueCode.MISSING_FIELD,
			"level",
		)
		assertSingleError(
			ChartParser.parseDifficulty(chartWithNote("""{ "type": "tap", "t": [1,0,1] }""")),
			IssueCode.MISSING_FIELD,
			"notes[0].lane",
		)
		assertSingleError(
			ChartParser.parseMeta(metaExample.replace(""""type": "permitted", """, "")),
			IssueCode.MISSING_FIELD,
			"license.type",
		)
	}

	@Test
	fun everyNoteFieldIsRequiredExceptDir() {
		val notes = listOf(
			"""{ "type": "tap", "t": [1,0,1], "lane": 0 }""" to listOf("t", "lane"),
			"""{ "type": "hold", "t": [1,0,1], "end": [2,0,1], "lane": 0 }""" to listOf("t", "end", "lane"),
			"""{ "type": "skyTap", "t": [1,0,1], "x": 0, "y": 0, "button": "L", "size": 1 }""" to
				listOf("t", "x", "y", "button", "size"),
			"""{ "type": "slide", "button": "L", "radius": 0.1, "points": [] }""" to
				listOf("button", "radius", "points"),
			"""{ "type": "flick", "t": [1,0,1], "x": 0, "y": 0, "dir": 0, "size": 1 }""" to
				listOf("t", "x", "y", "size"),
		)
		for ((note, fields) in notes) {
			assertEquals(emptyList(), ChartParser.parseDifficulty(chartWithNote(note)).issues, note)
			for (field in fields) {
				val without = note.replace(Regex(""", "$field": (\[[^]]*]|"[^"]*"|[\d.]+)"""), "")
				assertSingleError(
					ChartParser.parseDifficulty(chartWithNote(without)),
					IssueCode.MISSING_FIELD,
					"notes[0].$field",
				)
			}
		}
	}

	@Test
	fun slidePointFieldsAreRequired() {
		val slide = """{ "type": "slide", "button": "L", "radius": 0.1, "points": [ { "t": [1,0,1], "x": 0, "y": 0 } ] }"""
		assertSingleError(
			ChartParser.parseDifficulty(chartWithNote(slide)),
			IssueCode.MISSING_FIELD,
			"notes[0].points[0].ease",
		)
	}

	@Test
	fun wrongTypesAreReported() {
		val cases = listOf(
			""""lane": "0"""" to "notes[0].lane",
			""""lane": 1.5""" to "notes[0].lane",
			""""lane": 99999999999""" to "notes[0].lane",
			""""lane": true""" to "notes[0].lane",
			""""lane": null""" to "notes[0].lane",
		)
		for ((lane, path) in cases) {
			assertSingleError(
				ChartParser.parseDifficulty(chartWithNote("""{ "type": "tap", "t": [1,0,1], $lane }""")),
				IssueCode.WRONG_TYPE,
				path,
			)
		}
		assertSingleError(
			ChartParser.parseDifficulty(chartWithNote("").replace("[  ]", "1").replace("[ ]", "1")),
			IssueCode.WRONG_TYPE,
			"notes",
		)
		assertSingleError(ChartParser.parseDifficulty(chartWithNote("1")), IssueCode.WRONG_TYPE, "notes[0]")
	}

	@Test
	fun numbersMustBeFinite() {
		assertSingleError(
			ChartParser.parseDifficulty(specExample.replace(""""offsetMs": 120""", """"offsetMs": 1e999""")),
			IssueCode.WRONG_TYPE,
			"offsetMs",
		)
	}

	@Test
	fun beatsMustBeThreeWholeNumbers() {
		for (beat in listOf("[1,0]", "[1,0,1,0]", "[1,0.5,1]", """[1,"0",1]""", "4", "[99999999999,0,1]")) {
			assertSingleError(
				ChartParser.parseDifficulty(chartWithNote("""{ "type": "tap", "t": $beat, "lane": 0 }""")),
				IssueCode.WRONG_TYPE,
				"notes[0].t",
			)
		}
	}

	@Test
	fun unknownNamesAreReportedWithTheirChoices() {
		val noteType = ChartParser.parseDifficulty(chartWithNote("""{ "type": "laser" }"""))
		assertSingleError(noteType, IssueCode.UNKNOWN_VALUE, "notes[0].type")
		assertTrue("laser" in noteType.issues[0].message)
		assertTrue("flick" in noteType.issues[0].message)

		val difficulty = ChartParser.parseDifficulty(specExample.replace("EXPERT", "expert"))
		assertSingleError(difficulty, IssueCode.UNKNOWN_VALUE, "difficulty")
		assertTrue("MASTER" in difficulty.issues[0].message)

		assertSingleError(
			ChartParser.parseDifficulty(specExample.replace(""""button": "L", "size"""", """"button": "M", "size"""")),
			IssueCode.UNKNOWN_VALUE,
			"notes[2].button",
		)
		assertSingleError(
			ChartParser.parseDifficulty(specExample.replace("inOut", "bounce")),
			IssueCode.UNKNOWN_VALUE,
			"notes[3].points[1].ease",
		)
		assertSingleError(
			ChartParser.parseMeta(metaExample.replace("permitted", "stolen")),
			IssueCode.UNKNOWN_VALUE,
			"license.type",
		)
		assertSingleError(
			ChartParser.parseMeta(metaExample.replace("BASIC", "EASY")),
			IssueCode.UNKNOWN_VALUE,
			"difficulties[0]",
		)
	}

	@Test
	fun unknownFieldsWarnAndStillLoad() {
		val result = ChartParser.parseDifficulty(
			specExample
				.replace(""""level": 11,""", """"level": 11, "mood": "calm",""")
				.replace(""""lane": 0 }""", """"lane": 0, "colour": 3 }"""),
		)
		assertNotNull(result.value)
		assertEquals(listOf("mood", "notes[0].colour"), result.issues.map { it.path })
		for (issue in result.issues) {
			assertEquals(Severity.WARNING, issue.severity)
			assertEquals(IssueCode.UNKNOWN_FIELD, issue.code)
		}
		assertEquals(Note.Tap(Beat(4, 0, 1), 0), result.value.notes.first())
	}

	@Test
	fun unknownFieldsWarnInMeta() {
		val result = ChartParser.parseMeta(metaExample.replace(""""type": "permitted",""", """"type": "permitted", "year": 2026,"""))
		assertNotNull(result.value)
		assertEquals("license.year", result.issues.single().path)
	}

	@Test
	fun everyErrorInAFileIsReported() {
		val result = ChartParser.parseDifficulty(
			"""{ "formatVersion": 1, "level": "x", "laneCount": 4, "offsetMs": 0, "bpm": [], "meter": [],
			    "notes": [ { "type": "tap", "t": [1,0,1] }, { "type": "laser" } ] }""",
		)
		assertNull(result.value)
		assertEquals(
			listOf("difficulty", "level", "notes[0].lane", "notes[1].type"),
			result.issues.map { it.path },
		)
	}
}
