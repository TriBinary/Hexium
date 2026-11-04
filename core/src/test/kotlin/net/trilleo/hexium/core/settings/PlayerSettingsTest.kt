package net.trilleo.hexium.core.settings

import kotlin.test.Test
import kotlin.test.assertEquals

class PlayerSettingsTest {
	private val custom = PlayerSettings(noteSpeed = 60, visualOffsetMs = -35, judgementOffsetMs = 42)

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
		val clamped = PlayerSettings(noteSpeed = 1, visualOffsetMs = 9999, judgementOffsetMs = -9999).inRange()
		assertEquals(PlayerSettings(PlayerSettings.MIN_NOTE_SPEED, PlayerSettings.MAX_OFFSET_MS, -PlayerSettings.MAX_OFFSET_MS), clamped)
	}
}
