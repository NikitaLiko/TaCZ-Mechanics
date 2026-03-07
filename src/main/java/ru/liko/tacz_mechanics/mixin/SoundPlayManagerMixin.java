package ru.liko.tacz_mechanics.mixin;

import com.mojang.logging.LogUtils;
import com.tacz.guns.client.sound.GunSoundInstance;
import com.tacz.guns.client.sound.SoundPlayManager;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import ru.liko.tacz_mechanics.Config;
import ru.liko.tacz_mechanics.client.sound.DistantFireHandler;

/**
 * Mixin to intercept gun sounds and apply distance-based filtering.
 */
@Mixin(value = SoundPlayManager.class, remap = false)
public class SoundPlayManagerMixin {
    private static final Logger LOGGER = LogUtils.getLogger();
    
    @Inject(
        method = "playClientSound(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/resources/ResourceLocation;FFIZ)Lcom/tacz/guns/client/sound/GunSoundInstance;",
        at = @At("HEAD"),
        cancellable = true
    )
    private static void tacz_mechanics$applyDistantFireFilter(
            Entity entity, @Nullable ResourceLocation name, float volume, float pitch, int distance, boolean mono,
            CallbackInfoReturnable<GunSoundInstance> cir) {
        
        if (Config.debug) {
            LOGGER.debug("[DistantFire] SoundPlayManagerMixin triggered! Sound: {}", name);
        }
        
        // Process all gun sounds (don't filter by name - gun packs use various naming)
        if (name == null) return;
        
        // Calculate distance from player
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        
        Vec3 playerPos = mc.player.position();
        Vec3 soundPos = entity.position();
        double distanceToSound = playerPos.distanceTo(soundPos);
        
        if (Config.debug) {
            LOGGER.debug("[DistantFire] Processing fire sound: {} at distance: {}", name, distanceToSound);
        }
        
        // Let DistantFireHandler process this
        GunSoundInstance result = DistantFireHandler.handleGunSound(
            entity, name, volume, pitch, distance, mono, distanceToSound
        );
        
        if (result != null) {
            if (Config.debug) {
                LOGGER.debug("[DistantFire] Applied muffle filter, returning custom sound");
            }
            cir.setReturnValue(result);
        } else if (Config.debug) {
            LOGGER.debug("[DistantFire] No muffle needed (close range), using default sound");
        }
    }
}
