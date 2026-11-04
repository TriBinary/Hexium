package net.trilleo.hexium.client.mixin;

import net.minecraft.client.MouseHandler;
import net.trilleo.hexium.client.session.HexiumSession;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** The wheel changes the hotbar slot directly. Screens still get it. */
@Mixin(MouseHandler.class)
abstract class MouseHandlerMixin {
	@Inject(method = "onScroll", at = @At("HEAD"), cancellable = true)
	private void hexium$noHotbarScroll(long handle, double xoffset, double yoffset, CallbackInfo ci) {
		if (!HexiumSession.active()) return;
		if (HexiumSession.noScreenOpen()) ci.cancel();
	}
}
