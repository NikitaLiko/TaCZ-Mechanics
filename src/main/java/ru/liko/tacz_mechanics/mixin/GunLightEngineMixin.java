package ru.liko.tacz_mechanics.mixin;

import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.lighting.BlockLightEngine;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import ru.liko.tacz_mechanics.client.light.GunLightMap;

@Mixin(BlockLightEngine.class)
public abstract class GunLightEngineMixin {

    @Inject(method = "getEmission", at = @At("HEAD"), cancellable = true)
    private void taczMechanics$gunLight(long packedPos, BlockState state, CallbackInfoReturnable<Integer> cir) {
        int dyn = GunLightMap.getLight(packedPos);
        if (dyn > state.getLightEmission()) {
            cir.setReturnValue(dyn);
        }
    }
}
