package net.trilleo.hexium.client.mixin;

import com.mojang.blaze3d.audio.Library;
import net.trilleo.hexium.client.audio.SongStream;
import net.trilleo.hexium.client.session.HexiumSession;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** The song's source dies with this context, so the stream must stop using it first. */
@Mixin(Library.class)
abstract class SoundLibraryMixin {
	@Inject(method = "cleanup", at = @At("HEAD"))
	private void hexium$songSourceEnds(CallbackInfo ci) {
		if (!HexiumSession.active()) return;
		SongStream.onContextLost();
	}
}
