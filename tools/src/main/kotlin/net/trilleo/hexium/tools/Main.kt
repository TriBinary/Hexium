package net.trilleo.hexium.tools

import java.io.IOException
import java.io.PrintStream
import kotlin.system.exitProcess

internal const val EXIT_OK = 0
internal const val EXIT_PROBLEMS = 1
internal const val EXIT_USAGE = 2

private const val USAGE = """Hexium tools
  lint <file or folder>...        Check chart files against the chart format
  rejudge <chart> <replay>        Run a replay through the judge and print the result
  dump <replay>                   Print a replay as text"""

fun main(args: Array<String>) {
	exitProcess(runTool(args.toList(), System.out))
}

/** Returns the exit code: 0 for fine, 1 for problems in the files, 2 for a wrong command or missing file. */
internal fun runTool(args: List<String>, out: PrintStream): Int {
	val rest = args.drop(1)
	return try {
		when (args.firstOrNull()) {
			"lint" -> if (rest.isEmpty()) usage(out) else lint(rest, out)
			"rejudge" -> if (rest.size != 2) usage(out) else rejudge(rest[0], rest[1], out)
			"dump" -> if (rest.size != 1) usage(out) else dump(rest[0], out)
			else -> usage(out)
		}
	} catch (e: IOException) {
		out.println("Can't read a file: ${e.message}")
		EXIT_PROBLEMS
	}
}

private fun usage(out: PrintStream): Int {
	out.println(USAGE)
	return EXIT_USAGE
}
