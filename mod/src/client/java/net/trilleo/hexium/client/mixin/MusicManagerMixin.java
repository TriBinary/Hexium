package net.trilleo.hexium.client.mixin;

import net.minecraft.client.sounds.MusicManager;
import net.trilleo.hexium.client.session.HexiumSession;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Vanilla music must not start over a song. */
@Mixin(MusicManager.class)
abstract class MusicManagerMixin {
	@Inject(method = "tick", at = @At("HEAD"), cancellable = true)
	private void hexium$holdMusic(CallbackInfo ci) {
		if (!HexiumSession.active()) return;
		ci.cancel();
	}
}
