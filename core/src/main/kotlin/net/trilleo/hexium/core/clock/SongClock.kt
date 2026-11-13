package net.trilleo.hexium.core.clock

import kotlin.math.abs

/**
 * Song time from a steady wall clock, gently pulled toward the audio position. The raw position
 * only moves in steps, so it can't place notes itself, but over a song it decides where the music
 * is. Knows nothing about where its numbers come from, so the mod feeds it and tests can too.
 *
 * Doesn't allocate.
 */
class SongClock {
	private var baseSongMs = 0.0
	private var baseWallNs = 0L
	private var lastObservedNs = 0L
	/** [nowMs] never returns less than this, except after [start]. */
	private var floorMs = Double.NEGATIVE_INFINITY

	var running = false
		private set

	/** The audio position minus the clock, at the last [observe]. Positive means the music is ahead. */
	var driftMs = 0.0
		private set

	/** The largest [driftMs] either way since [start]. */
	var maxDriftMs = 0.0
		private set

	/** How many times the audio was too far away to pull toward, since this clock was made. */
	var resyncCount = 0
		private set

	/** Starts running from [songMs]. */
	fun start(songMs: Double, wallNs: Long) {
		seek(songMs, wallNs)
		running = true
	}

	/** Moves to [songMs], running or paused as it was. Starting and seeking are the only ways time can go backwards. */
	fun seek(songMs: Double, wallNs: Long) {
		baseSongMs = songMs
		baseWallNs = wallNs
		lastObservedNs = wallNs
		floorMs = Double.NEGATIVE_INFINITY
		driftMs = 0.0
		maxDriftMs = 0.0
	}

	fun pause(wallNs: Long) {
		if (!running) return
		baseSongMs = nowMs(wallNs)
		baseWallNs = wallNs
		running = false
	}

	/** Tells the clock where the audio is. Call once per frame. A position that isn't finite is ignored. */
	fun observe(audioMs: Double, wallNs: Long) {
		if (!running || !audioMs.isFinite()) return
		val sinceLastNs = wallNs - lastObservedNs
		if (sinceLastNs <= 0L) return
		lastObservedNs = wallNs

		val error = audioMs - mapMs(wallNs)
		driftMs = error
		if (abs(error) > maxDriftMs) maxDriftMs = abs(error)
		if (abs(error) > RESYNC_MS) {
			baseSongMs = audioMs
			baseWallNs = wallNs
			resyncCount++
			return
		}
		// This is a share of the time passed, so it's the same at any frame rate. Since the error is
		// under RESYNC_MS, the pull is always less than the time passed, so time never reverses.
		val share = sinceLastNs / NANOS_PER_MS / PULL_TIME_MS
		baseSongMs += error * (if (share < 1.0) share else 1.0)
	}

	/** Song time at [wallNs], for an input timestamp. May be earlier than [nowMs] has already returned. */
	fun mapMs(wallNs: Long): Double {
		if (!running) return baseSongMs
		return baseSongMs + (wallNs - baseWallNs) / NANOS_PER_MS
	}

	/** Song time for this frame. Never goes backwards. */
	fun nowMs(wallNs: Long): Double {
		val time = mapMs(wallNs)
		if (time > floorMs) floorMs = time
		return floorMs
	}

	private companion object {
		const val NANOS_PER_MS = 1_000_000.0

		/** Longer smooths out the steps better. Shorter follows a sound card's own speed more closely. */
		const val PULL_TIME_MS = 1000.0

		/** Past this, something has happened, such as an audio stall. Pulling would take too long. */
		const val RESYNC_MS = 100.0
	}
}
