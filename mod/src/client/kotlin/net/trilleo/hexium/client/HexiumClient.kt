package net.trilleo.hexium.client

import net.fabricmc.api.ClientModInitializer
import net.trilleo.hexium.Hexium
import net.trilleo.hexium.client.screen.TitleScreenButton
import org.slf4j.LoggerFactory

object HexiumClient : ClientModInitializer {
	private val LOGGER = LoggerFactory.getLogger(Hexium.MOD_ID)

	override fun onInitializeClient() {
		TitleScreenButton.register()
		LOGGER.info("Hexium loaded")
	}
}
