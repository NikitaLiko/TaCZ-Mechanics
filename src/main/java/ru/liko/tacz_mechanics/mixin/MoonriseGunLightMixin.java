package ru.liko.tacz_mechanics.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import ru.liko.tacz_mechanics.client.light.GunLightMap;

/**
 * Moonrise replaces the vanilla light engine with Starlight, which never calls
 * {@code BlockLightEngine.getEmission} — instead all its paths read emission
 * through this single PlatformHooks method. Applied only when Moonrise is
 * present (see TaczMechanicsMixinPlugin).
 */
@Mixin(targets = "ca.spottedleaf.moonrise.neoforge.NeoForgeHooks", remap = false)
public abstract class MoonriseGunLightMixin {

    @Inject(method = "getLightEmission", at = @At("RETURN"), cancellable = true)
    private void taczMechanics$gunLight(BlockState state, BlockGetter level, BlockPos pos, CallbackInfoReturnable<Integer> cir) {
        int dyn = GunLightMap.getLight(pos.asLong());
        if (dyn > cir.getReturnValueI()) {
            cir.setReturnValue(dyn);
        }
    }
}
