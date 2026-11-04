package net.trilleo.hexium.client.input

import net.trilleo.hexium.client.session.HexiumSession
import org.lwjgl.sdl.SDLScancode
import org.lwjgl.sdl.SDL_KeyboardEvent

/**
 * Lane keys, read straight from SDL's events: vanilla only sees a key later in the frame, and SDL
 * stamps each event with the clock the song clock runs on. Keys are physical positions, so the left
 * hand sits the same on any keyboard layout. A fixed ring holds them until the frame drains it, so
 * nothing here allocates.
 */
object LaneInput {
	private val SCANCODES = intArrayOf(
		SDLScancode.SDL_SCANCODE_A,
		SDLScancode.SDL_SCANCODE_S,
		SDLScancode.SDL_SCANCODE_D,
		SDLScancode.SDL_SCANCODE_F,
	)
	private const val CAPACITY = 256

	private val lanes = IntArray(CAPACITY)
	private val downs = BooleanArray(CAPACITY)
	private val times = LongArray(CAPACITY)
	private var head = 0
	private var count = 0

	/** The next event's lane, once [hasEvent] says there's one. */
	val lane: Int
		get() = lanes[head]
	val down: Boolean
		get() = downs[head]

	/** When it happened, on SDL's tick counter. */
	val timeNs: Long
		get() = times[head]

	val hasEvent: Boolean
		get() = count > 0

	fun next() {
		head = (head + 1) % CAPACITY
		count--
	}

	fun clear() {
		head = 0
		count = 0
	}

	/** Returns true if the event was a lane key meant for the game, so vanilla shouldn't see it. */
	@JvmStatic
	fun onKeyEvent(event: Long): Boolean {
		val play = HexiumSession.play() ?: return false
		val lane = laneOf(SDL_KeyboardEvent.nscancode(event))
		if (lane < 0 || !play.acceptingInput) return false
		// Taken while a screen is open too, since the pause rule needs to know which keys are down on resuming.
		if (!SDL_KeyboardEvent.nrepeat(event)) push(lane, SDL_KeyboardEvent.ndown(event), SDL_KeyboardEvent.ntimestamp(event))
		return HexiumSession.noScreenOpen()
	}

	fun push(lane: Int, isDown: Boolean, atNs: Long) {
		if (count == CAPACITY) return
		val slot = (head + count) % CAPACITY
		lanes[slot] = lane
		downs[slot] = isDown
		times[slot] = atNs
		count++
	}

	private fun laneOf(scancode: Int): Int {
		for (i in SCANCODES.indices) {
			if (SCANCODES[i] == scancode) return i
		}
		return -1
	}
}
