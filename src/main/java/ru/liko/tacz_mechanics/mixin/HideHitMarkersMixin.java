package ru.liko.tacz_mechanics.mixin;

import com.mojang.blaze3d.platform.Window;
import com.tacz.guns.client.event.RenderCrosshairEvent;
import net.minecraft.client.gui.GuiGraphics;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ru.liko.tacz_mechanics.Config;

/**
 * Mixin to hide hit markers based on config setting.
 * Ported from TaCZTweaks.
 */
@Mixin(value = RenderCrosshairEvent.class, remap = false)
public abstract class HideHitMarkersMixin {
    
    @Inject(method = "renderHitMarker", at = @At("HEAD"), cancellable = true)
    private static void tacz_mechanics$hideHitMarker(GuiGraphics graphics, Window window, CallbackInfo ci) {
        if (Config.Tweaks.hideHitMarkers) {
            ci.cancel();
        }
    }
}
