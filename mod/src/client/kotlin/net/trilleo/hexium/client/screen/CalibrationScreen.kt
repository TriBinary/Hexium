package net.trilleo.hexium.client.screen

import java.io.IOException
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.CommonComponents
import net.minecraft.network.chat.Component
import net.minecraft.sounds.SoundSource
import net.trilleo.hexium.Hexium
import net.trilleo.hexium.client.audio.SongStream
import net.trilleo.hexium.client.chart.ChartPack
import net.trilleo.hexium.client.clock.AudioClock
import net.trilleo.hexium.client.input.LaneInput
import net.trilleo.hexium.client.play.PlaySettings
import net.trilleo.hexium.client.session.HexiumSession
import net.trilleo.hexium.core.calibration.Calibration
import net.trilleo.hexium.core.calibration.TapEstimate
import net.trilleo.hexium.core.chart.ChartFolder
import net.trilleo.hexium.core.settings.PlayerSettings
import org.slf4j.LoggerFactory

/**
 * Suggests offsets A and B in two steps. The player taps to clicks with their eyes closed, then to moving
 * notes with no sound. It runs on a plain screen with the bundled click track, on the same audio clock
 * and key timestamps as a real play. The maths is in [Calibration]. Holds a session from the menu that
 * opened it until it closes.
 */
class CalibrationScreen(private val lastScreen: Screen, private val pack: ChartPack) : Screen(TITLE) {
	enum class Step { LISTEN_INTRO, LISTENING, WATCH_INTRO, WATCHING, REVIEW }

	var step = Step.LISTEN_INTRO
		private set

	/** The clock taps are read against, for the game test. */
	var clock: AudioClock? = null
		private set

	var suggestedVisualOffsetMs = 0
		private set
	var suggestedJudgementOffsetMs = 0
		private set

	private var stream: SongStream? = null
	private var error: Component? = null
	private var tooUneven = false
	private var opened = false
	private val taps = DoubleArray(TAPS_NEEDED)
	private var tapCount = 0
	private var tapsSkipped = 0
	private val beatMs = MS_PER_MINUTE / pack.charts.first().bpm.first().bpm
	private val songOffsetMs = pack.charts.first().offsetMs

	override fun init() {
		if (!opened) {
			opened = true
			open()
		}
		val centre = width / 2
		val y = height - BUTTONS_FROM_BOTTOM
		when (step) {
			Step.LISTEN_INTRO -> addStart(y) { startTapping(Step.LISTENING) }
			Step.WATCH_INTRO -> addStart(y) { startTapping(Step.WATCHING) }
			Step.REVIEW -> {
				addStepButtons(height / 2 - REVIEW_UP) { suggestedJudgementOffsetMs = nudged(suggestedJudgementOffsetMs, it) }
				addStepButtons(height / 2 - REVIEW_UP + ROW) { suggestedVisualOffsetMs = nudged(suggestedVisualOffsetMs, it) }
				addRenderableWidget(
					Button.builder(Component.translatable("hexium.calibration.save")) { save() }
						.bounds(centre - BUTTON_WIDTH - BUTTON_WIDTH / 2 - GAP, y, BUTTON_WIDTH, BUTTON_HEIGHT).build(),
				)
				addRenderableWidget(
					Button.builder(Component.translatable("hexium.calibration.again")) { goTo(Step.LISTEN_INTRO) }
						.bounds(centre - BUTTON_WIDTH / 2, y, BUTTON_WIDTH, BUTTON_HEIGHT).build(),
				)
			}
			else -> {}
		}
		addRenderableWidget(
			Button.builder(CommonComponents.GUI_CANCEL) { onClose() }
				.bounds(centre + BUTTON_WIDTH / 2 + GAP, y, BUTTON_WIDTH, BUTTON_HEIGHT).build(),
		)
	}

	private fun addStart(y: Int, start: () -> Unit) {
		val button = addRenderableWidget(
			Button.builder(Component.translatable("hexium.calibration.start")) { start() }
				.bounds(width / 2 - BUTTON_WIDTH / 2, y, BUTTON_WIDTH, BUTTON_HEIGHT).build(),
		)
		button.active = stream != null && !cantHearClicks()
	}

	/** Steady taps to clicks nobody can hear would pass as a result, so it won't start. */
	private fun cantHearClicks(): Boolean =
		step == Step.LISTEN_INTRO && minecraft.options.getFinalSoundSourceVolume(SoundSource.MUSIC) <= 0f

	private fun addStepButtons(y: Int, change: (Int) -> Unit) {
		val half = ROW_WIDTH / 2
		addRenderableWidget(
			Button.builder(Component.translatable("hexium.stage.less")) { change(-1) }
				.bounds(width / 2 - half, y, BUTTON_HEIGHT, BUTTON_HEIGHT).build(),
		)
		addRenderableWidget(
			Button.builder(Component.translatable("hexium.stage.more")) { change(1) }
				.bounds(width / 2 + half - BUTTON_HEIGHT, y, BUTTON_HEIGHT, BUTTON_HEIGHT).build(),
		)
	}

	private fun nudged(value: Int, steps: Int): Int =
		(value + steps * NUDGE_MS).coerceIn(-PlayerSettings.MAX_OFFSET_MS, PlayerSettings.MAX_OFFSET_MS)

	private fun open() {
		minecraft.musicManager.stopPlaying()
		try {
			val song = SongStream.open(pack.folder.resolve(ChartFolder.AUDIO_FILE))
			stream = song
			clock = AudioClock(song)
		} catch (e: IOException) {
			failed(e)
		} catch (e: RuntimeException) {
			failed(e)
		}
	}

	private fun failed(cause: Exception) {
		LOGGER.warn("Can't play the song in {}", pack.folder, cause)
		error = Component.translatable("hexium.audio_test.failed", cause.message.orEmpty())
		stream?.close()
		stream = null
		clock = null
	}

	private fun goTo(next: Step) {
		step = next
		rebuildWidgets()
	}

	private fun startTapping(next: Step) {
		val audioClock = clock ?: return
		tapCount = 0
		tapsSkipped = 0
		tooUneven = false
		LaneInput.clear()
		LaneInput.capturing = true
		audioClock.pause()
		audioClock.playFrom(-LEAD_IN_MS)
		goTo(next)
	}

	private fun readTaps(audioClock: AudioClock) {
		// A click on Start leaves that button focused, and Space or Enter must not press it mid-step.
		if (focused != null) focused = null
		// Taps never reach vanilla, so it would think nobody's there and lower the frame rate.
		if (LaneInput.hasEvent) minecraft.framerateLimitTracker.onInputReceived()
		while (LaneInput.hasEvent) {
			val songMs = audioClock.songTimeAt(LaneInput.timeNs)
			// Before this the clock is still settling onto the music.
			if (LaneInput.down && tapCount < TAPS_NEEDED && songMs >= SETTLED_MS) {
				// The first few taps are the player finding the beat.
				if (tapsSkipped < TAPS_TO_SKIP) tapsSkipped++ else taps[tapCount++] = songMs - songOffsetMs
			}
			LaneInput.next()
		}
		if (tapCount == TAPS_NEEDED) {
			finishTapping(audioClock)
		} else if (stream?.finished == true) {
			// Out of clicks, and the clock would carry on without them.
			tapCount = 0
			finishTapping(audioClock)
		}
	}

	private fun finishTapping(audioClock: AudioClock) {
		audioClock.pause()
		LaneInput.capturing = false
		val estimate = Calibration.estimate(taps, tapCount, beatMs)
		if (estimate == null || !estimate.isSteady) {
			tooUneven = true
			goTo(if (step == Step.LISTENING) Step.LISTEN_INTRO else Step.WATCH_INTRO)
			return
		}
		accept(estimate)
	}

	private fun accept(estimate: TapEstimate) {
		if (step == Step.LISTENING) {
			suggestedJudgementOffsetMs = Calibration.judgementOffsetMs(estimate)
			goTo(Step.WATCH_INTRO)
		} else {
			suggestedVisualOffsetMs = Calibration.visualOffsetMs(suggestedJudgementOffsetMs, estimate)
				.coerceIn(-PlayerSettings.MAX_OFFSET_MS, PlayerSettings.MAX_OFFSET_MS)
			goTo(Step.REVIEW)
		}
	}

	private fun save() {
		PlaySettings.setOffsets(suggestedVisualOffsetMs, suggestedJudgementOffsetMs)
		onClose()
	}

	override fun extractRenderState(graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, a: Float) {
		super.extractRenderState(graphics, mouseX, mouseY, a)
		graphics.centeredText(font, title, width / 2, TITLE_Y, TEXT_COLOR)
		val problem = error
		if (problem != null) {
			graphics.centeredText(font, problem, width / 2, height / 2, TEXT_COLOR)
			return
		}
		val audioClock = clock ?: return
		// Silent for the notes, so only the eye can be followed.
		stream?.setGain(if (step == Step.WATCHING) 0f else minecraft.options.getFinalSoundSourceVolume(SoundSource.MUSIC))
		if (step == Step.LISTENING || step == Step.WATCHING) {
			audioClock.update()
			readTaps(audioClock)
		}
		when (step) {
			Step.LISTEN_INTRO -> drawIntro(graphics, "hexium.calibration.listen_intro")
			Step.WATCH_INTRO -> drawIntro(graphics, "hexium.calibration.watch_intro")
			Step.LISTENING -> drawTapCount(graphics, "hexium.calibration.listening")
			Step.WATCHING -> {
				drawNotes(graphics, audioClock.songTimeMs)
				drawTapCount(graphics, "hexium.calibration.watching")
			}
			Step.REVIEW -> drawReview(graphics)
		}
	}

	private fun drawIntro(graphics: GuiGraphicsExtractor, key: String) {
		graphics.centeredText(font, Component.translatable(key), width / 2, height / 2 - LINE, TEXT_COLOR)
		if (cantHearClicks()) {
			graphics.centeredText(font, Component.translatable("hexium.calibration.no_sound"), width / 2, height / 2 + LINE, WARNING_COLOR)
		} else if (tooUneven) {
			graphics.centeredText(font, Component.translatable("hexium.calibration.too_uneven"), width / 2, height / 2 + LINE, WARNING_COLOR)
		}
	}

	private fun drawTapCount(graphics: GuiGraphicsExtractor, key: String) {
		graphics.centeredText(font, Component.translatable(key), width / 2, TEXT_Y, TEXT_COLOR)
		val count = Component.translatable("hexium.calibration.taps", tapCount, TAPS_NEEDED)
		graphics.centeredText(font, count, width / 2, TEXT_Y + LINE, TEXT_COLOR)
	}

	/** Drawn at song time with no offset, since this step measures the player's eye with nothing in the way. */
	private fun drawNotes(graphics: GuiGraphicsExtractor, songMs: Double) {
		val lineY = height - LINE_FROM_BOTTOM
		val half = NOTE_WIDTH / 2
		val centre = width / 2
		graphics.fill(centre - half - LINE_OVERHANG, lineY - 1, centre + half + LINE_OVERHANG, lineY + 1, LINE_COLOR)
		val pixelsPerMs = (lineY - NOTES_FROM_TOP) / NOTE_VISIBLE_MS
		var beat = Math.ceil((songMs - NOTE_KEEP_MS - songOffsetMs) / beatMs).toLong()
		while (true) {
			val distance = (beat * beatMs + songOffsetMs - songMs) * pixelsPerMs
			if (distance > lineY - NOTES_FROM_TOP) break
			val y = lineY - Math.round(distance).toInt()
			graphics.fill(centre - half, y - NOTE_HEIGHT / 2, centre + half, y + NOTE_HEIGHT / 2, TEXT_COLOR)
			beat++
		}
	}

	private fun drawReview(graphics: GuiGraphicsExtractor) {
		val centre = width / 2
		val top = height / 2 - REVIEW_UP
		graphics.centeredText(font, Component.translatable("hexium.calibration.review"), centre, top - LINE * 2, TEXT_COLOR)
		graphics.centeredText(font, Component.translatable("hexium.calibration.judgement_offset", suggestedJudgementOffsetMs), centre, top + LABEL_DOWN, TEXT_COLOR)
		graphics.centeredText(font, Component.translatable("hexium.stage.visual_offset", suggestedVisualOffsetMs), centre, top + ROW + LABEL_DOWN, TEXT_COLOR)
	}

	override fun onClose() {
		minecraft.gui.setScreen(lastScreen)
	}

	override fun removed() {
		LaneInput.capturing = false
		try {
			stream?.close()
		} finally {
			stream = null
			clock = null
			HexiumSession.end()
		}
	}

	private companion object {
		val LOGGER = LoggerFactory.getLogger(Hexium.MOD_ID)
		val TITLE: Component = Component.translatable("hexium.calibration.title")
		const val MS_PER_MINUTE = 60000.0
		const val LEAD_IN_MS = 2000.0
		const val SETTLED_MS = 500.0
		const val TAPS_TO_SKIP = 2
		const val TAPS_NEEDED = 16
		const val NUDGE_MS = 5
		const val TITLE_Y = 15
		const val TEXT_Y = 35
		const val LINE = 12
		const val LABEL_DOWN = 6
		const val ROW = 24
		const val ROW_WIDTH = 240
		const val REVIEW_UP = 24
		const val BUTTONS_FROM_BOTTOM = 30
		const val BUTTON_WIDTH = 90
		const val BUTTON_HEIGHT = 20
		const val GAP = 4
		const val LINE_FROM_BOTTOM = 50
		const val LINE_OVERHANG = 20
		const val NOTES_FROM_TOP = 65
		const val NOTE_WIDTH = 80
		const val NOTE_HEIGHT = 6
		const val NOTE_VISIBLE_MS = 1200.0
		const val NOTE_KEEP_MS = 100.0
		const val TEXT_COLOR = -1
		const val LINE_COLOR = 0xFFFFD040.toInt()
		const val WARNING_COLOR = 0xFFFF9A3C.toInt()
	}
}
