package net.trilleo.hexium.client.mixin;

import net.minecraft.client.Minecraft;
import net.trilleo.hexium.client.session.HexiumSession;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Minecraft.class)
abstract class MinecraftMixin {
	/** No action keys on the stage: attacking, items, hotbar, inventory, chat, F1, F5 and the rest of this method. */
	@Inject(method = "handleKeybinds", at = @At("HEAD"), cancellable = true)
	private void hexium$noActions(CallbackInfo ci) {
		if (!HexiumSession.active()) return;
		ci.cancel();
	}

	/** Vanilla's pause menu could leave the stage without going through Hexium. */
	@Inject(method = "pauseGame", at = @At("HEAD"), cancellable = true)
	private void hexium$ownPauseMenu(boolean suppressPauseMenu, CallbackInfo ci) {
		if (!HexiumSession.active()) return;
		HexiumSession.requestPause();
		ci.cancel();
	}
}
