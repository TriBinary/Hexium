package net.trilleo.hexium.client.mixin;

import net.minecraft.client.tutorial.Tutorial;
import net.trilleo.hexium.client.session.HexiumSession;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Outside survival, vanilla's tutorial ends itself for good, and its "move" hint could never be done here. */
@Mixin(Tutorial.class)
abstract class TutorialMixin {
	@Inject(method = "start", at = @At("HEAD"), cancellable = true)
	private void hexium$noTutorial(CallbackInfo ci) {
		if (!HexiumSession.active()) return;
		ci.cancel();
	}
}
