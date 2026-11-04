package net.trilleo.hexium.core.chart

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class ChartFolderTest {
	private val meta = File("../mod/src/client/resources/hexium_packs/test-pattern/meta.json").readText()
		.replace("""[ "BASIC", "ADVANCED" ]""", """[ "BASIC" ]""")
	private val basic = File("../docs/examples/basic.json").readText()

	private val audio = ChartFolder.AUDIO_FILE
	private val metaFile = ChartFolder.META_FILE

	private fun codes(result: ChartFolder.Result) = result.issues.map { it.issue.code }

	@Test
	fun aGoodFolderHasNoIssues() {
		val result = ChartFolder.read(
			listOf(ChartFolder.File(metaFile, meta), ChartFolder.File("basic.json", basic)),
			listOf(metaFile, "basic.json", audio),
		)
		assertEquals(emptyList(), result.issues)
		assertNotNull(result.meta)
		assertEquals(listOf(Difficulty.BASIC), result.charts.map { it.difficulty })
	}

	@Test
	fun missingMetaIsReportedEvenWithABrokenChart() {
		val result = ChartFolder.read(listOf(ChartFolder.File("basic.json", "{")), listOf("basic.json", audio))
		assertTrue(IssueCode.MISSING_META_FILE in codes(result))
		assertTrue(IssueCode.INVALID_JSON in codes(result))
	}

	@Test
	fun brokenMetaIsNotAlsoCalledMissing() {
		val result = ChartFolder.read(listOf(ChartFolder.File(metaFile, "{")), listOf(metaFile, audio))
		assertEquals(listOf(IssueCode.INVALID_JSON), codes(result))
	}

	@Test
	fun missingAudioIsAnError() {
		val result = ChartFolder.read(
			listOf(ChartFolder.File(metaFile, meta), ChartFolder.File("basic.json", basic)),
			listOf(metaFile, "basic.json"),
		)
		assertEquals(listOf(IssueCode.MISSING_AUDIO_FILE), codes(result))
	}

	/** The chart that failed already has an issue. Calling it missing too would be wrong. */
	@Test
	fun aChartThatCantBeReadIsNotAlsoCalledMissing() {
		for (text in listOf("{", null)) {
			val result = ChartFolder.read(
				listOf(ChartFolder.File(metaFile, meta), ChartFolder.File("basic.json", text)),
				listOf(metaFile, "basic.json", audio),
			)
			assertFalse(IssueCode.MISSING_DIFFICULTY_FILE in codes(result), "text $text")
		}
	}

	@Test
	fun aFileNamedForAnotherDifficultyIsAnError() {
		val result = ChartFolder.read(
			listOf(ChartFolder.File(metaFile, meta), ChartFolder.File("expert.json", basic)),
			listOf(metaFile, "expert.json", audio),
		)
		assertTrue(IssueCode.FILE_NAME_MISMATCH in codes(result))
	}

	@Test
	fun anOversizedPackageIsAnError() {
		val result = ChartFolder.read(
			listOf(ChartFolder.File(metaFile, meta), ChartFolder.File("basic.json", basic)),
			listOf(metaFile, "basic.json", audio),
			packageSizeBytes = ChartLimits.MAX_PACKAGE_BYTES,
		)
		assertEquals(listOf(IssueCode.PACKAGE_TOO_LARGE), codes(result))
	}

	@Test
	fun onlyJsonFilesThatArentHiddenAreChartFiles() {
		assertTrue(ChartFolder.isChartFile("basic.json"))
		assertTrue(ChartFolder.isChartFile(metaFile))
		assertFalse(ChartFolder.isChartFile("._basic.json"))
		assertFalse(ChartFolder.isChartFile(audio))
	}

	@Test
	fun aFileIsTooLargeToReadOnlyPastTheLimitInBytes() {
		val limit = ChartLimits.MAX_JSON_FILE_CHARS * 4L
		assertFalse(ChartFolder.isTooLargeToRead(limit))
		assertTrue(ChartFolder.isTooLargeToRead(limit + 1))
	}
}
