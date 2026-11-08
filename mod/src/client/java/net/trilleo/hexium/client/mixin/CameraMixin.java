package net.trilleo.hexium.client.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import net.minecraft.client.Camera;
import net.trilleo.hexium.client.play.Comfort;
import net.trilleo.hexium.client.session.HexiumSession;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** The play's own field of view, in place of the player's FOV setting and any FOV effects. */
@Mixin(Camera.class)
abstract class CameraMixin {
	@ModifyReturnValue(method = "calculateFov", at = @At("RETURN"))
	private float hexium$playFov(float fov) {
		if (!HexiumSession.active()) return fov;
		return Comfort.fov(fov);
	}
}
