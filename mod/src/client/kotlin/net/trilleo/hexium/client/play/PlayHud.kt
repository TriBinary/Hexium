package net.trilleo.hexium.client.play

import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.Font
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.Style
import net.minecraft.util.FormattedCharSequence
import net.minecraft.util.FormattedCharSink
import net.trilleo.hexium.Hexium
import net.trilleo.hexium.client.session.HexiumSession
import net.trilleo.hexium.core.judge.ClearLamp
import net.trilleo.hexium.core.judge.Judgement

/**
 * Combo, the last judgement and the score. Numbers go in reused buffers and fixed text is prepared
 * when a play starts, so nothing here allocates. Vanilla still makes a small object per text drawn.
 */
object PlayHud {
	private val JUDGEMENT_COLORS = intArrayOf(0xFFFFD84A.toInt(), 0xFFFF9A3C.toInt(), 0xFF6FD66F.toInt(), 0xFFA0A0A0.toInt())

	private const val TEXT_COLOR = -1
	private const val JUDGEMENT_SHOWN_MS = 500.0
	private const val COMBO_SCALE = 3f
	private const val COMBO_ABOVE = 60
	private const val JUDGEMENT_ABOVE = 26
	private const val DELTA_ABOVE = 14
	private const val MARGIN = 6
	private const val FINISHED_ABOVE = 30
	private const val LINE_HEIGHT = 12
	private const val ASCII = 128

	/** Text prepared once, with its width. */
	private class Prepared(font: Font, text: Component) {
		val sequence: FormattedCharSequence = text.visualOrderText
		val width = font.width(sequence)
	}

	/** A short run of characters that can be rewritten without allocating. */
	private class NumberText : FormattedCharSequence {
		private val chars = IntArray(CAPACITY)
		private var length = 0
		var width = 0
			private set

		fun set(value: Int) {
			clear()
			appendNumber(value)
		}

		fun setSignedMs(value: Int) {
			clear()
			append(if (value > 0) '+'.code else if (value < 0) '-'.code else ' '.code)
			appendNumber(if (value < 0) -value else value)
			append(' '.code)
			append('m'.code)
			append('s'.code)
		}

		fun clear() {
			length = 0
			width = 0
		}

		private fun appendNumber(value: Int) {
			var divisor = 1
			while (value / divisor >= 10) divisor *= 10
			while (divisor > 0) {
				append('0'.code + value / divisor % 10)
				divisor /= 10
			}
		}

		private fun append(char: Int) {
			chars[length++] = char
			width += charWidths[char]
		}

		override fun accept(output: FormattedCharSink): Boolean {
			for (i in 0 until length) {
				if (!output.accept(i, Style.EMPTY, chars[i])) return false
			}
			return true
		}

		private companion object {
			const val CAPACITY = 16
		}
	}

	private val charWidths = IntArray(ASCII)
	private val combo = NumberText()
	private val score = NumberText()
	private val delta = NumberText()

	private var judgementText = arrayOfNulls<Prepared>(0)
	private var lampText = arrayOfNulls<Prepared>(0)
	private var readyText: Prepared? = null
	private var finishedText: Prepared? = null

	private var session: PlaySession? = null
	private var seen = 0
	private var last: Judgement? = null
	private var lastAtMs = 0.0

	fun register() {
		HudElementRegistry.addLast(Hexium.id("play")) { graphics, _ ->
			val play = if (HexiumSession.active()) HexiumSession.play() else null
			if (play != null) draw(graphics, play) else session = null
		}
	}

	private fun prepare(font: Font) {
		for (i in 0 until ASCII) charWidths[i] = font.width(i.toChar().toString())
		judgementText = Array(Judgement.entries.size) {
			Prepared(font, Component.translatable("hexium.judgement.${Judgement.entries[it].name.lowercase()}"))
		}
		lampText = Array(ClearLamp.entries.size) {
			val lamp = ClearLamp.entries[it]
			if (lamp == ClearLamp.NONE) null else Prepared(font, Component.translatable("hexium.lamp.${lamp.name.lowercase()}"))
		}
		readyText = Prepared(font, Component.translatable("hexium.play.ready"))
		finishedText = Prepared(font, Component.translatable("hexium.play.finished"))
	}

	private fun draw(graphics: GuiGraphicsExtractor, play: PlaySession) {
		val font = Minecraft.getInstance().font
		if (session !== play) {
			session = play
			prepare(font)
			seen = 0
			last = null
			combo.set(0)
			score.set(0)
			delta.clear()
		}
		catchUp(play)
		val centre = graphics.guiWidth() / 2
		val middle = graphics.guiHeight() / 2
		val now = play.clock.songTimeMs

		if (now < 0.0 && play.state == PlaySession.State.PLAYING) {
			drawCentred(graphics, font, readyText, centre, middle - JUDGEMENT_ABOVE, TEXT_COLOR)
		}
		graphics.text(font, score, graphics.guiWidth() - score.width - MARGIN, MARGIN, TEXT_COLOR)

		if (play.judge.combo > 1) {
			graphics.pose().pushMatrix()
			graphics.pose().translate(centre.toFloat(), (middle - COMBO_ABOVE).toFloat())
			graphics.pose().scale(COMBO_SCALE, COMBO_SCALE)
			graphics.text(font, combo, -combo.width / 2, 0, TEXT_COLOR)
			graphics.pose().popMatrix()
		}
		val judgement = last
		if (judgement != null && now - lastAtMs < JUDGEMENT_SHOWN_MS) {
			drawCentred(graphics, font, judgementText[judgement.ordinal], centre, middle - JUDGEMENT_ABOVE, JUDGEMENT_COLORS[judgement.ordinal])
			graphics.text(font, delta, centre - delta.width / 2, middle - DELTA_ABOVE, TEXT_COLOR)
		}
		if (play.state == PlaySession.State.FINISHED) {
			drawCentred(graphics, font, finishedText, centre, middle + FINISHED_ABOVE, TEXT_COLOR)
			drawCentred(graphics, font, lampText[play.judge.clearLamp.ordinal], centre, middle + FINISHED_ABOVE + LINE_HEIGHT, TEXT_COLOR)
		}
	}

	private fun drawCentred(graphics: GuiGraphicsExtractor, font: Font, text: Prepared?, centre: Int, y: Int, color: Int) {
		if (text != null) graphics.text(font, text.sequence, centre - text.width / 2, y, color)
	}

	private fun catchUp(play: PlaySession) {
		val judge = play.judge
		val count = judge.judgedCount
		if (seen == count) return
		val newest = count - 1
		last = judge.judgement(newest)
		lastAtMs = play.clock.songTimeMs
		// A tick's delta says how long ago the key was held, which would read as a mistimed press.
		var i = newest
		while (i >= seen && !judge.judgedIsPress(i)) i--
		if (i >= seen) {
			val ms = judge.judgedDeltaMs(i)
			if (ms.isNaN()) delta.clear() else delta.setSignedMs(Math.round(ms).toInt())
		} else if (judge.judgement(newest) == Judgement.MISS) {
			delta.clear()
		}
		combo.set(judge.combo)
		score.set(judge.score)
		seen = count
	}
}
