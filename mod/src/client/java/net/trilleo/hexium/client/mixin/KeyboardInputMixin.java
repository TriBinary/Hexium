package net.trilleo.hexium.client.mixin;

import net.minecraft.client.player.ClientInput;
import net.minecraft.client.player.KeyboardInput;
import net.minecraft.world.entity.player.Input;
import net.minecraft.world.phys.Vec2;
import net.trilleo.hexium.client.session.HexiumSession;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** No moving, jumping or sneaking on the stage, whatever keys are down. */
@Mixin(KeyboardInput.class)
abstract class KeyboardInputMixin extends ClientInput {
	@Inject(method = "tick", at = @At("RETURN"))
	private void hexium$holdStill(CallbackInfo ci) {
		if (!HexiumSession.active()) return;
		this.keyPresses = Input.EMPTY;
		this.moveVector = Vec2.ZERO;
	}
}
