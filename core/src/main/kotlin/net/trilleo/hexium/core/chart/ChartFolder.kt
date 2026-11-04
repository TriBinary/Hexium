package net.trilleo.hexium.core.chart

/**
 * The rules for a folder of chart files, so everything that reads one treats it the same. The
 * caller does the file access and passes in what it found. Never throws.
 */
object ChartFolder {
	const val META_FILE: String = "meta.json"
	const val AUDIO_FILE: String = "audio.ogg"

	/** What a folder's own files add on top of checking each file. */
	private val FOLDER_CODES = setOf(
		IssueCode.MISSING_DIFFICULTY_FILE,
		IssueCode.UNLISTED_DIFFICULTY_FILE,
		IssueCode.DUPLICATE_DIFFICULTY_FILE,
		IssueCode.PACKAGE_TOO_LARGE,
	)

	/** [text] is null if it couldn't be read. The caller reports why. */
	class File(val name: String, val text: String?)

	/** One file's contents, if it could be read, and its problems. */
	class FileResult(val meta: ChartMeta?, val chart: DifficultyChart?, val issues: List<ChartIssue>)

	/** A problem in [file], or in the folder itself if [file] is null. */
	data class Issue(val file: String?, val issue: ChartIssue)

	class Result(val meta: ChartMeta?, val charts: List<DifficultyChart>, val issues: List<Issue>)

	/** Hidden files like the `._basic.json` that macOS leaves in archives aren't charts. */
	fun isChartFile(name: String): Boolean = name.endsWith(".json") && !name.startsWith(".")

	/** A character is at most 4 bytes, so a longer file can't be within the limit. Don't try to read it. */
	fun isTooLargeToRead(sizeBytes: Long): Boolean = sizeBytes > ChartLimits.MAX_JSON_FILE_CHARS * 4L

	fun readFile(name: String, text: String): FileResult {
		if (name == META_FILE) {
			val parsed = ChartParser.parseMeta(text)
			val meta = parsed.value ?: return FileResult(null, null, parsed.issues)
			return FileResult(meta, null, parsed.issues + ChartValidator.validateMeta(meta))
		}
		val parsed = ChartParser.parseDifficulty(text)
		val chart = parsed.value ?: return FileResult(null, null, parsed.issues)
		val issues = parsed.issues + ChartValidator.validateChart(chart) + ChartValidator.validateFileName(chart, name)
		return FileResult(null, chart, issues)
	}

	/** What's wrong with the folder as a whole. Only meaningful once every file in it could be read. */
	fun folderIssues(
		meta: ChartMeta,
		charts: List<DifficultyChart>,
		fileNames: List<String>,
		packageSizeBytes: Long? = null,
	): List<ChartIssue> {
		val issues = ArrayList<ChartIssue>()
		for (issue in ChartValidator.validatePackage(ChartPackage(meta, charts), packageSizeBytes = packageSizeBytes)) {
			if (issue.code in FOLDER_CODES) issues += issue
		}
		if (AUDIO_FILE !in fileNames) {
			issues += ChartIssue(Severity.ERROR, IssueCode.MISSING_AUDIO_FILE, "$", "has no $AUDIO_FILE")
		}
		return issues
	}

	/** Reads a whole folder: [files] are its chart files, and [fileNames] names everything in it. */
	fun read(files: List<File>, fileNames: List<String>, packageSizeBytes: Long? = null): Result {
		var meta: ChartMeta? = null
		val charts = ArrayList<DifficultyChart>()
		val issues = ArrayList<Issue>()
		var allRead = true
		for (file in files) {
			val text = file.text
			if (text == null) {
				allRead = false
				continue
			}
			val result = readFile(file.name, text)
			for (issue in result.issues) issues += Issue(file.name, issue)
			if (result.meta == null && result.chart == null) allRead = false
			if (result.meta != null) meta = result.meta
			if (result.chart != null) charts += result.chart
		}
		if (META_FILE !in fileNames) {
			issues += Issue(null, ChartIssue(Severity.ERROR, IssueCode.MISSING_META_FILE, "$", "has no $META_FILE"))
		}
		val listed = meta
		if (listed != null && allRead) {
			for (issue in folderIssues(listed, charts, fileNames, packageSizeBytes)) issues += Issue(null, issue)
		}
		return Result(listed, charts, issues)
	}
}
