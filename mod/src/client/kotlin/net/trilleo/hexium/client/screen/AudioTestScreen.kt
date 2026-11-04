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
import net.trilleo.hexium.client.debug.DebugOverlay
import net.trilleo.hexium.client.session.HexiumSession
import net.trilleo.hexium.core.chart.ChartFolder
import net.trilleo.hexium.core.chart.TimingMap
import org.slf4j.LoggerFactory

/**
 * Plays a pack's song with a flash on every beat and the clock's numbers on screen, for checking
 * that the music and the clock stay together over a whole song. Holds a session from the menu that
 * opened it until it closes.
 */
class AudioTestScreen(private val lastScreen: Screen, private val pack: ChartPack) : Screen(TITLE) {
	private val timing = pack.charts.first().let { TimingMap(it.offsetMs, it.bpm) }
	private var stream: SongStream? = null
	private var clock: AudioClock? = null
	private var overlay: DebugOverlay? = null
	private var error: Component? = null
	private var paused = false
	private var opened = false

	override fun init() {
		if (!opened) {
			opened = true
			open()
		}
		val y = height - BUTTONS_FROM_BOTTOM
		val centre = width / 2
		val pause = addRenderableWidget(
			Button.builder(pauseLabel()) { button ->
				togglePause()
				button.message = pauseLabel()
			}.bounds(centre - BUTTON_WIDTH - BUTTON_WIDTH / 2 - GAP, y, BUTTON_WIDTH, BUTTON_HEIGHT).build(),
		)
		val back = addRenderableWidget(
			Button.builder(Component.translatable("hexium.audio_test.rewind")) {
				clock?.let { it.seek((it.songTimeMs - REWIND_MS).coerceAtLeast(0.0)) }
			}.bounds(centre - BUTTON_WIDTH / 2, y, BUTTON_WIDTH, BUTTON_HEIGHT).build(),
		)
		pause.active = stream != null
		back.active = stream != null
		addRenderableWidget(
			Button.builder(CommonComponents.GUI_BACK) { onClose() }
				.bounds(centre + BUTTON_WIDTH / 2 + GAP, y, BUTTON_WIDTH, BUTTON_HEIGHT).build(),
		)
	}

	private fun open() {
		minecraft.musicManager.stopPlaying()
		try {
			val song = SongStream.open(pack.folder.resolve(ChartFolder.AUDIO_FILE))
			stream = song
			val audioClock = AudioClock(song)
			audioClock.play()
			clock = audioClock
			overlay = DebugOverlay(audioClock, song)
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
	}

	private fun pauseLabel(): Component =
		Component.translatable(if (paused) "hexium.audio_test.resume" else "hexium.audio_test.pause")

	private fun togglePause() {
		val audioClock = clock ?: return
		paused = !paused
		if (paused) audioClock.pause() else audioClock.play()
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
		val song = stream ?: return
		song.setGain(minecraft.options.getFinalSoundSourceVolume(SoundSource.MUSIC))
		audioClock.update()
		overlay?.update()
		overlay?.draw(graphics, font, TEXT_X, OVERLAY_Y)

		val beat = timing.msToBeat(audioClock.songTimeMs)
		val sinceBeat = beat - Math.floor(beat)
		if (sinceBeat < FLASH_BEATS) {
			val accent = Math.floorMod(Math.floor(beat).toLong(), BEATS_PER_BAR) == 0L
			val half = FLASH_SIZE / 2
			graphics.fill(
				width / 2 - half,
				height / 2 - half,
				width / 2 + half,
				height / 2 + half,
				if (accent) ACCENT_COLOR else BEAT_COLOR,
			)
		}
	}

	override fun onClose() {
		minecraft.gui.setScreen(lastScreen)
	}

	override fun removed() {
		try {
			stream?.close()
		} finally {
			stream = null
			clock = null
			overlay = null
			HexiumSession.end()
		}
	}

	private companion object {
		val LOGGER = LoggerFactory.getLogger(Hexium.MOD_ID)
		val TITLE: Component = Component.translatable("hexium.audio_test.title")
		const val TITLE_Y = 15
		const val TEXT_X = 10
		const val OVERLAY_Y = 30
		const val TEXT_COLOR = -1
		const val BUTTONS_FROM_BOTTOM = 30
		const val BUTTON_WIDTH = 90
		const val BUTTON_HEIGHT = 20
		const val GAP = 4
		const val REWIND_MS = 3000.0
		const val FLASH_BEATS = 0.2
		const val FLASH_SIZE = 40
		const val BEATS_PER_BAR = 4L
		const val BEAT_COLOR = -1
		const val ACCENT_COLOR = -43691
	}
}
