package ru.liko.tacz_mechanics.mixin.movement;

import com.tacz.guns.entity.EntityKineticBullet;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ru.liko.tacz_mechanics.Config;
import ru.liko.tacz_mechanics.movement.MovementStateManager;
import ru.liko.tacz_mechanics.movement.PlayerState;

/**
 * Mixin to offset bullet spawn position when player is leaning.
 */
@Mixin(value = EntityKineticBullet.class, remap = false)
public abstract class BulletLeanOffsetMixin {

    @Inject(method = "<init>(Lnet/minecraft/world/level/Level;Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/resources/ResourceLocation;Lnet/minecraft/resources/ResourceLocation;Lnet/minecraft/resources/ResourceLocation;ZLcom/tacz/guns/resource/pojo/data/gun/GunData;Lcom/tacz/guns/resource/pojo/data/gun/BulletData;)V",
            at = @At("TAIL"))
    private void applyLeanOffset(net.minecraft.world.level.Level worldIn, LivingEntity throwerIn,
                                  net.minecraft.world.item.ItemStack gunItem,
                                  net.minecraft.resources.ResourceLocation ammoId,
                                  net.minecraft.resources.ResourceLocation gunId,
                                  net.minecraft.resources.ResourceLocation gunDisplayId,
                                  boolean isTracerAmmo,
                                  com.tacz.guns.resource.pojo.data.gun.GunData gunData,
                                  com.tacz.guns.resource.pojo.data.gun.BulletData bulletData,
                                  CallbackInfo ci) {
        if (!Config.Movement.enabled) return;
        if (!(throwerIn instanceof Player player)) return;

        PlayerState state = MovementStateManager.get(player.getUUID());
        if (state == null) return;

        float probeOffset = state.getProbeOffset();
        if (probeOffset == 0) return;

        EntityKineticBullet bullet = (EntityKineticBullet) (Object) this;
        Vec3 currentPos = bullet.position();

        // Calculate lateral offset based on player's yaw
        float yaw = player.getYRot();
        float radYaw = yaw * Mth.DEG_TO_RAD;
        
        // Use same formula as camera for consistency
        double offsetX = -Mth.cos(radYaw) * probeOffset * 0.6;
        double offsetZ = -Mth.sin(radYaw) * probeOffset * 0.6;

        bullet.setPos(currentPos.x + offsetX, currentPos.y, currentPos.z + offsetZ);
    }
}
