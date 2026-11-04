package net.trilleo.hexium.core.replay

/**
 * What reaches [recorder] during a play, which is where the pause rule lives. Resuming rewinds the
 * song so the player can get back in time, but judging and recording only reopen when it comes back to
 * the point where they paused, and a key that changed in between is synced at that exact time. So
 * a replay's times never go down, and a replay can't tell a pause happened. Doesn't allocate.
 */
class PlayInput(private val recorder: ReplayRecorder, laneCount: Int) {
	/** Where the keys really are. */
	private val down = BooleanArray(laneCount)

	/** Where the judge thinks they are. */
	private val judged = BooleanArray(laneCount)

	private var paused = false

	/** True once input reaches the judge, which isn't while paused or during the rewind after it. */
	var isOpen = true
		private set

	/** Everything up to here has been played. Before the first pause that's nothing. */
	var playedUntilMs = Double.NEGATIVE_INFINITY
		private set

	fun isLaneDown(lane: Int): Boolean = down[lane]

	/** A lane key changing, wherever the play is. */
	fun lane(lane: Int, isDown: Boolean, timeMs: Double) {
		if (!isOpen && !paused && timeMs >= playedUntilMs) reopen()
		down[lane] = isDown
		if (isOpen) send(lane, isDown, timeMs)
	}

	/** Call once per frame while the song is running, after that frame's input. */
	fun frame(timeMs: Double, aimX: Float, aimY: Float) {
		if (!isOpen && !paused && timeMs >= playedUntilMs) reopen()
		if (!isOpen) return
		recorder.aimSample(timeMs, aimX, aimY)
		recorder.advanceTo(timeMs)
	}

	/** Stops the play at [timeMs]. Pausing again during a rewind keeps the first pause point. */
	fun pause(timeMs: Double) {
		if (isOpen) playedUntilMs = timeMs
		isOpen = false
		paused = true
	}

	/** Returns the song time to go back to. */
	fun resume(): Double {
		paused = false
		return playedUntilMs - REWIND_MS
	}

	private fun reopen() {
		for (lane in down.indices) {
			if (down[lane] != judged[lane]) send(lane, down[lane], playedUntilMs)
		}
		isOpen = true
	}

	private fun send(lane: Int, isDown: Boolean, timeMs: Double) {
		judged[lane] = isDown
		if (isDown) recorder.pressLane(lane, timeMs) else recorder.releaseLane(lane, timeMs)
	}

	companion object {
		const val REWIND_MS = 3000.0
	}
}
