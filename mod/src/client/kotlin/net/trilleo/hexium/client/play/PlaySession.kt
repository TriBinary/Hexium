package net.trilleo.hexium.client.play

import java.io.IOException
import java.nio.file.Files
import net.minecraft.client.Minecraft
import net.minecraft.sounds.SoundSource
import net.trilleo.hexium.Hexium
import net.trilleo.hexium.client.audio.SongStream
import net.trilleo.hexium.client.chart.ChartPack
import net.trilleo.hexium.client.clock.AudioClock
import net.trilleo.hexium.client.input.LaneInput
import net.trilleo.hexium.core.chart.ChartFolder
import net.trilleo.hexium.core.chart.ChartParser
import net.trilleo.hexium.core.chart.ChartValidator
import net.trilleo.hexium.core.chart.Difficulty
import net.trilleo.hexium.core.chart.DifficultyChart
import net.trilleo.hexium.core.chart.Severity
import net.trilleo.hexium.core.judge.Judge
import net.trilleo.hexium.core.judge.JudgeChart
import net.trilleo.hexium.core.replay.Replay
import net.trilleo.hexium.core.replay.ReplayRecorder
import org.slf4j.LoggerFactory

/**
 * One play of a chart. Each frame it reads the audio clock, then sends everything that happened
 * through [recorder], so a replay holds exactly what the judge saw. All the rules are in `core`.
 */
class PlaySession private constructor(
	val chart: DifficultyChart,
	val chartFile: ByteArray,
	private val stream: SongStream,
) : AutoCloseable {
	enum class State { WAITING_FOR_STAGE, PLAYING, PAUSED, FINISHED }

	val judgeChart: JudgeChart = JudgeChart.compile(chart)
	val clock = AudioClock(stream)
	val recorder = ReplayRecorder(judgeChart, Replay.hashOf(chartFile), JUDGEMENT_OFFSET_MS, expectedEvents())
	private val endMs = chart.lastNoteEndMs() + TAIL_MS

	var state = State.WAITING_FOR_STAGE
		private set

	val judge: Judge
		get() = recorder.judge

	val acceptingInput: Boolean
		get() = state == State.PLAYING || state == State.PAUSED

	private fun expectedEvents(): Int {
		val seconds = (stream.lengthMs + LEAD_IN_MS + TAIL_MS) / 1000.0
		return (seconds * EXPECTED_SAMPLES_PER_SECOND).toInt() + judgeChart.objectCount * 2
	}

	/** Call once per frame, before anything reads the time. Doesn't allocate. */
	fun frame() {
		val minecraft = Minecraft.getInstance()
		stream.setGain(minecraft.options.getFinalSoundSourceVolume(SoundSource.MUSIC))
		val screenOpen = minecraft.gui.screen() != null
		when (state) {
			State.WAITING_FOR_STAGE -> {
				if (screenOpen || minecraft.player == null) return
				LaneInput.clear()
				clock.playAfter(LEAD_IN_MS)
				state = State.PLAYING
			}
			State.PLAYING -> if (screenOpen) {
				clock.pause()
				state = State.PAUSED
			}
			State.PAUSED -> if (!screenOpen) {
				clock.play()
				state = State.PLAYING
			}
			State.FINISHED -> return
		}
		clock.update()
		drainInput()
		if (state != State.PLAYING) return
		// Vanilla lowers the frame rate after a minute with no input it can see, and lane keys never reach it.
		minecraft.framerateLimitTracker.onInputReceived()
		val now = clock.songTimeMs
		recorder.aimSample(now, REST_AIM, REST_AIM)
		recorder.advanceTo(now)
		if (now >= endMs) finish()
	}

	private fun drainInput() {
		while (LaneInput.hasEvent) {
			val songMs = clock.songTimeAt(LaneInput.timeNs)
			if (LaneInput.down) recorder.pressLane(LaneInput.lane, songMs) else recorder.releaseLane(LaneInput.lane, songMs)
			LaneInput.next()
		}
	}

	private fun finish() {
		recorder.finish()
		clock.pause()
		state = State.FINISHED
		LOGGER.info("Finished with score {}, max combo {}, lamp {}", judge.score, judge.maxCombo, judge.clearLamp)
	}

	override fun close() {
		stream.close()
	}

	companion object {
		private val LOGGER = LoggerFactory.getLogger(Hexium.MOD_ID)
		private const val LEAD_IN_MS = 3000.0

		/** How long after the last note the play stays open. */
		private const val TAIL_MS = 2000.0

		/** Room for this frame rate without growing during play, on top of the notes' own presses. */
		private const val EXPECTED_SAMPLES_PER_SECOND = 2000

		/** Calibration sets this later. */
		private const val JUDGEMENT_OFFSET_MS = 0.0

		/** Aiming arrives with sky notes. Until then the crosshair stays mid-screen, on the same code path. */
		private const val REST_AIM = 0.5f

		/** Reads the chart's own bytes, since a replay is tied to them. Throws [IOException] saying what's wrong. */
		fun load(pack: ChartPack, difficulty: Difficulty): PlaySession {
			val bytes = Files.readAllBytes(pack.folder.resolve(ChartValidator.fileName(difficulty)))
			val parsed = ChartParser.parseDifficulty(String(bytes, Charsets.UTF_8))
			val chart = parsed.value ?: throw IOException(parsed.issues.first { it.severity == Severity.ERROR }.toString())
			val stream = SongStream.open(pack.folder.resolve(ChartFolder.AUDIO_FILE))
			try {
				val problem = ChartValidator.validateChart(chart, stream.lengthMs).firstOrNull { it.severity == Severity.ERROR }
				if (problem != null) throw IOException(problem.toString())
				return PlaySession(chart, bytes, stream)
			} catch (e: Exception) {
				stream.close()
				throw e
			}
		}
	}
}
