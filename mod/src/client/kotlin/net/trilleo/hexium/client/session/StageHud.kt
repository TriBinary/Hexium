package net.trilleo.hexium.client.session

import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements

/** Hides what doesn't apply on the stage. Everything else in the HUD stays vanilla. */
object StageHud {
	fun register() {
		HudElementRegistry.replaceElement(VanillaHudElements.FOOD_BAR) { vanilla ->
			HudElement { graphics, deltaTracker ->
				if (!HexiumSession.active()) vanilla.extractRenderState(graphics, deltaTracker)
			}
		}
	}
}
