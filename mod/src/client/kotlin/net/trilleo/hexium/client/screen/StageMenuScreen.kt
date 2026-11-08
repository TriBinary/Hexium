package net.trilleo.hexium.client.screen

import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.CommonComponents
import net.minecraft.network.chat.Component
import net.trilleo.hexium.client.play.PlaySettings
import net.trilleo.hexium.client.session.HexiumSession
import net.trilleo.hexium.client.session.StageWorld

/**
 * What Esc opens on the stage, in place of vanilla's pause menu: resume, retry and leave, then the
 * play settings in two columns, each a value with - and + either side.
 */
class StageMenuScreen : Screen(Component.translatable("menu.paused")) {
	private fun top(): Int = (height - (6 * ROW + GROUP_GAP)) / 2 + TITLE_ABOVE / 2

	private fun settingsTop(): Int = top() + 3 * ROW + GROUP_GAP

	private fun leftX(): Int = width / 2 - COLUMN_GAP / 2 - COLUMN_WIDTH

	private fun rightX(): Int = width / 2 + COLUMN_GAP / 2

	override fun init() {
		val x = width / 2 - BUTTON_WIDTH / 2
		val y = top()
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
		val settings = settingsTop()
		// Note speed's buttons come first, so the game test's search for "+" finds them.
		addStepButtons(leftX(), settings) { PlaySettings.changeNoteSpeed(it) }
		addStepButtons(rightX(), settings) { PlaySettings.changeMouseSensitivity(it) }
		addStepButtons(leftX(), settings + ROW) { PlaySettings.changeVisualOffset(it) }
		addStepButtons(rightX(), settings + ROW) { PlaySettings.changeViewAssist(it) }
		addStepButtons(leftX(), settings + 2 * ROW) { PlaySettings.changePlayFov(it) }
	}

	private fun addStepButtons(x: Int, y: Int, change: (Int) -> Unit) {
		addRenderableWidget(
			Button.builder(Component.translatable("hexium.stage.less")) { change(-1) }
				.bounds(x, y, BUTTON_HEIGHT, BUTTON_HEIGHT).build(),
		)
		addRenderableWidget(
			Button.builder(Component.translatable("hexium.stage.more")) { change(1) }
				.bounds(x + COLUMN_WIDTH - BUTTON_HEIGHT, y, BUTTON_HEIGHT, BUTTON_HEIGHT).build(),
		)
	}

	override fun extractRenderState(graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, a: Float) {
		super.extractRenderState(graphics, mouseX, mouseY, a)
		graphics.centeredText(font, title, width / 2, top() - TITLE_ABOVE, TEXT_COLOR)
		val y = settingsTop() + LABEL_DOWN
		val left = leftX() + COLUMN_WIDTH / 2
		val right = rightX() + COLUMN_WIDTH / 2
		graphics.centeredText(font, Component.translatable("hexium.stage.note_speed", PlaySettings.noteSpeed), left, y, TEXT_COLOR)
		graphics.centeredText(font, Component.translatable("hexium.stage.mouse_sensitivity", PlaySettings.mouseSensitivity), right, y, TEXT_COLOR)
		graphics.centeredText(font, Component.translatable("hexium.stage.visual_offset", PlaySettings.visualOffsetMs), left, y + ROW, TEXT_COLOR)
		val assist = if (PlaySettings.viewAssist == 0) CommonComponents.OPTION_OFF else Component.literal(PlaySettings.viewAssist.toString())
		graphics.centeredText(font, Component.translatable("hexium.stage.view_assist", assist), right, y + ROW, TEXT_COLOR)
		graphics.centeredText(font, Component.translatable("hexium.stage.play_fov", PlaySettings.playFov), left, y + 2 * ROW, TEXT_COLOR)
	}

	private companion object {
		const val BUTTON_WIDTH = 200
		const val BUTTON_HEIGHT = 20
		/** Two columns and the gap fit a 320-wide GUI, and the longest label, "Visual Offset: -500 ms", fits between its buttons. */
		const val COLUMN_WIDTH = 155
		const val COLUMN_GAP = 8
		const val ROW = 24
		const val GROUP_GAP = 8
		const val LABEL_DOWN = 6
		const val TITLE_ABOVE = 20
		const val TEXT_COLOR = -1
	}
}
