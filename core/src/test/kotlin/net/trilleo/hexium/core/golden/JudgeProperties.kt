package net.trilleo.hexium.core.golden

import java.io.File
import kotlin.math.abs
import net.jqwik.api.Arbitraries
import net.jqwik.api.Arbitrary
import net.jqwik.api.ForAll
import net.jqwik.api.Property
import net.jqwik.api.Provide
import net.jqwik.api.RandomDistribution
import net.jqwik.api.constraints.DoubleRange
import net.jqwik.api.constraints.IntRange
import net.trilleo.hexium.core.chart.SkyButton
import net.trilleo.hexium.core.chart.ChartParser
import net.trilleo.hexium.core.judge.Judge
import net.trilleo.hexium.core.chart.Note
import net.trilleo.hexium.core.judge.JudgeChart
import net.trilleo.hexium.core.judge.Judgement
import net.trilleo.hexium.core.judge.beat
import net.trilleo.hexium.core.judge.chartOf
import net.trilleo.hexium.core.replay.Replay
import net.trilleo.hexium.core.replay.ReplayFile
import net.trilleo.hexium.core.replay.ReplayJudge
import net.trilleo.hexium.core.replay.ReplayRecorder

/** Whatever the input, these stay true (docs/DEVELOPMENT.md, test strategy). */
class JudgeProperties {
	private val names = listOf("basic", "advanced", "expert")

	private fun chartFile(which: Int) = File("../docs/examples/${names[which]}.json").readBytes()

	/** A play with a different small timing error on each press, the same for the same seed. */
	private fun jitter(seed: Int, spreadMs: Double): (Int) -> Double = { note ->
		val mixed = (note + 1) * 2654435761L xor seed.toLong()
		val whole = java.util.Random(mixed).nextDouble() * 2 - 1
		Math.round(whole * spreadMs * 1024.0) / 1024.0
	}

	private fun results(judge: Judge) = List(judge.judgedCount) { judge.judgedNote(it) to judge.judgement(it) }

	@Property(tries = 60)
	fun shiftingEveryInputByOffsetBLeavesTheResultUnchanged(
		@ForAll @IntRange(min = 0, max = 2) chart: Int,
		@ForAll seed: Int,
		@ForAll @IntRange(min = -3200, max = 3200) sixteenths: Int,
	): Boolean {
		val player = ScriptedPlayer(chartFile(chart))
		val spread = jitter(seed, 120.0)
		val plain = player.play(PlayStyle(jitterMs = spread)).judge
		val shifted = player.play(PlayStyle(jitterMs = spread, judgementOffsetMs = sixteenths / 16.0)).judge
		return plain.score == shifted.score && results(plain) == results(shifted)
	}

	@Provide
	fun swipeTimes(): Arbitrary<Double> = Arbitraries.doubles().between(-220.0, 230.0).withDistribution(RandomDistribution.uniform())

	@Provide
	fun angles(): Arbitrary<Double> = Arbitraries.doubles().between(0.0, 360.0).withDistribution(RandomDistribution.uniform())

	@Property(tries = 300)
	fun aSteadySwipeGetsTheSameJudgementAtEveryFrameRate(
		@ForAll("swipeTimes") lateMs: Double,
		@ForAll("angles") degrees: Double,
		@ForAll @DoubleRange(min = 0.0, max = 1.0) framePhase: Double,
	): Boolean {
		// 6 heights per second through a flick at 1000 ms, so it's inside the hit area for 13.33 ms either side of lateMs.
		val speed = 0.006
		val insideMs = 0.08 / speed
		val opens = -166.67
		val closes = 166.67
		val leavesAtMs = lateMs + insideMs
		val crossesAtMs = maxOf(lateMs - insideMs, opens)
		// Floats round positions, so stay clear of the very edge of each limit.
		val clearOfLimits = abs(abs(crossesAtMs) - 83.33) > 0.01 && abs(leavesAtMs - opens) > 0.01 && abs(crossesAtMs - closes) > 0.01
		if (!clearOfLimits) return true
		val dx = Math.cos(Math.toRadians(degrees)) * speed / 2.0
		val dy = Math.sin(Math.toRadians(degrees)) * speed
		val chart = JudgeChart.compile(chartOf(Note.Flick(beat(1), 0.5, 0.5, 1.0)))
		val judgements = listOf(30.0, 60.0, 120.0, 144.0, 240.0, 360.0).map { fps ->
			val judge = Judge(chart)
			val frame = 1000.0 / fps
			var time = 600.0 + framePhase * frame
			while (time < 1400.0) {
				val moved = time - 1000.0 - lateMs
				judge.aimSample(time, (0.5 + dx * moved).toFloat(), (0.5 + dy * moved).toFloat())
				time += frame
			}
			judge.finish()
			judge.judgement(0)
		}
		val expected = when {
			leavesAtMs < opens || crossesAtMs > closes -> Judgement.MISS
			abs(crossesAtMs) <= 83.33 -> Judgement.JUSTICE_CRITICAL
			else -> Judgement.JUSTICE
		}
		return judgements.all { it == expected }
	}

	@Provide
	fun actions(): Arbitrary<List<Int>> = Arbitraries.integers().between(0, 5).list().ofMinSize(50).ofMaxSize(400)

	@Property(tries = 200)
	fun randomInputNeverBreaksTheJudgeAndReplaysMatch(
		@ForAll @IntRange(min = 0, max = 2) chart: Int,
		@ForAll("actions") actions: List<Int>,
		@ForAll seed: Long,
	): Boolean {
		val file = chartFile(chart)
		val compiled = JudgeChart.compile(ChartParser.parseDifficulty(String(file)).value!!)
		val recorder = ReplayRecorder(compiled, Replay.hashOf(file), 0.0, actions.size)
		val random = java.util.Random(seed)
		val chartNotes = ChartParser.parseDifficulty(String(file)).value!!.notes
		val spots = chartNotes.mapNotNull { note ->
			when (note) {
				is Note.SkyTap -> note.x to note.y
				is Note.Flick -> note.x to note.y
				is Note.Slide -> note.points[0].x to note.points[0].y
				else -> null
			}
		}
		var time = 0.0
		for (action in actions) {
			time += random.nextDouble() * 90.0 - 10.0
			// Half the time, somewhere a note really is, so that plays hit things as well as miss them.
			val spot = if (spots.isNotEmpty() && random.nextBoolean()) spots[random.nextInt(spots.size)] else null
			val x = spot?.first?.toFloat() ?: (random.nextFloat() * 1.4f - 0.2f)
			val y = spot?.second?.toFloat() ?: (random.nextFloat() * 1.4f - 0.2f)
			val lane = random.nextInt(5)
			val button = if (random.nextBoolean()) SkyButton.L else SkyButton.R
			when (action) {
				0 -> recorder.pressLane(lane, time)
				1 -> recorder.releaseLane(lane, time)
				2 -> recorder.pressButton(button, time, x, y)
				3 -> recorder.releaseButton(button, time)
				4 -> recorder.aimSample(time, x, y)
				else -> recorder.advanceTo(time)
			}
		}
		recorder.finish()
		val judge = recorder.judge
		val written = ReplayFile.write(recorder.toReplay())
		val replayed = ReplayJudge.play(compiled, ReplayFile.read(written).replay!!)
		return judge.judgedCount == judge.objectCount &&
			judge.score in 0..1_010_000 &&
			judge.maxCombo <= judge.objectCount &&
			results(judge) == results(replayed) &&
			List(judge.judgedCount) { judge.judgedDeltaMs(it) } == List(replayed.judgedCount) { replayed.judgedDeltaMs(it) } &&
			judge.score == replayed.score &&
			judge.maxCombo == replayed.maxCombo
	}
}
