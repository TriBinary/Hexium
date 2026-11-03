package net.trilleo.hexium.tools

import java.io.ByteArrayOutputStream
import java.io.File
import java.io.PrintStream
import java.io.RandomAccessFile
import kotlin.io.path.createTempDirectory
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import net.trilleo.hexium.core.chart.SkyButton
import net.trilleo.hexium.core.judge.JudgeChart
import net.trilleo.hexium.core.chart.ChartParser
import net.trilleo.hexium.core.replay.Replay
import net.trilleo.hexium.core.replay.ReplayFile
import net.trilleo.hexium.core.replay.ReplayRecorder

class ToolsTest {
	private val dir = createTempDirectory("hexium-tools").toFile()
	private val examples = File("../docs/examples")

	@AfterTest
	fun cleanUp() {
		dir.deleteRecursively()
	}

	private fun run(vararg args: String): Pair<Int, String> {
		val bytes = ByteArrayOutputStream()
		val code = runTool(args.toList(), PrintStream(bytes))
		return code to bytes.toString()
	}

	private val goodChart = """
		{
		  "formatVersion": 1, "difficulty": "BASIC", "level": 1, "laneCount": 4, "offsetMs": 0,
		  "bpm": [ { "t": [0,0,1], "bpm": 60 } ],
		  "meter": [ { "t": [0,0,1], "num": 4, "den": 4 } ],
		  "notes": [ { "type": "tap", "t": [1,0,1], "lane": 0 } ]
		}
	""".trimIndent()

	private val goodMeta = """
		{
		  "formatVersion": 1, "id": "song", "title": "T", "artist": "A", "charter": "C",
		  "previewStartMs": 0, "license": { "type": "original" }, "difficulties": [ "BASIC" ]
		}
	""".trimIndent()

	private fun write(name: String, text: String) = File(dir, name).also { it.writeText(text) }

	@Test
	fun noArgumentsPrintsUsage() {
		val (code, output) = run()
		assertEquals(2, code)
		assertTrue("lint" in output && "rejudge" in output && "dump" in output)
	}

	@Test
	fun unknownCommandPrintsUsage() {
		assertEquals(2, run("convert", "x").first)
	}

	@Test
	fun lintPassesTheExampleCharts() {
		val (code, output) = run("lint", examples.path)
		assertEquals(0, code, output)
		assertTrue("basic.json: ok" in output && "advanced.json: ok" in output && "expert.json: ok" in output, output)
		assertTrue("0 errors, 0 warnings" in output, output)
	}

	@Test
	fun lintPassesASingleFile() {
		val (code, output) = run("lint", write("basic.json", goodChart).path)
		assertEquals(0, code, output)
		assertTrue("1 file" in output, output)
	}

	@Test
	fun lintReportsParseErrorsWithTheirPath() {
		val (code, output) = run("lint", write("basic.json", goodChart.replace(""""lane": 0""", """"lane": "x"""")).path)
		assertEquals(1, code)
		assertTrue("notes[0].lane" in output, output)
		assertTrue("1 errors" in output, output)
	}

	@Test
	fun lintReportsValidatorErrors() {
		val (code, output) = run("lint", write("basic.json", goodChart.replace(""""lane": 0""", """"lane": 9""")).path)
		assertEquals(1, code)
		assertTrue("notes[0].lane" in output, output)
	}

	@Test
	fun lintWarningsDoNotFail() {
		val (code, output) = run("lint", write("basic.json", goodChart.replace(""""level": 1,""", """"level": 1, "mood": "calm",""")).path)
		assertEquals(0, code, output)
		assertTrue("mood" in output && "1 warnings" in output, output)
	}

	@Test
	fun lintRejectsAFileNamedForAnotherDifficulty() {
		val (code, output) = run("lint", write("expert.json", goodChart).path)
		assertEquals(1, code)
		assertTrue("basic.json" in output, output)
	}

	@Test
	fun lintChecksMetaFiles() {
		assertEquals(0, run("lint", write("meta.json", goodMeta).path).first)
		val (code, output) = run("lint", write("meta.json", goodMeta.replace(""""song"""", """"Bad Id"""")).path)
		assertEquals(1, code)
		assertTrue("id" in output, output)
	}

	@Test
	fun lintChecksAFolderWithMetaAsAPackage() {
		write("meta.json", goodMeta)
		write("basic.json", goodChart)
		assertEquals(0, run("lint", dir.path).first)

		write("meta.json", goodMeta.replace(""""BASIC"""", """"BASIC", "EXPERT""""))
		val (code, output) = run("lint", dir.path)
		assertEquals(1, code)
		assertTrue("expert.json" in output, output)
	}

	@Test
	fun lintDoesNotCallABrokenFileMissing() {
		write("meta.json", goodMeta)
		write("basic.json", "{ not json")
		val (code, output) = run("lint", dir.path)
		assertEquals(1, code)
		assertFalse("has no basic.json" in output, output)
	}

	@Test
	fun lintKeepsGoingPastABadPath() {
		val good = write("basic.json", goodChart)
		val (code, output) = run("lint", File(dir, "nothing").path, good.path)
		assertEquals(2, code)
		assertTrue("basic.json: ok" in output && "1 file(s) checked" in output, output)
	}

	@Test
	fun lintDoesNotTryToReadAHugeFile() {
		val huge = File(dir, "basic.json")
		RandomAccessFile(huge, "rw").use { it.setLength(3L * 1024 * 1024 * 1024) }
		val good = write("expert.json", goodChart.replace("BASIC", "EXPERT"))
		val (code, output) = run("lint", huge.path, good.path)
		assertEquals(1, code)
		assertTrue("over the limit" in output && "expert.json: ok" in output, output)
	}

	@Test
	fun lintFailsOnAMissingPathOrEmptyFolder() {
		assertEquals(2, run("lint", File(dir, "nothing").path).first)
		assertEquals(2, run("lint", dir.path).first)
		assertEquals(2, run("lint").first)
	}

	private fun replayOf(chartText: String): File {
		val bytes = chartText.toByteArray()
		val chart = JudgeChart.compile(ChartParser.parseDifficulty(chartText).value!!)
		val recorder = ReplayRecorder(chart, Replay.hashOf(bytes), 0.0, 8)
		recorder.aimSample(990.0, 0.5f, 0.5f)
		recorder.pressLane(0, 1050.0)
		recorder.pressButton(SkyButton.L, 1060.0, 0.25f, 0.75f)
		recorder.finish()
		return File(dir, "play.hexreplay").also { it.writeBytes(ReplayFile.write(recorder.toReplay())) }
	}

	@Test
	fun rejudgePrintsTheResult() {
		val replay = replayOf(goodChart)
		val (code, output) = run("rejudge", write("basic.json", goodChart).path, replay.path)
		assertEquals(0, code, output)
		assertTrue("1000000" in output, output)
		assertTrue("ALL_JUSTICE" in output, output)
	}

	@Test
	fun rejudgeRefusesAReplayOfAnotherChart() {
		val replay = replayOf(goodChart)
		val (code, output) = run("rejudge", write("basic.json", goodChart.replace(""""level": 1""", """"level": 2""")).path, replay.path)
		assertEquals(1, code)
		assertTrue("different chart" in output, output)
	}

	@Test
	fun rejudgeRefusesABrokenReplayFile() {
		val (code, output) = run("rejudge", write("basic.json", goodChart).path, write("x.hexreplay", "nope").path)
		assertEquals(1, code)
		assertTrue("compressed" in output, output)
	}

	@Test
	fun rejudgeNeedsBothFiles() {
		assertEquals(2, run("rejudge", "only-one").first)
		assertEquals(2, run("rejudge", File(dir, "a").path, File(dir, "b").path).first)
	}

	@Test
	fun dumpPrintsAReplayAsText() {
		val (code, output) = run("dump", replayOf(goodChart).path)
		assertEquals(0, code, output)
		assertTrue("3 events" in output, output)
		assertTrue("AIM" in output && "LANE_DOWN" in output && "BUTTON_DOWN" in output, output)
		assertTrue("0.25" in output, output)
		assertFalse("Exception" in output)
	}

	@Test
	fun dumpRefusesABrokenFile() {
		assertEquals(1, run("dump", write("x.hexreplay", "nope").path).first)
		assertEquals(2, run("dump").first)
	}
}
