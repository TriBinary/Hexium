package net.trilleo.hexium.client.screen

import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.Component
import net.trilleo.hexium.client.play.PlaySettings
import net.trilleo.hexium.client.session.HexiumSession
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
			Button.builder(Component.translatable("hexium.results.retry")) { HexiumSession.retry() }
				.bounds(x, y + ROW, BUTTON_WIDTH, BUTTON_HEIGHT).build(),
		)
		addRenderableWidget(
			Button.builder(Component.translatable("hexium.stage.leave")) { StageWorld.leave(minecraft) }
				.bounds(x, y + 2 * ROW, BUTTON_WIDTH, BUTTON_HEIGHT).build(),
		)
		addStepButtons(y + 3 * ROW + GROUP_GAP) { PlaySettings.changeNoteSpeed(it) }
		addStepButtons(y + 4 * ROW + GROUP_GAP) { PlaySettings.changeVisualOffset(it) }
	}

	private fun addStepButtons(y: Int, change: (Int) -> Unit) {
		val x = width / 2 - BUTTON_WIDTH / 2
		addRenderableWidget(
			Button.builder(Component.translatable("hexium.stage.less")) { change(-1) }
				.bounds(x, y, BUTTON_HEIGHT, BUTTON_HEIGHT).build(),
		)
		addRenderableWidget(
			Button.builder(Component.translatable("hexium.stage.more")) { change(1) }
				.bounds(x + BUTTON_WIDTH - BUTTON_HEIGHT, y, BUTTON_HEIGHT, BUTTON_HEIGHT).build(),
		)
	}

	override fun extractRenderState(graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, a: Float) {
		super.extractRenderState(graphics, mouseX, mouseY, a)
		val y = height / 2 - BUTTON_HEIGHT
		graphics.centeredText(font, title, width / 2, y - TITLE_ABOVE, TEXT_COLOR)
		val noteSpeed = Component.translatable("hexium.stage.note_speed", PlaySettings.noteSpeed)
		graphics.centeredText(font, noteSpeed, width / 2, y + 3 * ROW + GROUP_GAP + LABEL_DOWN, TEXT_COLOR)
		val visualOffset = Component.translatable("hexium.stage.visual_offset", PlaySettings.visualOffsetMs)
		graphics.centeredText(font, visualOffset, width / 2, y + 4 * ROW + GROUP_GAP + LABEL_DOWN, TEXT_COLOR)
	}

	private companion object {
		const val BUTTON_WIDTH = 200
		const val BUTTON_HEIGHT = 20
		const val ROW = 24
		const val GROUP_GAP = 8
		const val LABEL_DOWN = 6
		const val TITLE_ABOVE = 20
		const val TEXT_COLOR = -1
	}
}
