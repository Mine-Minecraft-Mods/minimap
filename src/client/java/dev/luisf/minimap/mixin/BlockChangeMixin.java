package dev.luisf.minimap.mixin;

import dev.luisf.minimap.MinimapClient;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Keeps the top-down colour cache in sync with block changes on the client.
 *
 * <p>Single-block and section-update packets both go through
 * {@code setServerVerifiedBlockState}; client-side predictions (breaking or placing before the
 * server answers) go through {@code ClientLevel#setBlock}. Both are hooked.
 */
@Mixin(ClientLevel.class)
abstract class BlockChangeMixin {
	@Inject(method = "setServerVerifiedBlockState", at = @At("RETURN"))
	private void minimap$onServerBlockState(BlockPos pos, BlockState state, int flags, CallbackInfo ci) {
		MinimapClient.onBlockChanged((ClientLevel) (Object) this, pos);
	}

	@Inject(method = "setBlock(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;II)Z", at = @At("RETURN"))
	private void minimap$onSetBlock(BlockPos pos, BlockState state, int flags, int updateLimit, CallbackInfoReturnable<Boolean> cir) {
		MinimapClient.onBlockChanged((ClientLevel) (Object) this, pos);
	}
}
