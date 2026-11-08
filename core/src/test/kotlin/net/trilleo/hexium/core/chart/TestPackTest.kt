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
		val pkg = ChartPackage(meta(), listOf(chart("basic"), chart("advanced"), chart("expert")))
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
	fun theChartsAreWrittenInTheWritersLayout() {
		for (name in listOf("advanced", "expert")) assertEquals(File(pack, "$name.json").readText(), ChartWriter.writeDifficulty(chart(name)), name)
	}

	/** Expert is the Milestone 3 readability chart, so it needs every sky note, on both buttons. */
	@Test
	fun expertHasEverySkyNoteOnBothButtons() {
		val notes = chart("expert").notes
		for (button in SkyButton.entries) {
			assertTrue(notes.any { it is Note.SkyTap && it.button == button }, "$button sky tap")
			assertTrue(notes.any { it is Note.Slide && it.button == button }, "$button slide")
		}
		assertTrue(notes.any { it is Note.Flick }, "flick")
	}

	/** The gate asks whether the track stays readable while aiming at the corners. */
	@Test
	fun expertReachesEveryCornerWhileGroundNotesAreComing() {
		val chart = chart("expert")
		val sky = SkyTrack(chart)
		val ground = GroundTrack(chart)
		for ((cornerX, cornerY) in listOf(0.1 to 0.9, 0.9 to 0.9, 0.1 to 0.1, 0.9 to 0.1)) {
			assertTrue((0 until sky.size).any { sky.x(it, sky.startMs(it)) == cornerX && sky.y(it, sky.startMs(it)) == cornerY }, "corner $cornerX, $cornerY")
		}
		// Each top corner within a beat of a ground note.
		for (cornerX in listOf(0.1, 0.9)) {
			assertTrue(
				(0 until sky.size).any { i ->
					val ms = sky.startMs(i)
					sky.x(i, ms) == cornerX && sky.y(i, ms) == 0.9 && (0 until ground.size).any { Math.abs(ground.startMs(it) - ms) <= 500.0 }
				},
				"top corner at x $cornerX with a ground note near it",
			)
		}
	}

	/** Every hit area fits on the screen, so nothing asks the crosshair to leave it. */
	@Test
	fun expertsHitAreasStayOnTheScreen() {
		val sky = SkyTrack(chart("expert"))
		for (i in 0 until sky.size) {
			val halfWidth = sky.radius(i) / sky.screenAspect
			var step = 0
			while (step <= 16) {
				val ms = sky.startMs(i) + (sky.endMs(i) - sky.startMs(i)) * step / 16.0
				val x = sky.x(i, ms)
				val y = sky.y(i, ms)
				assertTrue(x - halfWidth >= 0.0 && x + halfWidth <= 1.0 && y - sky.radius(i) >= 0.0 && y + sky.radius(i) <= 1.0, "note ${sky.chartNote(i)} at $x, $y")
				step++
			}
		}
	}

	/**
	 * One crosshair can't be in two places: sky notes at the same moment share a spot, and a tap or
	 * flick while a slide is going sits inside the slide's hit area.
	 */
	@Test
	fun expertsSkyNotesAtTheSameTimeShareASpot() {
		val sky = SkyTrack(chart("expert"))
		for (i in 0 until sky.size) {
			if (sky.kind(i) == SkyTrack.Kind.SLIDE) continue
			val ms = sky.startMs(i)
			for (j in 0 until sky.size) {
				if (sky.kind(j) != SkyTrack.Kind.SLIDE || ms < sky.startMs(j) || ms > sky.endMs(j)) continue
				val dx = (sky.x(i, ms) - sky.x(j, ms)) * sky.screenAspect
				val dy = sky.y(i, ms) - sky.y(j, ms)
				assertTrue(dx * dx + dy * dy <= sky.radius(j) * sky.radius(j), "note ${sky.chartNote(i)} off slide ${sky.chartNote(j)}")
			}
		}
		for (i in 0 until sky.size) {
			for (j in i + 1 until sky.size) {
				if (sky.startMs(j) != sky.startMs(i)) continue
				assertEquals(sky.x(i, sky.startMs(i)), sky.x(j, sky.startMs(j)), "notes ${sky.chartNote(i)} and ${sky.chartNote(j)}")
				assertEquals(sky.y(i, sky.startMs(i)), sky.y(j, sky.startMs(j)), "notes ${sky.chartNote(i)} and ${sky.chartNote(j)}")
			}
		}
	}

	@Test
	fun groundOnlyTellsChartsWithSkyNotesApart() {
		assertTrue(chart("basic").isGroundOnly)
		assertTrue(chart("advanced").isGroundOnly)
		assertFalse(chart("expert").isGroundOnly)
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
			Note.Flick(beat, 0.5, 0.5, 1.0),
		)
		for (note in skyNotes) assertFalse(empty.copy(notes = listOf(note)).isGroundOnly, note.toString())
	}

	private fun packBytes(): Long = pack.listFiles().orEmpty().sumOf { it.length() }

	private companion object {
		const val AUDIO_MS = 185_000.0
	}
}
