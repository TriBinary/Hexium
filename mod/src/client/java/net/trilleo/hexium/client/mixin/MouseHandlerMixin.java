package net.trilleo.hexium.client.mixin;

import net.minecraft.client.MouseHandler;
import net.trilleo.hexium.client.play.Comfort;
import net.trilleo.hexium.client.session.HexiumSession;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MouseHandler.class)
abstract class MouseHandlerMixin {
	/** The camera has just turned with this frame's mouse movement, and nothing has drawn it yet. */
	@Inject(method = "handleAccumulatedMovement", at = @At("TAIL"))
	private void hexium$afterMouse(CallbackInfo ci) {
		if (!HexiumSession.active()) return;
		HexiumSession.afterMouse();
	}

	/** The play's own mouse sensitivity, on top of vanilla's, so menus feel the same. */
	@ModifyArg(method = "turnPlayer", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/LocalPlayer;turn(DD)V"), index = 0)
	private double hexium$turnX(double xo) {
		if (!HexiumSession.active()) return xo;
		return Comfort.scaleTurn(xo);
	}

	@ModifyArg(method = "turnPlayer", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/LocalPlayer;turn(DD)V"), index = 1)
	private double hexium$turnY(double yo) {
		if (!HexiumSession.active()) return yo;
		return Comfort.scaleTurn(yo);
	}

	/** The wheel changes the hotbar slot directly. Screens still get it. */
	@Inject(method = "onScroll", at = @At("HEAD"), cancellable = true)
	private void hexium$noHotbarScroll(long handle, double xoffset, double yoffset, CallbackInfo ci) {
		if (!HexiumSession.active()) return;
		if (HexiumSession.noScreenOpen()) ci.cancel();
	}
}
