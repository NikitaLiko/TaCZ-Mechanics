package ru.liko.tacz_mechanics.mixin.movement;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import ru.liko.tacz_mechanics.Config;
import ru.liko.tacz_mechanics.movement.MovementStateManager;
import ru.liko.tacz_mechanics.movement.PlayerState;

/**
 * Mixin to modify player eye position based on movement state (leaning).
 * This affects raycasting/shooting calculations.
 */
@Mixin(Entity.class)
public abstract class PlayerEyePositionMixin {
    
    @Inject(method = "getEyePosition(F)Lnet/minecraft/world/phys/Vec3;", at = @At("RETURN"), cancellable = true)
    private void modifyEyePosition(float partialTicks, CallbackInfoReturnable<Vec3> cir) {
        Entity self = (Entity) (Object) this;
        if (!(self instanceof Player player)) return;
        if (!Config.Movement.enabled) return;
        
        Vec3 original = cir.getReturnValue();
        double offsetX = 0, offsetY = 0, offsetZ = 0;
        
        PlayerState state = MovementStateManager.get(player.getUUID());
        if (state == null) return;
        
        // Apply vertical offset for sitting/crawling hitbox
        float yOffset = getYOffsetForState(state);
        if (yOffset != 0) {
            offsetY += yOffset;
        }
        
        // Apply horizontal offset for leaning
        float probeOffset = state.getProbeOffset();
        if (probeOffset != 0) {
            float yaw = player.getYRot();
            double radians = Math.toRadians(yaw);
            offsetX = -probeOffset * 0.6 * Math.cos(radians);
            offsetZ = -probeOffset * 0.6 * Math.sin(radians);
        }
        
        if (offsetX != 0 || offsetY != 0 || offsetZ != 0) {
            cir.setReturnValue(original.add(offsetX, offsetY, offsetZ));
        }
    }
    
    @Inject(method = "getEyePosition()Lnet/minecraft/world/phys/Vec3;", at = @At("RETURN"), cancellable = true)
    private void modifyEyePositionNoPartial(CallbackInfoReturnable<Vec3> cir) {
        Entity self = (Entity) (Object) this;
        if (!(self instanceof Player player)) return;
        if (!Config.Movement.enabled) return;
        
        Vec3 original = cir.getReturnValue();
        double offsetX = 0, offsetY = 0, offsetZ = 0;
        
        PlayerState state = MovementStateManager.get(player.getUUID());
        if (state == null) return;
        
        // Apply vertical offset for sitting/crawling hitbox
        float yOffset = getYOffsetForState(state);
        if (yOffset != 0) {
            offsetY += yOffset;
        }
        
        // Apply horizontal offset for leaning
        float probeOffset = state.getProbeOffset();
        if (probeOffset != 0) {
            float yaw = player.getYRot();
            double radians = Math.toRadians(yaw);
            offsetX = -probeOffset * 0.6 * Math.cos(radians);
            offsetZ = -probeOffset * 0.6 * Math.sin(radians);
        }
        
        if (offsetX != 0 || offsetY != 0 || offsetZ != 0) {
            cir.setReturnValue(original.add(offsetX, offsetY, offsetZ));
        }
    }
    
    private static float getYOffsetForState(PlayerState state) {
        if (state.isCrawling()) return Config.Movement.crawlYOffset;
        if (state.isSitting()) return Config.Movement.sitYOffset;
        return 0;
    }
}
