package net.trilleo.hexium.client.screen

import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.components.StringWidget
import net.minecraft.client.gui.components.Tooltip
import net.minecraft.client.gui.layouts.HeaderAndFooterLayout
import net.minecraft.client.gui.layouts.LinearLayout
import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.CommonComponents
import net.minecraft.network.chat.Component
import net.trilleo.hexium.client.chart.ChartLibrary
import net.trilleo.hexium.client.chart.ChartPack
import net.trilleo.hexium.client.session.HexiumSession
import net.trilleo.hexium.client.session.StageWorld
import net.trilleo.hexium.core.chart.DifficultyChart

/** Hexium's menu, opened from the title screen. A plain list of charts until song select exists. */
class HexiumMenuScreen(private val lastScreen: Screen) : Screen(TITLE) {
	private val packs: List<ChartPack> = ChartLibrary.scan()
	private var page = 0

	override fun init() {
		// Back here means any session is over, including a stage that failed to load.
		HexiumSession.end()
		val layout = HeaderAndFooterLayout(this)
		layout.addTitleHeader(TITLE, font)

		val perPage = ((layout.contentHeight - CONTENT_TOP_MARGIN + ROW_SPACING) / ROW_HEIGHT).coerceAtLeast(1)
		val pages = ((packs.size + perPage - 1) / perPage).coerceAtLeast(1)
		page = page.coerceIn(0, pages - 1)

		val content = layout.addToContents(LinearLayout.vertical().spacing(ROW_SPACING))
		content.defaultCellSetting().alignHorizontallyCenter()
		if (packs.isEmpty()) content.addChild(StringWidget(Component.translatable("hexium.menu.no_charts"), font))
		for (i in page * perPage until minOf((page + 1) * perPage, packs.size)) {
			content.addChild(row(packs[i]))
		}

		val footer = layout.addToFooter(LinearLayout.horizontal().spacing(ROW_SPACING))
		val previous = footer.addChild(pageButton("<", -1))
		val audioTest = footer.addChild(
			Button.builder(Component.translatable("hexium.audio_test.title")) { openAudioTest() }.width(FOOTER_BUTTON_WIDTH).build(),
		)
		audioTest.active = audioTestPack() != null
		footer.addChild(Button.builder(CommonComponents.GUI_BACK) { onClose() }.width(FOOTER_BUTTON_WIDTH).build())
		val next = footer.addChild(pageButton(">", 1))
		previous.active = page > 0
		next.active = page < pages - 1

		layout.arrangeElements()
		layout.visitWidgets { addRenderableWidget(it) }
	}

	private fun audioTestPack(): ChartPack? = packs.firstOrNull { it.errors.isEmpty() && it.charts.isNotEmpty() }

	private fun openAudioTest() {
		val pack = audioTestPack() ?: return
		val screen = AudioTestScreen(this, pack)
		HexiumSession.start()
		minecraft.gui.setScreen(screen)
	}

	private fun pageButton(label: String, step: Int): Button =
		Button.builder(Component.literal(label)) {
			page += step
			rebuildWidgets()
		}.width(PAGE_BUTTON_WIDTH).build()

	private fun row(pack: ChartPack): LinearLayout {
		val row = LinearLayout.horizontal().spacing(ROW_SPACING)
		row.defaultCellSetting().alignVerticallyMiddle()
		val title = pack.meta?.title ?: pack.folder.fileName.toString()
		row.addChild(StringWidget(Component.literal(title), font).setMaxWidth(TITLE_WIDTH))
		if (pack.errors.isNotEmpty()) {
			val broken = Button.builder(Component.translatable("hexium.menu.broken")) {}
				.width(DIFFICULTY_WIDTH)
				.tooltip(Tooltip.create(Component.literal(pack.errors.first())))
				.build()
			broken.active = false
			row.addChild(broken)
			return row
		}
		for (chart in pack.charts) row.addChild(difficultyButton(chart))
		return row
	}

	private fun difficultyButton(chart: DifficultyChart): Button {
		val difficulty = Component.translatable("hexium.difficulty.${chart.difficulty.name.lowercase()}")
		val button = Button.builder(difficulty) { StageWorld.enter(minecraft, this) }.width(DIFFICULTY_WIDTH)
		if (!chart.isGroundOnly) button.tooltip(Tooltip.create(Component.translatable("hexium.menu.sky_notes")))
		return button.build().also { it.active = chart.isGroundOnly }
	}

	override fun onClose() {
		minecraft.gui.setScreen(lastScreen)
	}

	private companion object {
		val TITLE: Component = Component.translatable("hexium.menu.title")
		/** How far HeaderAndFooterLayout keeps its contents below the header. */
		const val CONTENT_TOP_MARGIN = 30
		const val ROW_HEIGHT = 24
		const val ROW_SPACING = 4
		const val TITLE_WIDTH = 140
		const val DIFFICULTY_WIDTH = 64
		const val FOOTER_BUTTON_WIDTH = 100
		const val PAGE_BUTTON_WIDTH = 20
	}
}
