package net.trilleo.hexium.tools

import java.io.File
import java.io.IOException
import java.io.PrintStream
import net.trilleo.hexium.core.chart.ChartIssue
import net.trilleo.hexium.core.chart.ChartLimits
import net.trilleo.hexium.core.chart.ChartMeta
import net.trilleo.hexium.core.chart.ChartPackage
import net.trilleo.hexium.core.chart.ChartParser
import net.trilleo.hexium.core.chart.ChartValidator
import net.trilleo.hexium.core.chart.DifficultyChart
import net.trilleo.hexium.core.chart.IssueCode
import net.trilleo.hexium.core.chart.Severity

private const val META_FILE = "meta.json"

/** The package rules a folder adds on top of checking each file. */
private val PACKAGE_CODES = setOf(
	IssueCode.MISSING_DIFFICULTY_FILE,
	IssueCode.UNLISTED_DIFFICULTY_FILE,
	IssueCode.DUPLICATE_DIFFICULTY_FILE,
)

private class Tally(val out: PrintStream) {
	var files = 0
	var errors = 0
	var warnings = 0

	fun report(file: File, issues: List<ChartIssue>) {
		for (issue in issues) {
			if (issue.severity == Severity.ERROR) errors++ else warnings++
			out.println("${file.path}: $issue")
		}
	}

	fun error(file: File, message: String) {
		errors++
		out.println("${file.path}: ${Severity.ERROR} $message")
	}
}

internal fun lint(paths: List<String>, out: PrintStream): Int {
	val tally = Tally(out)
	var badPath = false
	for (path in paths) {
		val file = File(path)
		when {
			file.isDirectory -> {
				val charts = file.listFiles { f -> f.isFile && f.name.endsWith(".json") }.orEmpty().sortedBy { it.name }
				if (charts.isEmpty()) {
					out.println("$path: no .json files here")
					badPath = true
				} else {
					lintFolder(file, charts, tally)
				}
			}
			file.isFile -> lintFile(file, tally)
			else -> {
				out.println("$path: no such file or folder")
				badPath = true
			}
		}
	}
	if (tally.files > 0) out.println("${tally.files} file(s) checked: ${tally.errors} errors, ${tally.warnings} warnings")
	return when {
		badPath -> EXIT_USAGE
		tally.errors > 0 -> EXIT_PROBLEMS
		else -> EXIT_OK
	}
}

private fun lintFolder(folder: File, files: List<File>, tally: Tally) {
	var meta: ChartMeta? = null
	val charts = ArrayList<DifficultyChart>()
	var allRead = true
	for (file in files) {
		when (val value = lintFile(file, tally)) {
			is ChartMeta -> meta = value
			is DifficultyChart -> charts += value
			else -> allRead = false
		}
	}
	// A file that couldn't be read has been reported already, and isn't missing.
	val listed = meta ?: return
	if (!allRead) return
	val issues = ChartValidator.validatePackage(ChartPackage(listed, charts)).filter { it.code in PACKAGE_CODES }
	tally.report(folder, issues)
}

/** Returns what the file holds, if it could be read. */
private fun lintFile(file: File, tally: Tally): Any? {
	tally.files++
	val problemsBefore = tally.errors + tally.warnings
	val value = readAndCheck(file, tally)
	if (tally.errors + tally.warnings == problemsBefore) tally.out.println("${file.path}: ok")
	return value
}

private fun readAndCheck(file: File, tally: Tally): Any? {
	// A character is at most 4 bytes, so a longer file can't be within the limit. Don't try to read it.
	if (file.length() > ChartLimits.MAX_JSON_FILE_CHARS * 4L) {
		tally.error(file, "is ${file.length()} bytes, far over the limit of ${ChartLimits.MAX_JSON_FILE_CHARS} characters")
		return null
	}
	val text = try {
		file.readText()
	} catch (e: IOException) {
		tally.error(file, "can't be read: ${e.message}")
		return null
	}
	if (file.name == META_FILE) {
		val parsed = ChartParser.parseMeta(text)
		tally.report(file, parsed.issues)
		val meta = parsed.value ?: return null
		tally.report(file, ChartValidator.validateMeta(meta))
		return meta
	}
	val parsed = ChartParser.parseDifficulty(text)
	tally.report(file, parsed.issues)
	val chart = parsed.value ?: return null
	tally.report(file, ChartValidator.validateChart(chart))
	tally.report(file, ChartValidator.validateFileName(chart, file.name))
	return chart
}
