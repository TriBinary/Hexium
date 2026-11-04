package net.trilleo.hexium.client.session

import java.io.IOException
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.components.toasts.SystemToast
import net.minecraft.network.chat.Component
import net.trilleo.hexium.Hexium
import net.trilleo.hexium.client.play.PlaySession
import net.trilleo.hexium.client.screen.StageMenuScreen
import org.slf4j.LoggerFactory

/**
 * The one switch between Hexium and vanilla. Every mixin and event hook returns early unless
 * [active], so normal worlds and servers are untouched. The title screen button is the one
 * exception, since it opens the menu. Only that menu may start a session.
 */
object HexiumSession {
	private val LOGGER = LoggerFactory.getLogger(Hexium.MOD_ID)

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

	/** Starts the chart again on the stage that's already here. If it can't be read, the player stays where they are. */
	fun retry() {
		val old = play ?: return
		val minecraft = Minecraft.getInstance()
		val next = try {
			old.loadAgain()
		} catch (e: IOException) {
			cantRetry(minecraft, e)
			return
		} catch (e: RuntimeException) {
			cantRetry(minecraft, e)
			return
		}
		play = next
		old.close()
		minecraft.gui.setScreen(null)
	}

	private fun cantRetry(minecraft: Minecraft, cause: Exception) {
		LOGGER.warn("Can't play the chart again", cause)
		SystemToast.add(
			minecraft.gui.toastManager(),
			SystemToast.SystemToastId.PACK_LOAD_FAILURE,
			Component.translatable("hexium.menu.cant_play"),
			Component.literal(cause.message.orEmpty()),
		)
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
