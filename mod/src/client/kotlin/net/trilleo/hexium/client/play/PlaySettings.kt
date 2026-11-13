package net.trilleo.hexium.client.play

import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import net.fabricmc.loader.api.FabricLoader
import net.trilleo.hexium.Hexium
import net.trilleo.hexium.core.settings.PlayerSettings
import org.slf4j.LoggerFactory

/** The player's settings, kept in `.minecraft/hexium/settings.json`. Every change is saved straight away. */
object PlaySettings {
	private val LOGGER = LoggerFactory.getLogger(Hexium.MOD_ID)
	private const val NOTE_SPEED_STEP = 5
	private const val OFFSET_STEP_MS = 5
	private const val SENSITIVITY_STEP = 10
	private const val FOV_STEP = 5

	private var settings = PlayerSettings()

	/** Blocks per second. It changes how far away notes appear, never when they arrive. */
	val noteSpeed: Int
		get() = settings.noteSpeed

	/** Offset A in docs/JUDGEMENT.md. Positive draws notes later. Judging doesn't see it. */
	val visualOffsetMs: Int
		get() = settings.visualOffsetMs

	/** Offset B in docs/JUDGEMENT.md. A play keeps the value it started with, and its replay records it. */
	val judgementOffsetMs: Int
		get() = settings.judgementOffsetMs

	/** Percent of vanilla's mouse sensitivity, during a play only. */
	val mouseSensitivity: Int
		get() = settings.mouseSensitivity

	/** The field of view on the stage, in degrees. */
	val playFov: Int
		get() = settings.playFov

	/** How strongly the camera eases back to the rest view when no sky notes are near, 0 for off. */
	val viewAssist: Int
		get() = settings.viewAssist

	/** Call once at startup. A file that can't be read leaves the defaults, and is replaced on the next change. */
	fun load() {
		val file = file()
		if (!Files.exists(file)) return
		try {
			settings = PlayerSettings.parse(Files.readString(file))
		} catch (e: IOException) {
			LOGGER.warn("Can't read {}", file, e)
		}
	}

	fun changeNoteSpeed(steps: Int) = set(settings.copy(noteSpeed = settings.noteSpeed + steps * NOTE_SPEED_STEP))

	fun changeMouseSensitivity(steps: Int) = set(settings.copy(mouseSensitivity = settings.mouseSensitivity + steps * SENSITIVITY_STEP))

	fun changePlayFov(steps: Int) = set(settings.copy(playFov = settings.playFov + steps * FOV_STEP))

	fun changeViewAssist(steps: Int) = set(settings.copy(viewAssist = settings.viewAssist + steps))

	fun changeVisualOffset(steps: Int) = setOffsets(settings.visualOffsetMs + steps * OFFSET_STEP_MS, settings.judgementOffsetMs)

	fun setOffsets(visualOffsetMs: Int, judgementOffsetMs: Int) =
		set(settings.copy(visualOffsetMs = visualOffsetMs, judgementOffsetMs = judgementOffsetMs))

	/** Back to how a new player starts. */
	fun reset() = set(PlayerSettings())

	private fun set(changed: PlayerSettings) {
		val next = changed.inRange()
		if (next == settings) return
		settings = next
		save()
	}

	private fun save() {
		val file = file()
		val partial = file.resolveSibling("${file.fileName}.part")
		try {
			Files.createDirectories(file.parent)
			Files.writeString(partial, PlayerSettings.write(settings))
			Files.move(partial, file, StandardCopyOption.REPLACE_EXISTING)
		} catch (e: IOException) {
			LOGGER.warn("Can't save {}", file, e)
		}
	}

	private fun file(): Path = FabricLoader.getInstance().gameDir.resolve("hexium").resolve("settings.json")
}
