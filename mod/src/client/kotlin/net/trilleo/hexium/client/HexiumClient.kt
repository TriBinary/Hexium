package net.trilleo.hexium.client

import net.fabricmc.api.ClientModInitializer
import net.trilleo.hexium.Hexium
import org.slf4j.LoggerFactory

object HexiumClient : ClientModInitializer {
	private val LOGGER = LoggerFactory.getLogger(Hexium.MOD_ID)

	override fun onInitializeClient() {
		LOGGER.info("Hexium loaded")
	}
}
