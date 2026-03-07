package ru.liko.tacz_mechanics.mixin;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ru.liko.tacz_mechanics.client.suppression.SuppressionHandler;
import ru.liko.tacz_mechanics.client.suppression.SuppressionRenderer;

/**
 * Injects suppression post-processing at the end of GameRenderer.renderLevel,
 * AFTER both the world and first-person hands have been rendered, but BEFORE the GUI.
 */
@Mixin(GameRenderer.class)
public class SuppressionGameRendererMixin {
    @Inject(method = "renderLevel", at = @At("RETURN"))
    private void taczMechanics$afterRenderLevel(DeltaTracker deltaTracker, CallbackInfo ci) {
        if (!SuppressionHandler.isActive()) return;

        if (!SuppressionRenderer.isReady()) {
            SuppressionRenderer.init();
        }
        SuppressionRenderer.render(deltaTracker.getGameTimeDeltaPartialTick(false));
    }
}
