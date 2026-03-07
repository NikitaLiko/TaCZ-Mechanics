package ru.liko.tacz_mechanics.mixin;

import com.mojang.blaze3d.platform.Window;
import com.tacz.guns.client.event.RenderCrosshairEvent;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ru.liko.tacz_mechanics.Config;
import ru.liko.tacz_mechanics.client.freeaim.FreeAimHandler;

/**
 * Mixin to offset crosshair rendering based on free aim.
 * The crosshair moves to show where the gun is actually pointing.
 */
@Mixin(value = RenderCrosshairEvent.class, remap = false)
public class FreeAimCrosshairMixin {
    
    // Track if we pushed the pose stack
    private static final ThreadLocal<Boolean> pushedPose = ThreadLocal.withInitial(() -> false);
    
    /**
     * Inject at the start of renderCrosshair to translate the rendering matrix.
     */
    @Inject(
        method = "renderCrosshair",
        at = @At("HEAD")
    )
    private static void onRenderCrosshairStart(GuiGraphics graphics, Window window, CallbackInfo ci) {
        pushedPose.set(false);
        
        if (!Config.FreeAim.enabled || Config.FreeAim.disableCrosshairMovement) {
            return;
        }
        
        FreeAimHandler handler = FreeAimHandler.getInstance();
        float partialTicks = Minecraft.getInstance().getTimer().getGameTimeDeltaPartialTick(false);
        
        // Get crosshair offset from free aim
        float offsetX = handler.getCrosshairX(partialTicks);
        float offsetY = handler.getCrosshairY(partialTicks);
        
        if (Math.abs(offsetX) < 0.01f && Math.abs(offsetY) < 0.01f) {
            return;
        }
        
        // Push and translate the pose stack
        graphics.pose().pushPose();
        graphics.pose().translate(offsetX, offsetY, 0);
        pushedPose.set(true);
    }
    
    /**
     * Inject at every RETURN to restore the rendering matrix.
     */
    @Inject(
        method = "renderCrosshair",
        at = @At("RETURN")
    )
    private static void onRenderCrosshairEnd(GuiGraphics graphics, Window window, CallbackInfo ci) {
        if (pushedPose.get()) {
            graphics.pose().popPose();
            pushedPose.set(false);
        }
    }
}
