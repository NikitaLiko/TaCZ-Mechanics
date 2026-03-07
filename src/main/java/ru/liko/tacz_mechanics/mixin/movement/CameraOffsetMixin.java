package ru.liko.tacz_mechanics.mixin.movement;

import net.minecraft.client.Camera;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ru.liko.tacz_mechanics.Config;
import ru.liko.tacz_mechanics.movement.MovementStateManager;
import ru.liko.tacz_mechanics.movement.PlayerState;
import ru.liko.tacz_mechanics.movement.client.MovementClientHandler;

/**
 * Mixin to offset camera position when player is leaning.
 */
@Mixin(Camera.class)
public abstract class CameraOffsetMixin {
    
    @Shadow private Vec3 position;
    @Shadow protected abstract void setPosition(Vec3 pos);
    @Shadow protected abstract void move(float distanceOffset, float verticalOffset, float horizontalOffset);
    
    @Inject(method = "setup", at = @At("TAIL"))
    private void applyCameraOffset(BlockGetter level, Entity entity, boolean detached, 
                                    boolean thirdPersonReverse, float partialTick, CallbackInfo ci) {
        if (!Config.Movement.enabled) return;
        if (!(entity instanceof Player)) return;
        if (detached) return;
        
        double offsetX = 0, offsetY = 0, offsetZ = 0;
        
        // Apply vertical offset for sitting/crawling
        if (entity instanceof Player player) {
            PlayerState state = MovementStateManager.get(player.getUUID());
            if (state != null) {
                if (state.isCrawling()) offsetY = Config.Movement.crawlYOffset;
                else if (state.isSitting()) offsetY = Config.Movement.sitYOffset;
            }
        }
        
        // Apply horizontal offset for leaning
        float probeOffset = MovementClientHandler.cameraProbeOffset;
        if (probeOffset != 0) {
            float yaw = entity.getYRot();
            double radians = Math.toRadians(yaw);
            offsetX = -probeOffset * 0.6 * Math.cos(radians);
            offsetZ = -probeOffset * 0.6 * Math.sin(radians);
        }
        
        if (offsetX != 0 || offsetY != 0 || offsetZ != 0) {
            setPosition(position.add(offsetX, offsetY, offsetZ));
        }
    }
}
