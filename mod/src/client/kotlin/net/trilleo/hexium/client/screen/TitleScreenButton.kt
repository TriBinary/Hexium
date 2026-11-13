package net.trilleo.hexium.client.screen

import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents
import net.fabricmc.fabric.api.client.screen.v1.Screens
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.screens.TitleScreen
import net.minecraft.network.chat.Component
import net.trilleo.hexium.client.session.HexiumSession

/**
 * The way into Hexium: a full-width button under Multiplayer. The rows from Multiplayer up move up
 * one to make room, which keeps Options and Quit on small windows and Realms' icons on their button.
 *
 * It's the only hook that doesn't check for a session, since a session can only start from the menu
 * it opens.
 */
object TitleScreenButton {
	private const val WIDTH = 200
	private const val HEIGHT = 20
	private const val ROW = 24

	fun register() {
		ScreenEvents.AFTER_INIT.register { client, screen, _, _ ->
			if (screen is TitleScreen) {
				// No session outlives a return to the title screen, however it got here.
				HexiumSession.end()
				val widgets = Screens.getWidgets(screen)
				val multiplayer = Component.translatable("menu.multiplayer")
				// Demo mode has Reset Demo World in that row instead.
				val resetDemo = Component.translatable("menu.resetdemo")
				val anchor = widgets.firstOrNull { it.message == multiplayer || it.message == resetDemo }
				val y = anchor?.y ?: (screen.height / 4 + 48 + ROW)
				for (widget in widgets) {
					if (widget.y <= y) widget.y -= ROW
				}
				widgets += Button.builder(Component.translatable("hexium.title_button")) {
					client.gui.setScreen(HexiumMenuScreen(screen))
				}.bounds(screen.width / 2 - WIDTH / 2, y, WIDTH, HEIGHT).build()
			}
		}
	}
}
