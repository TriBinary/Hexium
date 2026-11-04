package net.trilleo.hexium.core.replay

/**
 * What reaches [recorder] during a play, which is where the pause rule lives. Resuming rewinds the
 * song so the player can get back in time, but judging and recording only reopen when it comes back to
 * the point where they paused, and a key that changed in between is synced at that exact time. So
 * a replay's times never go down, and a replay can't tell a pause happened.
 *
 * A key reaches the game after it happened, often after a later frame has already read the time. So
 * aim samples, and the judge's clock with them, wait [HOLD_BACK_MS] behind the song, and a key is
 * judged at once at its own time, among the samples around it. Only a key older than that gets moved
 * up to the judge's time, which [lateKeyCount] counts. All input must come through here, so it stays in order with the samples. Doesn't allocate.
 */
class PlayInput(private val recorder: ReplayRecorder, laneCount: Int) {
	/** Where the keys really are. */
	private val down = BooleanArray(laneCount)

	/** Where the judge thinks they are. */
	private val judged = BooleanArray(laneCount)

	private var paused = false

	/** Samples not sent yet, oldest first, in a fixed ring. */
	private val aimTimes = DoubleArray(AIM_CAPACITY)
	private val aimXs = FloatArray(AIM_CAPACITY)
	private val aimYs = FloatArray(AIM_CAPACITY)
	private var aimHead = 0
	private var aimCount = 0

	/** True once input reaches the judge, which isn't while paused or during the rewind after it. */
	var isOpen = true
		private set

	/** Everything up to here has been played. Before the first pause that's nothing. */
	var playedUntilMs = Double.NEGATIVE_INFINITY
		private set

	/** Keys older than the judge's current time, so they count as happening at it. */
	var lateKeyCount = 0
		private set

	fun isLaneDown(lane: Int): Boolean = down[lane]

	/** A lane key changing, wherever the play is. */
	fun lane(lane: Int, isDown: Boolean, timeMs: Double) {
		if (!isOpen && !paused && timeMs >= playedUntilMs) reopen()
		down[lane] = isDown
		if (!isOpen) return
		sendAimUpTo(timeMs)
		if (timeMs < recorder.latestMs) lateKeyCount++
		send(lane, isDown, timeMs)
	}

	/** Call once per frame while the song is running, after that frame's input. */
	fun frame(timeMs: Double, aimX: Float, aimY: Float) {
		if (!timeMs.isFinite()) return
		if (!isOpen && !paused && timeMs >= playedUntilMs) reopen()
		if (!isOpen) return
		if (aimCount == AIM_CAPACITY) sendOldestAim()
		val slot = (aimHead + aimCount) % AIM_CAPACITY
		aimTimes[slot] = timeMs
		aimXs[slot] = aimX
		aimYs[slot] = aimY
		aimCount++
		val judgedUntilMs = timeMs - HOLD_BACK_MS
		sendAimUpTo(judgedUntilMs)
		recorder.advanceTo(judgedUntilMs)
	}

	/** Stops the play at [timeMs]. Pausing again during a rewind keeps the first pause point. */
	fun pause(timeMs: Double) {
		if (isOpen) {
			sendAimUpTo(Double.POSITIVE_INFINITY)
			recorder.advanceTo(timeMs)
			playedUntilMs = timeMs
		}
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

	/** The play is over: sends what's waiting and judges everything left. */
	fun finish() {
		sendAimUpTo(Double.POSITIVE_INFINITY)
		recorder.finish()
	}

	private fun sendAimUpTo(timeMs: Double) {
		while (aimCount > 0 && aimTimes[aimHead] <= timeMs) sendOldestAim()
	}

	private fun sendOldestAim() {
		recorder.aimSample(aimTimes[aimHead], aimXs[aimHead], aimYs[aimHead])
		aimHead = (aimHead + 1) % AIM_CAPACITY
		aimCount--
	}

	companion object {
		const val REWIND_MS = 3000.0

		/** Covers a key a whole frame old at about 30 FPS, plus its way to the game. Misses and ticks show this much later. */
		const val HOLD_BACK_MS = 50.0

		/** Room for the hold-back at 5000 FPS. Past that the oldest sample just goes early. */
		private const val AIM_CAPACITY = 256
	}
}
