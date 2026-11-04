package net.trilleo.hexium.client.session

import net.minecraft.client.Minecraft
import net.trilleo.hexium.client.play.PlaySession
import net.trilleo.hexium.client.screen.StageMenuScreen

/**
 * The one switch between Hexium and vanilla. Every mixin and event hook returns early unless
 * [active], so normal worlds and servers are untouched. The title screen button is the one
 * exception, since it opens the menu. Only that menu may start a session.
 */
object HexiumSession {
	@Volatile
	private var active = false
	private var play: PlaySession? = null

	@JvmStatic
	fun active(): Boolean = active

	fun start(play: PlaySession? = null) {
		active = true
		this.play = play
	}

	/** The play going on, if this session has one. */
	@JvmStatic
	fun play(): PlaySession? = play

	/** Called at the start of every frame, after input arrives. */
	@JvmStatic
	fun frame() {
		play?.frame()
	}

	/** Render thread only. */
	fun end() {
		if (!active) return
		active = false
		play?.close()
		play = null
		StageWorld.restoreView()
	}

	/** True if input is going to the game rather than a screen. */
	@JvmStatic
	fun noScreenOpen(): Boolean = Minecraft.getInstance().gui.screen() == null

	/** Esc, or the window losing focus. A screen that's open handles Esc itself. */
	@JvmStatic
	fun requestPause() {
		if (noScreenOpen()) Minecraft.getInstance().gui.setScreen(StageMenuScreen())
	}
}
