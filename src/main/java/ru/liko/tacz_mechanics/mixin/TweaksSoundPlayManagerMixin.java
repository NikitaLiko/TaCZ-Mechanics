package ru.liko.tacz_mechanics.mixin;

import com.tacz.guns.client.sound.SoundPlayManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ru.liko.tacz_mechanics.Config;

/**
 * Mixin to suppress hit sounds based on config settings.
 * Ported from TaCZTweaks.
 */
@Mixin(value = SoundPlayManager.class, remap = false)
public abstract class TweaksSoundPlayManagerMixin {
    
    @Inject(method = "playHeadHitSound", at = @At("HEAD"), cancellable = true)
    private static void tacz_mechanics$suppressHeadHitSound(CallbackInfo ci) {
        if (Config.Tweaks.suppressHeadHitSounds) {
            ci.cancel();
        }
    }
    
    @Inject(method = "playFleshHitSound", at = @At("HEAD"), cancellable = true)
    private static void tacz_mechanics$suppressFleshHitSound(CallbackInfo ci) {
        if (Config.Tweaks.suppressFleshHitSounds) {
            ci.cancel();
        }
    }
    
    @Inject(method = "playKillSound", at = @At("HEAD"), cancellable = true)
    private static void tacz_mechanics$suppressKillSound(CallbackInfo ci) {
        if (Config.Tweaks.suppressKillSounds) {
            ci.cancel();
        }
    }
}
