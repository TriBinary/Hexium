package net.trilleo.hexium.client.debug

import java.util.Locale
import net.minecraft.client.gui.Font
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.trilleo.hexium.client.audio.SongStream
import net.trilleo.hexium.client.clock.AudioClock
import org.lwjgl.sdl.SDLTimer

/**
 * Timing numbers for development, in English only. The text is rebuilt a few times a second, so
 * measuring doesn't allocate every frame.
 */
class DebugOverlay(private val clock: AudioClock, private val stream: SongStream) {
	private val lines = Array(LINE_COUNT) { "" }
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
		lastRefreshNs = now
		framesSinceRefresh = 0
		worstFrameNs = 0L
	}

	fun draw(graphics: GuiGraphicsExtractor, font: Font, x: Int, y: Int) {
		for (i in lines.indices) {
			graphics.text(font, lines[i], x, y + i * LINE_HEIGHT, TEXT_COLOR)
		}
	}

	private companion object {
		const val LINE_COUNT = 4
		const val LINE_HEIGHT = 10
		const val REFRESH_NS = 250_000_000L
		const val TEXT_COLOR = -1
	}
}
