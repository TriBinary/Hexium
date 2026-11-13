package net.trilleo.hexium.tools

import java.io.File
import java.io.PrintStream
import net.trilleo.hexium.core.judge.Judgement
import net.trilleo.hexium.core.replay.Replay
import net.trilleo.hexium.core.replay.ReplayEvent
import net.trilleo.hexium.core.replay.ReplayFile
import net.trilleo.hexium.core.replay.ReplayJudge

internal fun rejudge(chartPath: String, replayPath: String, out: PrintStream): Int {
	val chartFile = File(chartPath)
	val replayFile = File(replayPath)
	for (file in listOf(chartFile, replayFile)) {
		if (!file.isFile) {
			out.println("${file.path}: no such file")
			return EXIT_USAGE
		}
	}
	val read = ReplayFile.read(replayFile.readBytes())
	val replay = read.replay
	if (replay == null) {
		out.println("${replayFile.path}: ${read.error}")
		return EXIT_PROBLEMS
	}
	val result = ReplayJudge.rejudge(chartFile.readBytes(), replay)
	val judge = result.judge
	if (judge == null) {
		out.println("${replayFile.path}: ${result.error}")
		return EXIT_PROBLEMS
	}
	out.println("Score: ${judge.score}")
	out.println("Lamp: ${judge.clearLamp}")
	out.println("Max combo: ${judge.maxCombo} of ${judge.objectCount} objects")
	for (judgement in Judgement.entries) out.println("$judgement: ${judge.count(judgement)}")
	return EXIT_OK
}

internal fun dump(replayPath: String, out: PrintStream): Int {
	val file = File(replayPath)
	if (!file.isFile) {
		out.println("$replayPath: no such file")
		return EXIT_USAGE
	}
	val read = ReplayFile.read(file.readBytes())
	val replay = read.replay
	if (replay == null) {
		out.println("$replayPath: ${read.error}")
		return EXIT_PROBLEMS
	}
	out.println("Judge version: ${replay.judgeVersion}")
	out.println("Chart hash: ${replay.chartHash.joinToString("") { "%02x".format(it) }}")
	out.println("Judgement offset: ${replay.judgementOffsetMs} ms")
	out.println("${replay.eventCount} events:")
	for (i in 0 until replay.eventCount) out.println(eventLine(replay, i))
	return EXIT_OK
}

private fun eventLine(replay: Replay, i: Int): String {
	val event = replay.event(i)
	val position = if (event == ReplayEvent.AIM || event == ReplayEvent.BUTTON_DOWN) " ${replay.x(i)} ${replay.y(i)}" else ""
	return "${replay.timeMs(i)} $event ${replay.index(i)}$position"
}
