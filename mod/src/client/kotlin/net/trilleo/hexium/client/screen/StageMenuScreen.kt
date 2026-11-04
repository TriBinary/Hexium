package net.trilleo.hexium.client.screen

import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.Component
import net.trilleo.hexium.client.session.StageWorld

/** What Esc opens on the stage, in place of vanilla's pause menu. */
class StageMenuScreen : Screen(Component.translatable("menu.paused")) {
	override fun init() {
		val x = width / 2 - BUTTON_WIDTH / 2
		val y = height / 2 - BUTTON_HEIGHT
		addRenderableWidget(
			Button.builder(Component.translatable("menu.returnToGame")) { onClose() }
				.bounds(x, y, BUTTON_WIDTH, BUTTON_HEIGHT).build(),
		)
		addRenderableWidget(
			Button.builder(Component.translatable("hexium.stage.leave")) { StageWorld.leave(minecraft) }
				.bounds(x, y + BUTTON_HEIGHT + GAP, BUTTON_WIDTH, BUTTON_HEIGHT).build(),
		)
	}

	override fun extractRenderState(graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, a: Float) {
		super.extractRenderState(graphics, mouseX, mouseY, a)
		graphics.centeredText(font, title, width / 2, height / 2 - BUTTON_HEIGHT - TITLE_ABOVE, TEXT_COLOR)
	}

	private companion object {
		const val BUTTON_WIDTH = 200
		const val BUTTON_HEIGHT = 20
		const val GAP = 4
		const val TITLE_ABOVE = 20
		const val TEXT_COLOR = -1
	}
}
