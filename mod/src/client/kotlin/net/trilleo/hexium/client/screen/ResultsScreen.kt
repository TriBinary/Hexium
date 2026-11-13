package net.trilleo.hexium.client.screen

import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.Component
import net.trilleo.hexium.client.play.PlaySession
import net.trilleo.hexium.client.session.HexiumSession
import net.trilleo.hexium.client.session.StageWorld
import net.trilleo.hexium.core.judge.ClearLamp
import net.trilleo.hexium.core.judge.Judgement

/** Opens when a play finishes. Esc goes the same way as Leave Stage. */
class ResultsScreen(private val play: PlaySession) : Screen(Component.translatable("hexium.results.title")) {
	override fun init() {
		val y = height / 2 + BUTTONS_DOWN
		val gapLeft = width / 2 - BUTTON_WIDTH - GAP / 2
		addRenderableWidget(
			Button.builder(Component.translatable("hexium.results.retry")) { HexiumSession.retry() }
				.bounds(gapLeft, y, BUTTON_WIDTH, BUTTON_HEIGHT).build(),
		)
		addRenderableWidget(
			Button.builder(Component.translatable("hexium.stage.leave")) { onClose() }
				.bounds(gapLeft + BUTTON_WIDTH + GAP, y, BUTTON_WIDTH, BUTTON_HEIGHT).build(),
		)
	}

	override fun onClose() {
		StageWorld.leave(minecraft)
	}

	override fun extractRenderState(graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, a: Float) {
		super.extractRenderState(graphics, mouseX, mouseY, a)
		val centre = width / 2
		var y = height / 2 - TOP_UP
		graphics.centeredText(font, Component.literal(play.chartTitle), centre, y, TEXT_COLOR)
		y += LINE
		val difficulty = Component.translatable("hexium.difficulty.${play.chart.difficulty.name.lowercase()}")
		graphics.centeredText(font, Component.translatable("hexium.results.level", difficulty, play.chart.level), centre, y, TEXT_COLOR)
		y += LINE + GROUP_GAP

		val judge = play.judge
		graphics.pose().pushMatrix()
		graphics.pose().translate(centre.toFloat(), y.toFloat())
		graphics.pose().scale(SCORE_SCALE, SCORE_SCALE)
		graphics.centeredText(font, Component.literal(judge.score.toString()), 0, 0, TEXT_COLOR)
		graphics.pose().popMatrix()
		y += SCORE_HEIGHT

		val lamp = judge.clearLamp
		if (lamp != ClearLamp.NONE) graphics.centeredText(font, Component.translatable("hexium.lamp.${lamp.name.lowercase()}"), centre, y, LAMP_COLOR)
		y += LINE + GROUP_GAP

		for (judgement in Judgement.entries) {
			val name = Component.translatable("hexium.judgement.${judgement.name.lowercase()}")
			graphics.centeredText(font, Component.translatable("hexium.results.count", name, judge.count(judgement)), centre, y, TEXT_COLOR)
			y += LINE
		}
		graphics.centeredText(font, Component.translatable("hexium.results.max_combo", judge.maxCombo), centre, y, TEXT_COLOR)
		y += LINE + GROUP_GAP

		val saved = play.savedReplay
		val replay = if (saved != null) {
			Component.translatable("hexium.results.replay_saved", saved.fileName.toString())
		} else {
			Component.translatable("hexium.results.replay_not_saved")
		}
		graphics.centeredText(font, replay, centre, y, NOTE_COLOR)
	}

	private companion object {
		const val BUTTON_WIDTH = 120
		const val BUTTON_HEIGHT = 20
		const val GAP = 8
		const val TOP_UP = 100
		const val BUTTONS_DOWN = 76
		const val LINE = 12
		const val GROUP_GAP = 6
		const val SCORE_SCALE = 3f
		const val SCORE_HEIGHT = 30
		const val TEXT_COLOR = -1
		const val LAMP_COLOR = 0xFFFFD84A.toInt()
		const val NOTE_COLOR = 0xFFA0A0A0.toInt()
	}
}
