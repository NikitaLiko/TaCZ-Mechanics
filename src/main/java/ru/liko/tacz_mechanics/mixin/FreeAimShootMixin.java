package ru.liko.tacz_mechanics.mixin;

import com.tacz.guns.item.ModernKineticGunScriptAPI;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import ru.liko.tacz_mechanics.Config;
import ru.liko.tacz_mechanics.server.FreeAimServerHandler;

import java.util.function.Supplier;

/**
 * Mixin to modify bullet shooting direction based on free aim offset.
 * Intercepts the pitch and yaw values used when spawning bullets.
 * Uses server-side synced free aim data.
 */
@Mixin(value = ModernKineticGunScriptAPI.class, remap = false)
public abstract class FreeAimShootMixin {
    
    @Shadow
    private Supplier<Float> pitchSupplier;
    
    @Shadow
    private Supplier<Float> yawSupplier;
    
    @Shadow
    private LivingEntity shooter;
    
    /**
     * Modify the pitch value used for shooting to include free aim offset.
     * Targets the local variable 'pitch' in shootOnce method.
     */
    @ModifyVariable(
        method = "shootOnce",
        at = @At(value = "STORE"),
        ordinal = 0,
        name = "pitch"
    )
    private float modifyShootPitch(float originalPitch) {
        if (!Config.FreeAim.enabled) {
            return originalPitch;
        }
        
        // Apply server-side free aim offset
        if (shooter instanceof ServerPlayer serverPlayer) {
            return FreeAimServerHandler.getAdjustedPitch(serverPlayer, originalPitch);
        }
        
        return originalPitch;
    }
    
    /**
     * Modify the yaw value used for shooting to include free aim offset.
     * Targets the local variable 'yaw' in shootOnce method.
     */
    @ModifyVariable(
        method = "shootOnce",
        at = @At(value = "STORE"),
        ordinal = 0,
        name = "yaw"
    )
    private float modifyShootYaw(float originalYaw) {
        if (!Config.FreeAim.enabled) {
            return originalYaw;
        }
        
        // Apply server-side free aim offset
        if (shooter instanceof ServerPlayer serverPlayer) {
            return FreeAimServerHandler.getAdjustedYaw(serverPlayer, originalYaw);
        }
        
        return originalYaw;
    }
}
