package net.trilleo.hexium.core.chart

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ChartPackageValidatorTest {
	private val meta = ChartMeta(
		formatVersion = 1,
		id = "example-song-2",
		title = "Example Song",
		artist = "Example Artist",
		charter = "Example Charter",
		previewStartMs = 0,
		license = License(LicenseType.ORIGINAL, null),
		difficulties = listOf(Difficulty.BASIC, Difficulty.EXPERT),
	)

	private fun chart(difficulty: Difficulty, vararg notes: Note) = DifficultyChart(
		formatVersion = 1,
		difficulty = difficulty,
		level = 1,
		laneCount = 4,
		offsetMs = 0.0,
		bpm = listOf(BpmChange(Beat(0, 0, 1), 120.0)),
		meter = listOf(MeterChange(Beat(0, 0, 1), 4, 4)),
		notes = notes.toList(),
	)

	private val pkg = ChartPackage(meta, listOf(chart(Difficulty.BASIC), chart(Difficulty.EXPERT)))

	private fun assertOnlyError(issues: List<ChartIssue>, code: IssueCode, path: String) {
		assertEquals(1, issues.size, issues.toString())
		assertEquals(code, issues[0].code)
		assertEquals(path, issues[0].path)
		assertEquals(Severity.ERROR, issues[0].severity)
		assertTrue(issues[0].message.isNotBlank())
	}

	private fun assertMetaError(meta: ChartMeta, code: IssueCode, path: String) {
		assertOnlyError(ChartValidator.validateMeta(meta), code, path)
	}

	@Test
	fun goodMetaPasses() = assertEquals(emptyList(), ChartValidator.validateMeta(meta))

	@Test
	fun goodPackagePasses() = assertEquals(emptyList(), ChartValidator.validatePackage(pkg))

	@Test
	fun unknownFormatVersionFails() =
		assertMetaError(meta.copy(formatVersion = 2), IssueCode.UNKNOWN_FORMAT_VERSION, "formatVersion")

	@Test
	fun idLimitsPass() {
		assertEquals(emptyList(), ChartValidator.validateMeta(meta.copy(id = "a")))
		assertEquals(emptyList(), ChartValidator.validateMeta(meta.copy(id = "a".repeat(64))))
		assertEquals(emptyList(), ChartValidator.validateMeta(meta.copy(id = "0-9-a-z")))
	}

	@Test
	fun badIdsFail() {
		for (id in listOf("", "a".repeat(65), "Upper", "under_score", "with space", "sláinte", "../evil")) {
			assertMetaError(meta.copy(id = id), IssueCode.BAD_ID, "id")
		}
	}

	@Test
	fun longBadIdsAreShortenedInTheMessage() {
		val issue = ChartValidator.validateMeta(meta.copy(id = "X".repeat(10_000))).single()
		assertTrue(issue.message.length < ChartLimits.MAX_ISSUE_TEXT_CHARS * 2, issue.message.length.toString())
	}

	@Test
	fun fileNamedAfterItsDifficultyPasses() {
		assertEquals(emptyList(), ChartValidator.validateFileName(chart(Difficulty.BASIC), "basic.json"))
		assertEquals("master.json", ChartValidator.fileName(Difficulty.MASTER))
	}

	@Test
	fun fileNamedForAnotherDifficultyFails() {
		assertOnlyError(
			ChartValidator.validateFileName(chart(Difficulty.BASIC), "expert.json"),
			IssueCode.FILE_NAME_MISMATCH,
			"$",
		)
	}

	@Test
	fun blankTextFails() {
		assertMetaError(meta.copy(title = ""), IssueCode.BLANK_TEXT, "title")
		assertMetaError(meta.copy(artist = "  "), IssueCode.BLANK_TEXT, "artist")
		assertMetaError(meta.copy(charter = "\t"), IssueCode.BLANK_TEXT, "charter")
	}

	@Test
	fun previewAtZeroOrLaterPasses() {
		assertEquals(emptyList(), ChartValidator.validateMeta(meta.copy(previewStartMs = 90000)))
	}

	@Test
	fun negativePreviewFails() =
		assertMetaError(meta.copy(previewStartMs = -1), IssueCode.NEGATIVE_PREVIEW, "previewStartMs")

	@Test
	fun noDifficultiesFails() = assertMetaError(meta.copy(difficulties = emptyList()), IssueCode.NO_DIFFICULTIES, "difficulties")

	@Test
	fun repeatedDifficultyFails() {
		assertMetaError(
			meta.copy(difficulties = listOf(Difficulty.BASIC, Difficulty.BASIC)),
			IssueCode.REPEATED_DIFFICULTY,
			"difficulties[1]",
		)
	}

	@Test
	fun metaIssuesInAPackageNameTheFile() {
		assertOnlyError(
			ChartValidator.validatePackage(pkg.copy(meta = meta.copy(title = ""))),
			IssueCode.BLANK_TEXT,
			"meta.json:title",
		)
	}

	@Test
	fun listedDifficultyWithoutAFileFails() {
		assertOnlyError(
			ChartValidator.validatePackage(pkg.copy(charts = listOf(chart(Difficulty.BASIC)))),
			IssueCode.MISSING_DIFFICULTY_FILE,
			"meta.json:difficulties[1]",
		)
	}

	@Test
	fun fileNotListedFails() {
		assertOnlyError(
			ChartValidator.validatePackage(pkg.copy(charts = pkg.charts + chart(Difficulty.MASTER))),
			IssueCode.UNLISTED_DIFFICULTY_FILE,
			"master.json",
		)
	}

	@Test
	fun twoFilesForOneDifficultyFail() {
		assertOnlyError(
			ChartValidator.validatePackage(pkg.copy(charts = pkg.charts + chart(Difficulty.BASIC))),
			IssueCode.DUPLICATE_DIFFICULTY_FILE,
			"basic.json",
		)
	}

	@Test
	fun chartIssuesInAPackageNameTheFile() {
		val bad = chart(Difficulty.EXPERT, Note.Tap(Beat(1, 0, 1), 9))
		assertOnlyError(
			ChartValidator.validatePackage(pkg.copy(charts = listOf(chart(Difficulty.BASIC), bad))),
			IssueCode.LANE_OUT_OF_RANGE,
			"expert.json:notes[0].lane",
		)
	}

	@Test
	fun audioLengthReachesEveryChart() {
		val late = chart(Difficulty.EXPERT, Note.Tap(Beat(4, 0, 1), 0))
		val withLate = pkg.copy(charts = listOf(chart(Difficulty.BASIC), late))
		assertEquals(emptyList(), ChartValidator.validatePackage(withLate, audioDurationMs = 2000.5))
		assertOnlyError(
			ChartValidator.validatePackage(withLate, audioDurationMs = 2000.0),
			IssueCode.NOTE_AFTER_AUDIO,
			"expert.json:notes[0]",
		)
	}

	@Test
	fun packageUnderTheLimitPasses() {
		assertEquals(emptyList(), ChartValidator.validatePackage(pkg, packageSizeBytes = ChartLimits.MAX_PACKAGE_BYTES - 1))
	}

	@Test
	fun packageSizeIsSkippedWhenNotGiven() {
		assertEquals(emptyList(), ChartValidator.validatePackage(pkg, packageSizeBytes = null))
	}

	@Test
	fun packageAtOrOverTheLimitFails() {
		assertEquals(30L * 1024 * 1024, ChartLimits.MAX_PACKAGE_BYTES)
		for (size in listOf(ChartLimits.MAX_PACKAGE_BYTES, ChartLimits.MAX_PACKAGE_BYTES + 1)) {
			assertOnlyError(ChartValidator.validatePackage(pkg, packageSizeBytes = size), IssueCode.PACKAGE_TOO_LARGE, "$")
		}
	}
}
