package net.trilleo.hexium.client.debug

import java.util.Locale
import net.minecraft.client.gui.Font
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.locale.Language
import net.minecraft.network.chat.FormattedText
import net.minecraft.util.FormattedCharSequence
import net.trilleo.hexium.client.audio.SongStream
import net.trilleo.hexium.client.clock.AudioClock
import net.trilleo.hexium.client.play.PlaySettings
import org.lwjgl.sdl.SDLTimer

/**
 * Timing numbers for development, in English only. The text is rebuilt a few times a second, so
 * measuring doesn't allocate every frame beyond vanilla's small object per text drawn.
 */
class DebugOverlay(
	private val clock: AudioClock,
	private val stream: SongStream,
	private val inputDelay: InputDelay? = null,
) {
	private val lines = Array(if (inputDelay != null) PLAY_LINE_COUNT else LINE_COUNT) { "" }
	private val visualLines = Array(lines.size) { FormattedCharSequence.EMPTY }
	private var lastFrameNs = 0L
	private var lastRefreshNs = 0L
	private var framesSinceRefresh = 0
	private var worstFrameNs = 0L

	fun update() {
		val now = SDLTimer.SDL_GetTicksNS()
		if (lastFrameNs != 0L) {
			val frame = now - lastFrameNs
			if (frame > worstFrameNs) worstFrameNs = frame
			framesSinceRefresh++
		}
		lastFrameNs = now
		if (now - lastRefreshNs >= REFRESH_NS) {
			refresh(now)
		}
	}

	/** Call when it isn't shown, so the gap doesn't count as one long frame when it comes back. */
	fun stop() {
		lastFrameNs = 0L
		lastRefreshNs = 0L
		framesSinceRefresh = 0
		worstFrameNs = 0L
	}

	private fun refresh(now: Long) {
		val songClock = clock.clock
		val averageMs = if (framesSinceRefresh == 0) 0.0 else (now - lastRefreshNs) / 1e6 / framesSinceRefresh
		lines[0] = String.format(Locale.ROOT, "song %.1f s / %.1f s", clock.songTimeMs / 1000.0, stream.lengthMs / 1000.0)
		lines[1] = String.format(Locale.ROOT, "drift %+.1f ms, max %.1f ms", songClock.driftMs, songClock.maxDriftMs)
		lines[2] = String.format(Locale.ROOT, "frame %.1f ms, worst %.1f ms", averageMs, worstFrameNs / 1e6)
		lines[3] = String.format(
			Locale.ROOT,
			"resyncs %d, underruns %d, latency %s",
			songClock.resyncCount,
			stream.underruns,
			if (stream.usesLatency) "known" else "unknown",
		)
		if (inputDelay != null) {
			lines[4] = String.format(Locale.ROOT, "key stamp age %.1f / %.1f / %.1f ms", inputDelay.minMs, inputDelay.averageMs, inputDelay.maxMs)
			lines[5] = String.format(
				Locale.ROOT,
				"offsets A %d ms, B %d ms, speed %d",
				PlaySettings.visualOffsetMs,
				PlaySettings.judgementOffsetMs,
				PlaySettings.noteSpeed,
			)
		}
		for (i in lines.indices) visualLines[i] = Language.getInstance().getVisualOrder(FormattedText.of(lines[i]))
		lastRefreshNs = now
		framesSinceRefresh = 0
		worstFrameNs = 0L
	}

	fun draw(graphics: GuiGraphicsExtractor, font: Font, x: Int, y: Int) {
		for (i in visualLines.indices) {
			graphics.text(font, visualLines[i], x, y + i * LINE_HEIGHT, TEXT_COLOR)
		}
	}

	private companion object {
		const val LINE_COUNT = 4
		const val PLAY_LINE_COUNT = 6
		const val LINE_HEIGHT = 10
		const val REFRESH_NS = 250_000_000L
		const val TEXT_COLOR = -1
	}
}
