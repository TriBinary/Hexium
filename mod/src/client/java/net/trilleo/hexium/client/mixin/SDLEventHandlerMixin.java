package net.trilleo.hexium.client.mixin;

import com.mojang.blaze3d.platform.SDLEventHandler;
import net.trilleo.hexium.client.input.LaneInput;
import net.trilleo.hexium.client.input.SkyInput;
import net.trilleo.hexium.client.session.HexiumSession;
import org.lwjgl.sdl.SDL_Event;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** The only place that still has an input event's own timestamp. */
@Mixin(SDLEventHandler.class)
abstract class SDLEventHandlerMixin {
	@Inject(method = "handleKeyEvent", at = @At("HEAD"), cancellable = true)
	private void hexium$laneKeys(SDL_Event event, CallbackInfo ci) {
		if (!HexiumSession.active()) return;
		if (LaneInput.onKeyEvent(event.address())) ci.cancel();
	}

	@Inject(method = "handleMouseButtonEvent", at = @At("HEAD"), cancellable = true)
	private void hexium$skyButtons(SDL_Event event, CallbackInfo ci) {
		if (!HexiumSession.active()) return;
		if (SkyInput.onMouseButtonEvent(event.address())) ci.cancel();
	}
}
