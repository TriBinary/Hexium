package net.trilleo.hexium.core.golden

import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import net.trilleo.hexium.core.chart.ChartParser
import net.trilleo.hexium.core.judge.ClearLamp
import net.trilleo.hexium.core.judge.Judge
import net.trilleo.hexium.core.judge.JudgeChart
import net.trilleo.hexium.core.judge.Judgement
import net.trilleo.hexium.core.replay.Replay
import net.trilleo.hexium.core.replay.ReplayFile
import net.trilleo.hexium.core.replay.ReplayJudge

/**
 * Stored replays of scripted players must keep giving the same results. Every summary below was
 * worked out by hand from the example charts, not copied from the judge (see the comments).
 * Each `.expected` file also locks every object's result, so any change to the judge shows up.
 *
 * After a deliberate change, regenerate them: ./gradlew :core:test --rerun -Dhexium.updateGolden=true
 */
class GoldenReplayTest {
	private class Golden(
		val name: String,
		val chart: String,
		val style: PlayStyle,
		val score: Int,
		val lamp: ClearLamp,
		val maxCombo: Int,
		val justiceCritical: Int,
		val justice: Int,
		val attack: Int,
		val miss: Int,
	)

	/*
	 * Objects in each chart, counting a hold as its head plus 7 ticks per 2 beats (ticks every 1/4 beat, no ends):
	 * - basic: 13 taps, 2 two-beat holds (8 each) = 29. 15 presses, 14 ticks.
	 * - advanced: 11 taps, 3 sky taps, 1 two-beat hold (8), 1 two-beat slide (7 ticks) = 29.
	 *   15 presses (taps, head, sky taps), 14 ticks.
	 * - expert: 18 taps, holds of 8, 6 and 8, so 40 ground objects. 3 sky taps, a two-beat slide (7), 3 flicks = 53.
	 *
	 * Score is 10,000 x (sum of weight percent) / objects, rounded down:
	 * - late_basic: 15 x 100 + 14 x 101 = 2914. 29,140,000 / 29 = 1,004,827.
	 * - attack_advanced: 15 x 50 + 14 x 101 = 2164. 21,640,000 / 29 = 746,206. 90 ms late is Attack only on easy charts.
	 * - ground_only_expert: 40 x 101 = 4040. 40,400,000 / 53 = 762,264.
	 * - sloppy_expert: presses 70 ms late are Attack on Expert (24: 18 taps, 3 heads, 3 sky taps). Flicks swiped 150 ms
	 *   late are Justice (3): beat 10's, with no required direction, is crossed a little earlier, on the jump from
	 *   rest to the start of its swipe, at about +111 ms, which is still Justice. The 26 ticks stay Justice Critical. 24 x 50 + 3 x 100 + 26 x 101 = 4126.
	 *   41,260,000 / 53 = 778,490.
	 * - ground_only_expert best combo: it's 10, between the hold head at 10.5 and the sky tap at 12 (missed at 12.24): 5 ticks,
	 *   the taps at 11, 11.25 and 11.5, and two taps at 12. By time alone it would be 11, but the flick at beat 10
	 *   is only known to be missed at the first aim sample after its deadline. That's 5.74 ms before the head at
	 *   10.5, and at 120 FPS the sample lands after the head, so the Miss splits the head off (docs/JUDGEMENT.md).
	 */
	private val goldens = listOf(
		Golden("perfect_basic", "basic", PlayStyle(), 1_010_000, ClearLamp.ALL_JUSTICE_CRITICAL, 29, 29, 0, 0, 0),
		Golden("late_basic", "basic", PlayStyle(lateMs = 50.0), 1_004_827, ClearLamp.ALL_JUSTICE, 29, 14, 15, 0, 0),
		Golden("perfect_advanced", "advanced", PlayStyle(), 1_010_000, ClearLamp.ALL_JUSTICE_CRITICAL, 29, 29, 0, 0, 0),
		Golden("attack_advanced", "advanced", PlayStyle(lateMs = 90.0), 746_206, ClearLamp.FULL_COMBO, 29, 14, 0, 15, 0),
		Golden("none_advanced", "advanced", PlayStyle(playGround = false, playSky = false), 0, ClearLamp.NONE, 0, 0, 0, 0, 29),
		Golden("perfect_expert", "expert", PlayStyle(judgementOffsetMs = 25.0), 1_010_000, ClearLamp.ALL_JUSTICE_CRITICAL, 53, 53, 0, 0, 0),
		Golden(
			"sloppy_expert",
			"expert",
			PlayStyle(lateMs = 70.0, swipeLateMs = 150.0),
			778_490,
			ClearLamp.FULL_COMBO,
			53,
			26,
			3,
			24,
			0,
		),
		Golden("ground_only_expert", "expert", PlayStyle(playSky = false), 762_264, ClearLamp.NONE, 10, 40, 0, 0, 13),
	)

	private val folder = File("src/test/resources/golden")
	private val recordedFolder = File(folder, "recorded")

	init {
		if (System.getProperty("hexium.updateGolden") == "true") regenerate()
	}

	private fun chartFile(name: String) = File("../docs/examples/$name.json").readBytes()

	private fun log(judge: Judge) = buildString {
		append("score ").append(judge.score).append('\n')
		append("lamp ").append(judge.clearLamp).append('\n')
		append("maxCombo ").append(judge.maxCombo).append('\n')
		for (i in 0 until judge.judgedCount) {
			append(judge.judgedNote(i)).append(' ').append(judge.judgement(i)).append(' ').append(judge.judgedDeltaMs(i)).append('\n')
		}
	}

	@Test
	fun storedReplaysGiveTheirHandWorkedResults() {
		for (golden in goldens) {
			val read = ReplayFile.read(File(folder, "${golden.name}.hexreplay").readBytes())
			assertNull(read.error, golden.name)
			val result = ReplayJudge.rejudge(chartFile(golden.chart), assertNotNull(read.replay))
			assertNull(result.error, golden.name)
			val judge = assertNotNull(result.judge)
			assertEquals(golden.score, judge.score, golden.name)
			assertEquals(golden.lamp, judge.clearLamp, golden.name)
			assertEquals(golden.maxCombo, judge.maxCombo, golden.name)
			assertEquals(golden.justiceCritical, judge.count(Judgement.JUSTICE_CRITICAL), golden.name)
			assertEquals(golden.justice, judge.count(Judgement.JUSTICE), golden.name)
			assertEquals(golden.attack, judge.count(Judgement.ATTACK), golden.name)
			assertEquals(golden.miss, judge.count(Judgement.MISS), golden.name)
			assertEquals(File(folder, "${golden.name}.expected").readText(), log(judge), golden.name)
		}
	}

	@Test
	fun scriptedPlayersStillProduceTheStoredResults() {
		for (golden in goldens) {
			val judge = ScriptedPlayer(chartFile(golden.chart)).play(golden.style).judge
			assertEquals(File(folder, "${golden.name}.expected").readText(), log(judge), golden.name)
		}
	}

	/*
	 * Real plays, recorded in the game, keeping the names they were saved under. Nobody can work out a person's
	 * results by hand, so these lock in what the judge gave: they catch a change, and regenerating, which only
	 * rewrites their `.expected` files, is the deliberate way to accept one. The inputs of a real play stay worth
	 * having under a newer judge, so unlike a real re-judge this ignores the judge version. It can't ignore the
	 * chart: after editing a chart, delete its recordings and record new ones.
	 */
	private fun recordedPlays(from: File): List<File> =
		from.listFiles { file -> file.name.endsWith(".hexreplay") }.orEmpty().sortedBy { it.name }

	private fun recordedChartFile(play: File): ByteArray {
		val match = assertNotNull(RECORDED_NAME.matchEntire(play.name), "${play.name} isn't named as the game saves replays")
		val (pack, difficulty) = match.destructured
		assertEquals(BUNDLED_PACK, pack, "${play.name} isn't of a pack the tests can read")
		return File("../mod/src/client/resources/hexium_packs/$BUNDLED_PACK/$difficulty.json").readBytes()
	}

	private fun rejudgeRecorded(play: File): Judge {
		val read = ReplayFile.read(play.readBytes())
		assertNull(read.error, play.name)
		val replay = assertNotNull(read.replay)
		val chartFile = recordedChartFile(play)
		assertContentEquals(Replay.hashOf(chartFile), replay.chartHash, "${play.name} was played on a different version of its chart")
		val chart = assertNotNull(ChartParser.parseDifficulty(String(chartFile, Charsets.UTF_8)).value)
		return ReplayJudge.play(JudgeChart.compile(chart), replay)
	}

	private fun expectedOf(play: File) = File(play.parentFile, "${play.name.removeSuffix(".hexreplay")}.expected")

	private fun assertRecordedPlaysMatch(from: File) {
		for (play in recordedPlays(from)) assertEquals(expectedOf(play).readText(), log(rejudgeRecorded(play)), play.name)
	}

	private fun writeRecordedExpected(from: File) {
		for (play in recordedPlays(from)) expectedOf(play).writeText(log(rejudgeRecorded(play)))
	}

	@Test
	fun recordedPlaysKeepTheirResults() {
		assertRecordedPlaysMatch(recordedFolder)
	}

	/** Checks the path above with a stored replay of the same chart, until and beyond there being real ones. */
	@Test
	fun aRecordedPlayIsReadAndLockedIn() {
		val temp = Files.createTempDirectory("hexium-recorded").toFile()
		try {
			val play = File(temp, "test-pattern-basic-20261104-160608-2.hexreplay")
			File(folder, "late_basic.hexreplay").copyTo(play)
			writeRecordedExpected(temp)
			assertEquals(File(folder, "late_basic.expected").readText(), expectedOf(play).readText())
			assertRecordedPlaysMatch(temp)
		} finally {
			temp.deleteRecursively()
		}
	}

	@Test
	fun aRecordedPlayMustBeNamedAsTheGameSavesIt() {
		assertFailsWith<AssertionError> { recordedChartFile(File("my-replay.hexreplay")) }
		assertFailsWith<AssertionError> { recordedChartFile(File("other-pack-basic-20261104-160608.hexreplay")) }
	}

	private companion object {
		const val BUNDLED_PACK = "test-pattern"
		val RECORDED_NAME = Regex("""(.+)-([a-z]+)-\d{8}-\d{6}(?:-\d+)?\.hexreplay""")
	}

	private fun regenerate() {
		folder.mkdirs()
		for (golden in goldens) {
			val recorder = ScriptedPlayer(chartFile(golden.chart)).play(golden.style)
			File(folder, "${golden.name}.hexreplay").writeBytes(ReplayFile.write(recorder.toReplay()))
			File(folder, "${golden.name}.expected").writeText(log(recorder.judge))
		}
		writeRecordedExpected(recordedFolder)
	}
}
