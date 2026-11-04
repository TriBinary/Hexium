package net.trilleo.hexium.client

import net.fabricmc.api.ClientModInitializer
import net.trilleo.hexium.Hexium
import net.trilleo.hexium.client.play.PlayHud
import net.trilleo.hexium.client.play.TrackRenderer
import net.trilleo.hexium.client.screen.TitleScreenButton
import net.trilleo.hexium.client.session.StageHud
import net.trilleo.hexium.client.session.StageWorld
import org.slf4j.LoggerFactory

object HexiumClient : ClientModInitializer {
	private val LOGGER = LoggerFactory.getLogger(Hexium.MOD_ID)

	override fun onInitializeClient() {
		TitleScreenButton.register()
		StageWorld.registerEvents()
		StageHud.register()
		PlayHud.register()
		TrackRenderer.register()
		LOGGER.info("Hexium loaded")
	}
}
