package net.trilleo.hexium.core.settings

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PlayerSettingsTest {
	private val custom = PlayerSettings(noteSpeed = 60, visualOffsetMs = -35, judgementOffsetMs = 42, mouseSensitivity = 150, playFov = 80, viewAssist = 3)

	@Test
	fun whatIsWrittenReadsBackTheSame() {
		assertEquals(custom, PlayerSettings.parse(PlayerSettings.write(custom)))
	}

	@Test
	fun brokenTextGivesTheDefaults() {
		assertEquals(PlayerSettings(), PlayerSettings.parse("not json {"))
		assertEquals(PlayerSettings(), PlayerSettings.parse("[1, 2]"))
		assertEquals(PlayerSettings(), PlayerSettings.parse(""))
	}

	@Test
	fun aMissingOrBrokenFieldGivesItsDefaultAndKeepsTheOthers() {
		val parsed = PlayerSettings.parse("""{ "noteSpeed": "fast", "visualOffsetMs": 20 }""")
		assertEquals(PlayerSettings(visualOffsetMs = 20), parsed)
	}

	@Test
	fun unknownFieldsAreIgnored() {
		assertEquals(PlayerSettings(noteSpeed = 30), PlayerSettings.parse("""{ "noteSpeed": 30, "later": { "a": 1 } }"""))
	}

	@Test
	fun valuesOutOfRangeComeBackInRange() {
		val parsed = PlayerSettings.parse("""{ "noteSpeed": 100000, "visualOffsetMs": -100000, "judgementOffsetMs": 100000 }""")
		assertEquals(PlayerSettings.MAX_NOTE_SPEED, parsed.noteSpeed)
		assertEquals(-PlayerSettings.MAX_OFFSET_MS, parsed.visualOffsetMs)
		assertEquals(PlayerSettings.MAX_OFFSET_MS, parsed.judgementOffsetMs)
	}

	@Test
	fun aNumberTooBigForAnIntComesBackInRange() {
		assertEquals(PlayerSettings.MAX_NOTE_SPEED, PlayerSettings.parse("""{ "noteSpeed": 99999999999999 }""").noteSpeed)
	}

	@Test
	fun aFractionalNumberIsRounded() {
		assertEquals(PlayerSettings(judgementOffsetMs = 13), PlayerSettings.parse("""{ "judgementOffsetMs": 12.6 }"""))
	}

	@Test
	fun anythingThatIsNotAFiniteNumberGivesTheDefault() {
		for (value in listOf("NaN", "Infinity", "-Infinity", "true", "null", "word", "\"30\"", "1e400", "[1]", "{}")) {
			assertEquals(PlayerSettings(visualOffsetMs = 5), PlayerSettings.parse("{ \"noteSpeed\": $value, \"visualOffsetMs\": 5 }"), value)
		}
	}

	@Test
	fun extremeNestingGivesTheDefaults() {
		assertEquals(PlayerSettings(), PlayerSettings.parse("[".repeat(200_000)))
	}

	@Test
	fun inRangeClampsEveryField() {
		val clamped = PlayerSettings(noteSpeed = 1, visualOffsetMs = 9999, judgementOffsetMs = -9999, mouseSensitivity = 1, playFov = 500, viewAssist = -2).inRange()
		val expected = PlayerSettings(
			PlayerSettings.MIN_NOTE_SPEED, PlayerSettings.MAX_OFFSET_MS, -PlayerSettings.MAX_OFFSET_MS,
			PlayerSettings.MIN_MOUSE_SENSITIVITY, PlayerSettings.MAX_PLAY_FOV, 0,
		)
		assertEquals(expected, clamped)
		val high = PlayerSettings(mouseSensitivity = 9999, playFov = 1, viewAssist = 99).inRange()
		assertEquals(PlayerSettings(mouseSensitivity = PlayerSettings.MAX_MOUSE_SENSITIVITY, playFov = PlayerSettings.MIN_PLAY_FOV, viewAssist = PlayerSettings.MAX_VIEW_ASSIST), high)
	}

	/** A new player turns the camera as vanilla does, sees vanilla's default view, and gets no help. */
	@Test
	fun comfortSettingsStartAsVanilla() {
		val settings = PlayerSettings()
		assertEquals(100, settings.mouseSensitivity)
		assertEquals(70, settings.playFov)
		assertEquals(0, settings.viewAssist)
	}

	/** A file from before these settings existed still reads, with their defaults. */
	@Test
	fun anOlderFileGetsTheComfortDefaults() {
		val parsed = PlayerSettings.parse("""{ "formatVersion": 1, "noteSpeed": 40, "visualOffsetMs": 5, "judgementOffsetMs": -5 }""")
		assertEquals(PlayerSettings(noteSpeed = 40, visualOffsetMs = 5, judgementOffsetMs = -5), parsed)
	}

	@Test
	fun comfortSettingsAreWrittenUnderTheirNames() {
		val text = PlayerSettings.write(custom)
		for (name in listOf("\"mouseSensitivity\": 150", "\"playFov\": 80", "\"viewAssist\": 3")) {
			assertTrue(name in text, "$name in $text")
		}
	}
}
