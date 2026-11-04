package net.trilleo.hexium.core.settings

import kotlin.math.roundToLong
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.put

/**
 * What a player has set, as stored in `settings.json`. Note speed is in blocks per second. The offsets
 * are A and B in docs/JUDGEMENT.md. Reading never fails: a player must always be able to start the game.
 * A later format version must still read these fields the same way, since nothing here checks the version.
 */
data class PlayerSettings(
	val noteSpeed: Int = DEFAULT_NOTE_SPEED,
	val visualOffsetMs: Int = 0,
	val judgementOffsetMs: Int = 0,
) {
	fun inRange() = PlayerSettings(
		noteSpeed.coerceIn(MIN_NOTE_SPEED, MAX_NOTE_SPEED),
		visualOffsetMs.coerceIn(-MAX_OFFSET_MS, MAX_OFFSET_MS),
		judgementOffsetMs.coerceIn(-MAX_OFFSET_MS, MAX_OFFSET_MS),
	)

	companion object {
		const val DEFAULT_NOTE_SPEED = 25
		const val MIN_NOTE_SPEED = 10
		const val MAX_NOTE_SPEED = 120
		const val MAX_OFFSET_MS = 500

		private const val FORMAT_VERSION = 1
		private val JSON = Json { prettyPrint = true }

		/** Broken text gives the defaults. A broken field gives its own default. Everything comes back in range. */
		fun parse(text: String): PlayerSettings {
			val root = try {
				Json.parseToJsonElement(text) as? JsonObject
			} catch (_: SerializationException) {
				null
			} catch (_: IllegalArgumentException) {
				null
			} catch (_: StackOverflowError) {
				null
			} ?: return PlayerSettings()
			return PlayerSettings(
				number(root, "noteSpeed", DEFAULT_NOTE_SPEED),
				number(root, "visualOffsetMs", 0),
				number(root, "judgementOffsetMs", 0),
			).inRange()
		}

		fun write(settings: PlayerSettings): String = JSON.encodeToString(
			JsonObject.serializer(),
			buildJsonObject {
				put("formatVersion", FORMAT_VERSION)
				put("noteSpeed", settings.noteSpeed)
				put("visualOffsetMs", settings.visualOffsetMs)
				put("judgementOffsetMs", settings.judgementOffsetMs)
			},
		)

		private fun number(root: JsonObject, name: String, default: Int): Int {
			val primitive = root[name] as? JsonPrimitive ?: return default
			if (primitive.isString) return default
			val value = primitive.doubleOrNull ?: return default
			if (!value.isFinite()) return default
			return value.roundToLong().coerceIn(Int.MIN_VALUE.toLong(), Int.MAX_VALUE.toLong()).toInt()
		}
	}
}
