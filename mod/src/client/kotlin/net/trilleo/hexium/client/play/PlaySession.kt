package net.trilleo.hexium.client.play

import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import net.minecraft.client.Minecraft
import net.minecraft.sounds.SoundSource
import net.trilleo.hexium.Hexium
import net.trilleo.hexium.client.audio.SongStream
import net.trilleo.hexium.client.chart.ChartPack
import net.trilleo.hexium.client.clock.AudioClock
import net.trilleo.hexium.client.debug.DebugOverlay
import net.trilleo.hexium.client.debug.InputDelay
import net.trilleo.hexium.client.input.Aim
import net.trilleo.hexium.client.input.LaneInput
import net.trilleo.hexium.client.input.SkyInput
import net.trilleo.hexium.client.screen.ResultsScreen
import net.trilleo.hexium.core.chart.ChartFolder
import net.trilleo.hexium.core.chart.ChartParser
import net.trilleo.hexium.core.chart.ChartValidator
import net.trilleo.hexium.core.chart.Difficulty
import net.trilleo.hexium.core.chart.DifficultyChart
import net.trilleo.hexium.core.chart.GroundTrack
import net.trilleo.hexium.core.chart.Severity
import net.trilleo.hexium.core.chart.SkyButton
import net.trilleo.hexium.core.chart.SkyTrack
import net.trilleo.hexium.core.judge.Judge
import net.trilleo.hexium.core.judge.JudgeChart
import net.trilleo.hexium.core.replay.PlayInput
import net.trilleo.hexium.core.replay.Replay
import net.trilleo.hexium.core.replay.ReplayRecorder
import org.lwjgl.sdl.SDLTimer
import org.slf4j.LoggerFactory

/**
 * One play of a chart. Each frame it reads the audio clock at the start ([frame]), then, once vanilla
 * has turned the camera with that frame's mouse movement ([afterMouse]), sends everything that
 * happened through [input] and on to [recorder], so a replay holds exactly what the judge saw and each
 * aim sample is where the camera really pointed. All the rules, including what a pause does, are in `core`.
 */
class PlaySession private constructor(
	private val pack: ChartPack,
	val chart: DifficultyChart,
	val chartFile: ByteArray,
	private val stream: SongStream,
) : AutoCloseable {
	/** [REWINDING] is the countdown after a pause: the song runs again, but input isn't judged yet. */
	enum class State { WAITING_FOR_STAGE, PLAYING, PAUSED, REWINDING, FINISHED }

	val judgeChart: JudgeChart = JudgeChart.compile(chart)
	val clock = AudioClock(stream)
	val inputDelay = InputDelay()
	val debugOverlay = DebugOverlay(clock, stream, inputDelay)
	val judgementOffsetMs = PlaySettings.judgementOffsetMs.toDouble()
	val recorder = ReplayRecorder(judgeChart, Replay.hashOf(chartFile), judgementOffsetMs, expectedEvents())
	val track = GroundTrack(chart)
	val sky = SkyTrack(chart, judgeChart.config)
	private val endMs = chart.lastNoteEndMs() + TAIL_MS
	private val headJudged = BooleanArray(chart.notes.size)
	private var judgedSeen = 0
	private val input = PlayInput(recorder, chart.laneCount)

	/** The song time [frame] read, waiting for [afterMouse] to send it with the aim. */
	private var frameMs = 0.0
	private var framePending = false

	/** The song time view assist last eased the camera at. */
	private var assistedAtMs = Double.NaN

	/** SDL's tick counter at the start of the frame, so a key's stamp age doesn't count vanilla's work since. */
	private var frameStartNs = 0L

	/** Where the replay of this play went, once it has finished, or null if it couldn't be saved. */
	var savedReplay: Path? = null
		private set

	val chartTitle: String
		get() = pack.meta?.title.orEmpty()

	/** Everything up to here has been played, so the rewind after a pause doesn't draw it again. */
	val playedUntilMs: Double
		get() = input.playedUntilMs

	var state = State.WAITING_FOR_STAGE
		private set

	val judge: Judge
		get() = recorder.judge

	val acceptingInput: Boolean
		get() = state == State.PLAYING || state == State.PAUSED || state == State.REWINDING

	/** False until the clock has its first time, when there's nothing right to draw. */
	val notesMoving: Boolean
		get() = state != State.WAITING_FOR_STAGE

	/** True once a tap, hold head, sky tap or flick, by its index in the chart's notes, has been judged. Ticks don't count. */
	fun isHeadJudged(chartNote: Int): Boolean = headJudged[chartNote]

	fun isLaneDown(lane: Int): Boolean = acceptingInput && input.isLaneDown(lane)

	/** For drawing a slide as held. */
	fun isButtonDown(button: SkyButton): Boolean = acceptingInput && input.isButtonDown(button)

	private fun expectedEvents(): Int {
		val seconds = (stream.lengthMs + LEAD_IN_MS + TAIL_MS) / 1000.0
		return (seconds * EXPECTED_SAMPLES_PER_SECOND).toInt() + judgeChart.objectCount * 2
	}

	/** Call once per frame, before anything reads the time. Doesn't allocate. */
	fun frame() {
		val minecraft = Minecraft.getInstance()
		frameStartNs = SDLTimer.SDL_GetTicksNS()
		stream.setGain(minecraft.options.getFinalSoundSourceVolume(SoundSource.MUSIC))
		val screenOpen = minecraft.gui.screen() != null
		when (state) {
			State.WAITING_FOR_STAGE -> {
				if (screenOpen || minecraft.player == null) return
				LaneInput.clear()
				SkyInput.clear()
				Aim.reset()
				clock.playAfter(LEAD_IN_MS)
				state = State.PLAYING
			}
			State.PLAYING, State.REWINDING -> if (screenOpen) {
				// These came before the pause, so they count at their own times.
				drainInput()
				clock.pause()
				input.pause(clock.songTimeMs)
				assistedAtMs = Double.NaN
				state = State.PAUSED
			}
			State.PAUSED -> if (!screenOpen) {
				clock.playFrom(input.resume())
				state = State.REWINDING
			}
			State.FINISHED -> return
		}
		clock.update()
		if (state == State.PAUSED) {
			// Keys and buttons changed during the pause, read on this clock before resuming moves it.
			drainInput()
			readResults()
			return
		}
		// Vanilla lowers the frame rate after a minute with no input it can see, and lane keys never reach it.
		minecraft.framerateLimitTracker.onInputReceived()
		frameMs = clock.songTimeMs
		framePending = true
	}

	/**
	 * Call once per frame after vanilla has turned the camera with the mouse. Keeps the camera within its
	 * limit, then sends that frame's input and aim sample. Doesn't allocate.
	 */
	fun afterMouse() {
		if (state == State.WAITING_FOR_STAGE || state == State.FINISHED) return
		if (framePending && state != State.PAUSED) {
			// Song time, so it stops with the song. After a pause or the rewind it's NaN or goes back, and does nothing.
			val elapsed = frameMs - assistedAtMs
			if (elapsed.isFinite()) Comfort.assist(this, frameMs, elapsed)
			assistedAtMs = frameMs
		}
		Aim.update()
		drainInput()
		if (!framePending) return
		framePending = false
		input.frame(frameMs, Aim.x, Aim.y)
		if (state == State.REWINDING && input.isOpen) state = State.PLAYING
		readResults()
		if (state == State.PLAYING && frameMs >= endMs) finish(Minecraft.getInstance())
	}

	private fun readResults() {
		while (judgedSeen < judge.judgedCount) {
			if (judge.judgedIsPress(judgedSeen)) headJudged[judge.judgedNote(judgedSeen)] = true
			judgedSeen++
		}
	}

	/** Keys and mouse buttons, oldest first. A button is pressed where the crosshair is now. */
	private fun drainInput() {
		while (LaneInput.hasEvent || SkyInput.hasEvent) {
			val laneFirst = LaneInput.hasEvent && (!SkyInput.hasEvent || LaneInput.timeNs <= SkyInput.timeNs)
			val timeNs = if (laneFirst) LaneInput.timeNs else SkyInput.timeNs
			if (laneFirst) {
				input.lane(LaneInput.lane, LaneInput.down, clock.songTimeAt(timeNs))
				LaneInput.next()
			} else {
				input.button(SkyInput.button, SkyInput.down, clock.songTimeAt(timeNs), Aim.x, Aim.y)
				SkyInput.next()
			}
			inputDelay.lateKeys = input.lateKeyCount
			// Only what reached the judge, so a key changed during a pause isn't counted.
			if (input.isOpen) inputDelay.record(frameStartNs - timeNs)
		}
	}

	private fun finish(minecraft: Minecraft) {
		input.finish()
		readResults()
		clock.pause()
		state = State.FINISHED
		LOGGER.info("Finished with score {}, max combo {}, lamp {}", judge.score, judge.maxCombo, judge.clearLamp)
		LOGGER.info("Final drift {} ms, max {} ms, resyncs {}, underruns {}, late keys {}", clock.clock.driftMs, clock.clock.maxDriftMs, clock.clock.resyncCount, stream.underruns, input.lateKeyCount)
		// A cut-short replay would re-judge to a different result.
		if (!recorder.isFull) savedReplay = ReplayFolder.save(pack.meta?.id ?: UNKNOWN_CHART, chart.difficulty, recorder.toReplay())
		minecraft.gui.setScreen(ResultsScreen(this))
	}

	/** The same chart again from the start. Throws as [load] does. */
	fun loadAgain(): PlaySession = load(pack, chart.difficulty)

	override fun close() {
		stream.close()
	}

	companion object {
		private val LOGGER = LoggerFactory.getLogger(Hexium.MOD_ID)
		private const val LEAD_IN_MS = 3000.0
		private const val UNKNOWN_CHART = "chart"

		/** How long after the last note the play stays open. */
		private const val TAIL_MS = 2000.0

		/** Room for this frame rate without growing during play, on top of the notes' own presses. */
		private const val EXPECTED_SAMPLES_PER_SECOND = 2000

		/** Reads the chart's own bytes, since a replay is tied to them. Throws [IOException] saying what's wrong. */
		fun load(pack: ChartPack, difficulty: Difficulty): PlaySession {
			val bytes = Files.readAllBytes(pack.folder.resolve(ChartValidator.fileName(difficulty)))
			val parsed = ChartParser.parseDifficulty(String(bytes, Charsets.UTF_8))
			val chart = parsed.value ?: throw IOException(parsed.issues.first { it.severity == Severity.ERROR }.toString())
			val stream = SongStream.open(pack.folder.resolve(ChartFolder.AUDIO_FILE))
			try {
				val problem = ChartValidator.validateChart(chart, stream.lengthMs).firstOrNull { it.severity == Severity.ERROR }
				if (problem != null) throw IOException(problem.toString())
				return PlaySession(pack, chart, bytes, stream)
			} catch (e: Exception) {
				stream.close()
				throw e
			}
		}
	}
}
