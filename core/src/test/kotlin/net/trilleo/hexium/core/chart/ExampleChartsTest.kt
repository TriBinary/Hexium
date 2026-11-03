package net.trilleo.hexium.core.chart

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** The hand-written charts in docs/examples and the examples in the spec must stay valid. */
class ExampleChartsTest {
	private val docs = File("../docs")

	private fun text(name: String) = File(docs, "examples/$name.json").readText()

	private fun chart(name: String): DifficultyChart {
		val result = ChartParser.parseDifficulty(text(name))
		assertEquals(emptyList(), result.issues, name)
		return result.value!!
	}

	private val names = listOf("basic", "advanced", "expert")

	@Test
	fun examplesPassTheValidatorWithoutWarnings() {
		for (name in names) assertEquals(emptyList(), ChartValidator.validateChart(chart(name)), name)
	}

	@Test
	fun examplesAreNamedAfterTheirDifficulty() {
		for (name in names) assertEquals(name, chart(name).difficulty.name.lowercase())
	}

	@Test
	fun examplesAreWrittenInTheWritersLayout() {
		for (name in names) assertEquals(text(name), ChartWriter.writeDifficulty(chart(name)), name)
	}

	@Test
	fun examplesFormAValidPackage() {
		val meta = ChartMeta(
			formatVersion = 1,
			id = "example",
			title = "Example",
			artist = "Example",
			charter = "Example",
			previewStartMs = 0,
			license = License(LicenseType.ORIGINAL, null),
			difficulties = listOf(Difficulty.BASIC, Difficulty.ADVANCED, Difficulty.EXPERT),
		)
		val pkg = ChartPackage(meta, names.map { chart(it) })
		assertEquals(emptyList(), ChartValidator.validatePackage(pkg, audioDurationMs = 60000.0))
	}

	@Test
	fun basicIsGroundNotesOnly() {
		val notes = chart("basic").notes
		assertTrue(notes.any { it is Note.Tap })
		assertTrue(notes.any { it is Note.Hold })
		assertTrue(notes.all { it is Note.Tap || it is Note.Hold })
	}

	@Test
	fun advancedAddsSkyTapsAndASlide() {
		val notes = chart("advanced").notes
		assertTrue(notes.any { it is Note.Tap })
		assertTrue(notes.any { it is Note.Hold })
		assertTrue(notes.any { it is Note.SkyTap })
		assertTrue(notes.any { it is Note.Slide })
		assertTrue(notes.none { it is Note.Flick })
	}

	@Test
	fun expertUsesEveryNoteTypeAndTimingChange() {
		val chart = chart("expert")
		assertTrue(chart.notes.any { it is Note.Tap })
		assertTrue(chart.notes.any { it is Note.Hold })
		assertTrue(chart.notes.any { it is Note.SkyTap })
		assertTrue(chart.notes.any { it is Note.Slide })
		assertTrue(chart.notes.any { it is Note.Flick })
		assertTrue(chart.bpm.size > 1)
		assertTrue(chart.meter.size > 1)
	}

	@Test
	fun theSpecsExamplesAreValid() {
		val blocks = Regex("```json\\n(.*?)```", RegexOption.DOT_MATCHES_ALL)
			.findAll(File(docs, "CHART_FORMAT.md").readText())
			.map { it.groupValues[1] }
			.toList()
		assertEquals(2, blocks.size)

		val meta = ChartParser.parseMeta(blocks[0])
		assertEquals(emptyList(), meta.issues)
		assertEquals(emptyList(), ChartValidator.validateMeta(meta.value!!))

		val chart = ChartParser.parseDifficulty(blocks[1])
		assertEquals(emptyList(), chart.issues)
		assertEquals(emptyList(), ChartValidator.validateChart(chart.value!!))
	}
}
