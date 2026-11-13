package net.trilleo.hexium.client.play

import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import net.fabricmc.loader.api.FabricLoader
import net.trilleo.hexium.Hexium
import net.trilleo.hexium.core.chart.Difficulty
import net.trilleo.hexium.core.replay.Replay
import net.trilleo.hexium.core.replay.ReplayFile
import org.slf4j.LoggerFactory

/** Finished plays, in `.minecraft/hexium/replays`. Nothing here is part of a play's timing. */
object ReplayFolder {
	private val LOGGER = LoggerFactory.getLogger(Hexium.MOD_ID)
	private val STAMP = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")

	/** Returns where it went, or null if it couldn't be saved, which is never worth losing a result over. */
	fun save(chartId: String, difficulty: Difficulty, replay: Replay): Path? {
		var partial: Path? = null
		try {
			val folder = FabricLoader.getInstance().gameDir.resolve("hexium").resolve("replays")
			Files.createDirectories(folder)
			val target = freeName(folder, "$chartId-${difficulty.name.lowercase()}-${LocalDateTime.now().format(STAMP)}")
			// Moved into place, so a failed write doesn't leave half a replay under a real name.
			val part = target.resolveSibling("${target.fileName}.part")
			partial = part
			Files.write(part, ReplayFile.write(replay))
			Files.move(part, target, StandardCopyOption.REPLACE_EXISTING)
			return target
		} catch (e: IOException) {
			failed(partial, e)
		} catch (e: RuntimeException) {
			failed(partial, e)
		}
		return null
	}

	private fun failed(partial: Path?, cause: Exception) {
		LOGGER.warn("Can't save the replay", cause)
		try {
			if (partial != null) Files.deleteIfExists(partial)
		} catch (e: IOException) {
			LOGGER.warn("Can't delete {}", partial, e)
		}
	}

	private fun freeName(folder: Path, name: String): Path {
		var target = folder.resolve("$name.hexreplay")
		var again = 2
		while (Files.exists(target)) target = folder.resolve("$name-${again++}.hexreplay")
		return target
	}
}
