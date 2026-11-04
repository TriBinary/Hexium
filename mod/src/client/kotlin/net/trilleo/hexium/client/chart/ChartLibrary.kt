package net.trilleo.hexium.client.chart

import java.io.IOException
import java.io.UncheckedIOException
import java.nio.file.Files
import java.nio.file.Path
import net.fabricmc.loader.api.FabricLoader
import net.trilleo.hexium.Hexium
import net.trilleo.hexium.core.chart.ChartFolder
import net.trilleo.hexium.core.chart.ChartMeta
import net.trilleo.hexium.core.chart.DifficultyChart
import net.trilleo.hexium.core.chart.Severity
import org.slf4j.LoggerFactory

/** A chart folder. A pack with [errors] can't be played, and [meta] is null if its `meta.json` couldn't be read. */
class ChartPack(
	val folder: Path,
	val meta: ChartMeta?,
	val charts: List<DifficultyChart>,
	val errors: List<String>,
)

/**
 * Finds chart folders: the pack bundled in the mod, then each folder in `.minecraft/hexium/charts`.
 * This only reads the files. The rules come from [ChartFolder]. A bad folder comes back with its
 * errors and never stops the others.
 */
object ChartLibrary {
	private val LOGGER = LoggerFactory.getLogger(Hexium.MOD_ID)
	private const val BUNDLED_PACK = "hexium_packs/test-pattern"

	fun scan(): List<ChartPack> {
		val packs = ArrayList<ChartPack>()
		val bundled = FabricLoader.getInstance().getModContainer(Hexium.MOD_ID).flatMap { it.findPath(BUNDLED_PACK) }
		if (bundled.isPresent) packs += readPack(bundled.get()) else LOGGER.warn("The bundled chart pack is missing")
		for (folder in chartFolders()) packs += readPack(folder)
		for (pack in packs) {
			for (error in pack.errors) LOGGER.warn("Chart folder {}: {}", pack.folder, error)
		}
		return packs
	}

	private fun chartFolders(): List<Path> {
		val root = FabricLoader.getInstance().gameDir.resolve("hexium").resolve("charts")
		return try {
			Files.createDirectories(root)
			Files.list(root).use { files -> files.filter { Files.isDirectory(it) }.sorted().toList() }
		} catch (e: IOException) {
			LOGGER.warn("Can't read the charts folder {}", root, e)
			emptyList()
		} catch (e: UncheckedIOException) {
			LOGGER.warn("Can't read the charts folder {}", root, e)
			emptyList()
		}
	}

	private fun readPack(folder: Path): ChartPack {
		return try {
			readFiles(folder)
		} catch (e: IOException) {
			brokenPack(folder, e)
		} catch (e: RuntimeException) {
			// Includes the UncheckedIOException a directory listing throws when it fails part way through.
			brokenPack(folder, e)
		}
	}

	private fun brokenPack(folder: Path, cause: Exception) =
		ChartPack(folder, null, emptyList(), listOf("can't be read: ${cause.message}"))

	private fun readFiles(folder: Path): ChartPack {
		val paths = Files.list(folder).use { list -> list.filter { Files.isRegularFile(it) }.sorted().toList() }
		val errors = ArrayList<String>()
		val names = paths.map { it.fileName.toString() }
		val files = ArrayList<ChartFolder.File>()
		var sizeBytes = 0L
		for (path in paths) {
			val size = Files.size(path)
			sizeBytes += size
			val name = path.fileName.toString()
			if (ChartFolder.isChartFile(name)) files += ChartFolder.File(name, readText(path, name, size, errors))
		}
		val result = ChartFolder.read(files, names, sizeBytes)
		for (found in result.issues) {
			if (found.issue.severity != Severity.ERROR) continue
			errors += if (found.file == null) found.issue.toString() else "${found.file}: ${found.issue}"
		}
		return ChartPack(folder, result.meta, result.charts.sortedBy { it.difficulty }, errors)
	}

	private fun readText(path: Path, name: String, size: Long, errors: MutableList<String>): String? {
		if (ChartFolder.isTooLargeToRead(size)) {
			errors += "$name is too large"
			return null
		}
		return try {
			Files.readString(path)
		} catch (e: IOException) {
			errors += "$name can't be read: ${e.message}"
			null
		}
	}
}
