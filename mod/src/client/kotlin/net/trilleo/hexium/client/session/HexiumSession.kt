package net.trilleo.hexium.client.session

/**
 * The one switch between Hexium and vanilla. Every mixin and event hook returns early unless
 * [active], so normal worlds and servers are untouched. The title screen button is the one
 * exception, since it opens the menu. Only that menu may start a session.
 */
object HexiumSession {
	private var active = false

	@JvmStatic
	fun active(): Boolean = active

	fun start() {
		active = true
	}

	fun end() {
		active = false
	}
}
