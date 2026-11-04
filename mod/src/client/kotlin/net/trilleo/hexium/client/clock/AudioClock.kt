package net.trilleo.hexium.client.clock

import net.trilleo.hexium.client.audio.SongStream
import net.trilleo.hexium.core.clock.SongClock
import org.lwjgl.sdl.SDLTimer

/**
 * The only source of gameplay time: a [SongClock] fed by the playing song. Wall time is SDL's tick
 * counter, the clock SDL stamps input events with.
 */
class AudioClock(private val stream: SongStream) {
	val clock = SongClock()

	/** Song time for this frame, in milliseconds. */
	var songTimeMs = 0.0
		private set

	fun play() {
		stream.play()
		clock.start(songTimeMs, SDLTimer.SDL_GetTicksNS())
	}

	fun pause() {
		stream.pause()
		clock.pause(SDLTimer.SDL_GetTicksNS())
	}

	fun seek(ms: Double) {
		stream.seek(ms)
		songTimeMs = ms
		clock.seek(ms, SDLTimer.SDL_GetTicksNS())
	}

	/** Call once per frame, before anything reads the time. Doesn't allocate. */
	fun update() {
		val now = SDLTimer.SDL_GetTicksNS()
		stream.recoverIfLost(clock.nowMs(now))
		clock.observe(stream.positionMs(), now)
		songTimeMs = clock.nowMs(now)
	}
}
