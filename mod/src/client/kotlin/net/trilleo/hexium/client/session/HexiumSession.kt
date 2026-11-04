package net.trilleo.hexium.client.session

/**
 * The one switch between Hexium and vanilla. Every mixin and event hook returns early unless
 * [active], so normal worlds and servers are untouched. The title screen button is the one
 * exception, since it opens the menu. Only that menu may start a session, and nothing does yet.
 */
object HexiumSession {
	@JvmStatic
	fun active(): Boolean = false
}
