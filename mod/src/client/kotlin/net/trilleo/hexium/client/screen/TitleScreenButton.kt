package net.trilleo.hexium.client.screen

import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents
import net.fabricmc.fabric.api.client.screen.v1.Screens
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.screens.TitleScreen
import net.minecraft.network.chat.Component

/**
 * The way into Hexium: one button left of Singleplayer. It's the only hook that doesn't check for a
 * session, since a session can only start from the menu it opens.
 */
object TitleScreenButton {
	private const val WIDTH = 56
	private const val HEIGHT = 20
	private const val GAP = 2

	fun register() {
		ScreenEvents.AFTER_INIT.register { client, screen, _, _ ->
			if (screen is TitleScreen) {
				val widgets = Screens.getWidgets(screen)
				val singleplayer = Component.translatable("menu.singleplayer")
				// Demo mode has no Singleplayer button: line up with the first button instead.
				val anchor = widgets.firstOrNull { it.message == singleplayer } ?: widgets.firstOrNull()
				val x = screen.width / 2 - 100 - GAP - WIDTH
				val y = anchor?.y ?: (screen.height / 4 + 48)
				widgets += Button.builder(Component.translatable("hexium.title_button")) {
					client.gui.setScreen(HexiumMenuScreen(screen))
				}.bounds(x, y, WIDTH, HEIGHT).build()
			}
		}
	}
}
