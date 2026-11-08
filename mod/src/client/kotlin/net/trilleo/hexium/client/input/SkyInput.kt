package net.trilleo.hexium.client.input

import net.minecraft.client.Minecraft
import net.trilleo.hexium.client.session.HexiumSession
import net.trilleo.hexium.core.chart.SkyButton
import org.lwjgl.sdl.SDLMouse
import org.lwjgl.sdl.SDL_MouseButtonEvent

/**
 * The left and right mouse buttons for sky notes, read straight from SDL's events with their
 * timestamps, as [LaneInput] reads keys. A fixed ring holds them until the frame drains it, so
 * nothing here allocates.
 */
object SkyInput {
	private val BUTTONS = SkyButton.entries.toTypedArray()
	private const val CAPACITY = 256

	private val buttons = IntArray(CAPACITY)
	private val downs = BooleanArray(CAPACITY)
	private val times = LongArray(CAPACITY)
	private var head = 0
	private var count = 0

	/** Whether Hexium kept each button's last press from vanilla, so its release is kept too and vanilla never sees half a click. */
	private val tookPress = BooleanArray(BUTTONS.size)

	/** The next event's button, once [hasEvent] says there's one. */
	val button: SkyButton
		get() = BUTTONS[buttons[head]]
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

	/**
	 * Returns true if vanilla shouldn't see the event. Taken while a screen is open too, since the pause
	 * rule needs to know which buttons are down on resuming. A press reaches vanilla while a screen is
	 * open, or when it grabs the mouse back after the window lost focus. Its release follows it either
	 * way, even if the screen has closed since, so vanilla never sees half a click.
	 */
	@JvmStatic
	fun onMouseButtonEvent(event: Long): Boolean {
		val button = when (SDL_MouseButtonEvent.nbutton(event).toInt()) {
			SDLMouse.SDL_BUTTON_LEFT -> SkyButton.L
			SDLMouse.SDL_BUTTON_RIGHT -> SkyButton.R
			else -> return false
		}
		val isDown = SDL_MouseButtonEvent.ndown(event)
		if (HexiumSession.play()?.acceptingInput == true) push(button, isDown, SDL_MouseButtonEvent.ntimestamp(event))
		return !vanillaSees(button, isDown)
	}

	private fun vanillaSees(button: SkyButton, isDown: Boolean): Boolean {
		val b = button.ordinal
		if (!isDown) {
			val took = tookPress[b]
			tookPress[b] = false
			return !took
		}
		val playing = HexiumSession.play()?.acceptingInput == true
		tookPress[b] = playing && HexiumSession.noScreenOpen() && Minecraft.getInstance().mouseHandler.isMouseGrabbed
		return !tookPress[b]
	}

	/**
	 * For a new session. A press from before it, such as the click that started it, went to vanilla,
	 * so its release does too.
	 */
	fun forgetPresses() {
		tookPress.fill(false)
	}

	fun push(button: SkyButton, isDown: Boolean, atNs: Long) {
		if (count == CAPACITY) return
		val slot = (head + count) % CAPACITY
		buttons[slot] = button.ordinal
		downs[slot] = isDown
		times[slot] = atNs
		count++
	}
}
