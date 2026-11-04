package net.trilleo.hexium.core.chart

import java.io.File
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** The pack bundled in the mod, used to play and test before there's song select. */
class TestPackTest {
	private val pack = File("../mod/src/client/resources/hexium_packs/test-pattern")

	private fun chart(name: String): DifficultyChart {
		val result = ChartParser.parseDifficulty(File(pack, "$name.json").readText())
		assertEquals(emptyList(), result.issues, name)
		return assertNotNull(result.value, name)
	}

	private fun meta(): ChartMeta {
		val result = ChartParser.parseMeta(File(pack, "meta.json").readText())
		assertEquals(emptyList(), result.issues)
		return assertNotNull(result.value)
	}

	@Test
	fun packPassesTheValidatorWithoutWarnings() {
		val pkg = ChartPackage(meta(), listOf(chart("basic"), chart("advanced")))
		assertEquals(emptyList(), ChartValidator.validatePackage(pkg, audioDurationMs = AUDIO_MS, packageSizeBytes = packBytes()))
	}

	@Test
	fun theSongIsOurOwn() {
		assertEquals(LicenseType.ORIGINAL, meta().license.type)
		assertTrue(File(pack, "audio.ogg").length() > 0)
	}

	/** Golden replays of real plays use this chart, and a replay is tied to its file's exact bytes. */
	@Test
	fun basicIsTheExampleChartByteForByte() {
		assertContentEquals(File("../docs/examples/basic.json").readBytes(), File(pack, "basic.json").readBytes())
	}

	/** The milestone's gate is no drift over a 3-minute song. */
	@Test
	fun advancedLastsThreeMinutes() {
		val chart = chart("advanced")
		val timing = TimingMap(chart.offsetMs, chart.bpm)
		val last = chart.notes.maxOf { note ->
			when (note) {
				is Note.Tap -> note.t
				is Note.Hold -> note.end
				else -> error("Sky note in the test pack")
			}.let(timing::beatToMs)
		}
		assertTrue(last >= 180_000.0, "ends at $last ms")
	}

	@Test
	fun theChartIsWrittenInTheWritersLayout() {
		assertEquals(File(pack, "advanced.json").readText(), ChartWriter.writeDifficulty(chart("advanced")))
	}

	@Test
	fun groundOnlyTellsChartsWithSkyNotesApart() {
		assertTrue(chart("basic").isGroundOnly)
		assertTrue(chart("advanced").isGroundOnly)
		val sky = ChartParser.parseDifficulty(File("../docs/examples/expert.json").readText()).value
		assertFalse(assertNotNull(sky).isGroundOnly)
	}

	@Test
	fun anySkyNoteMakesAChartNotGroundOnly() {
		val empty = chart("basic").copy(notes = emptyList())
		assertTrue(empty.isGroundOnly)
		val beat = Beat(4, 0, 1)
		val point = SlidePoint(beat, 0.5, 0.5, Ease.LINEAR)
		val skyNotes = listOf(
			Note.SkyTap(beat, 0.5, 0.5, SkyButton.L, 1.0),
			Note.Slide(SkyButton.L, 0.06, listOf(point, point)),
			Note.Flick(beat, 0.5, 0.5, null, 1.0),
		)
		for (note in skyNotes) assertFalse(empty.copy(notes = listOf(note)).isGroundOnly, note.toString())
	}

	private fun packBytes(): Long = pack.listFiles().orEmpty().sumOf { it.length() }

	private companion object {
		const val AUDIO_MS = 185_000.0
	}
}
