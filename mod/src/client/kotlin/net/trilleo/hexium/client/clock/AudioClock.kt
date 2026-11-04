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

	/** True while the clock is running but hasn't reached the start of the song. */
	private var inLeadIn = false

	/** Starts from [leadInMs] before the song, so the first notes aren't sprung on the player. */
	fun playAfter(leadInMs: Double) {
		songTimeMs = -leadInMs
		play()
	}

	fun play() {
		inLeadIn = songTimeMs < 0.0
		if (!inLeadIn) stream.play()
		clock.start(songTimeMs, SDLTimer.SDL_GetTicksNS())
	}

	fun pause() {
		stream.pause()
		val now = SDLTimer.SDL_GetTicksNS()
		clock.pause(now)
		songTimeMs = clock.mapMs(now)
	}

	/** Goes back to [ms], which may be before the song, and carries on from there. Call while paused. */
	fun playFrom(ms: Double) {
		stream.seek(ms)
		songTimeMs = ms
		play()
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
		if (inLeadIn && clock.running && songTimeMs >= 0.0) {
			inLeadIn = false
			stream.play()
		}
	}

	/** Song time when something stamped [ticksNs] on SDL's tick counter happened. */
	fun songTimeAt(ticksNs: Long): Double = clock.mapMs(ticksNs)
}
