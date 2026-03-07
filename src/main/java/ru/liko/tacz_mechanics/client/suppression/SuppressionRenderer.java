package ru.liko.tacz_mechanics.client.suppression;

import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.PostChain;
import net.minecraft.client.renderer.PostPass;
import net.minecraft.resources.ResourceLocation;
import org.slf4j.Logger;
import ru.liko.tacz_mechanics.Config;
import ru.liko.tacz_mechanics.TaczMechanics;
import ru.liko.tacz_mechanics.mixin.PostChainAccessor;

import java.util.List;

/**
 * Loads and applies the suppression PostChain shader effect.
 * Manages the lifecycle of the PostChain, uniform updates, and window resize handling.
 */
public class SuppressionRenderer {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final ResourceLocation SHADER_LOCATION =
        ResourceLocation.fromNamespaceAndPath(TaczMechanics.MODID, "shaders/post/suppression.json");

    private static PostChain postChain;
    private static boolean initialized = false;
    private static boolean loadFailed = false;
    private static int lastWidth = -1;
    private static int lastHeight = -1;

    public static void init() {
        if (initialized || loadFailed) return;

        try {
            Minecraft mc = Minecraft.getInstance();
            postChain = new PostChain(
                mc.getTextureManager(),
                mc.getResourceManager(),
                mc.getMainRenderTarget(),
                SHADER_LOCATION
            );
            postChain.resize(mc.getWindow().getWidth(), mc.getWindow().getHeight());
            lastWidth = mc.getWindow().getWidth();
            lastHeight = mc.getWindow().getHeight();
            initialized = true;
            LOGGER.debug("[Suppression] PostChain loaded successfully");
        } catch (Exception e) {
            LOGGER.error("[Suppression] Failed to load PostChain shader", e);
            loadFailed = true;
            postChain = null;
        }
    }

    public static void render(float partialTick) {
        if (!initialized || postChain == null) return;

        float level = SuppressionHandler.getLevel(partialTick);
        if (level < 0.001f) return;

        Minecraft mc = Minecraft.getInstance();

        int w = mc.getWindow().getWidth();
        int h = mc.getWindow().getHeight();
        if (w != lastWidth || h != lastHeight) {
            postChain.resize(w, h);
            lastWidth = w;
            lastHeight = h;
        }

        updateUniforms(level);
        postChain.process(partialTick);
        mc.getMainRenderTarget().bindWrite(false);
    }

    private static void updateUniforms(float level) {
        if (postChain == null) return;

        List<PostPass> passes = ((PostChainAccessor) postChain).getPasses();
        float blurStr = (float) Config.Suppression.blurStrength;
        float vigStr = (float) Config.Suppression.vignetteStrength;
        float desatStr = (float) Config.Suppression.desaturationStrength;

        float blurRadius = level * 8.0f * blurStr;

        // Pass 0: horizontal blur
        if (passes.size() > 0) {
            PostPass hBlur = passes.get(0);
            hBlur.getEffect().safeGetUniform("Radius").set(blurRadius);
            hBlur.getEffect().safeGetUniform("Intensity").set(level);
        }
        // Pass 1: vertical blur
        if (passes.size() > 1) {
            PostPass vBlur = passes.get(1);
            vBlur.getEffect().safeGetUniform("Radius").set(blurRadius);
            vBlur.getEffect().safeGetUniform("Intensity").set(level);
        }
        // Pass 2: combine (desaturation + vignette)
        if (passes.size() > 2) {
            PostPass combine = passes.get(2);
            combine.getEffect().safeGetUniform("Intensity").set(level);
            combine.getEffect().safeGetUniform("VignetteStrength").set(vigStr);
            combine.getEffect().safeGetUniform("DesatStrength").set(desatStr);
        }
    }

    public static void shutdown() {
        if (postChain != null) {
            postChain.close();
            postChain = null;
        }
        initialized = false;
        loadFailed = false;
        lastWidth = -1;
        lastHeight = -1;
    }

    public static boolean isReady() {
        return initialized && postChain != null;
    }
}
