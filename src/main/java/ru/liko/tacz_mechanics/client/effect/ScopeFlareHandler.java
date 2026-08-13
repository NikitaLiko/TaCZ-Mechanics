package ru.liko.tacz_mechanics.client.effect;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.tacz.guns.api.entity.IGunOperator;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.api.item.attachment.AttachmentType;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderPlayerEvent;
import org.joml.Matrix4f;
import ru.liko.tacz_mechanics.Config;
import ru.liko.tacz_mechanics.TaczMechanics;
import com.tacz.guns.client.model.functional.BeamRenderer.LaserBeamRenderState;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;

@EventBusSubscriber(modid = TaczMechanics.MODID, value = Dist.CLIENT)
public class ScopeFlareHandler {

    private static final ResourceLocation FLARE_TEXTURE = ResourceLocation.fromNamespaceAndPath(TaczMechanics.MODID, "textures/scopeflare.png");

    private static final RenderType FLARE_RENDER_TYPE = RenderType.create("scope_flare",
            DefaultVertexFormat.NEW_ENTITY,
            VertexFormat.Mode.QUADS, 256, true, true,
            RenderType.CompositeState.builder()
                    .setShaderState(RenderStateShard.RENDERTYPE_ENTITY_TRANSLUCENT_EMISSIVE_SHADER)
                    .setTransparencyState(LaserBeamRenderState.LIGHTNING_ADDITIVE_TRANSPARENCY)
                    .setWriteMaskState(RenderStateShard.COLOR_DEPTH_WRITE)
                    .setCullState(RenderStateShard.NO_CULL)
                    .setTextureState(new RenderStateShard.TextureStateShard(FLARE_TEXTURE, false, false))
                    .createCompositeState(false));

    @SubscribeEvent
    public static void onRenderPlayerPost(RenderPlayerEvent.Post event) {
        if (!Config.ScopeFlare.enabled) {
            return;
        }

        Player player = event.getEntity();
        Minecraft mc = Minecraft.getInstance();
        
        if (player == mc.player && mc.options.getCameraType().isFirstPerson()) {
            return;
        }

        ItemStack mainHandItem = player.getMainHandItem();
        IGun iGun = IGun.getIGunOrNull(mainHandItem);
        if (iGun == null) return;

        IGunOperator operator = IGunOperator.fromLivingEntity(player);
        if (!operator.getSynIsAiming()) return;

        ResourceLocation scopeId = iGun.getAttachmentId(mainHandItem, AttachmentType.SCOPE);
        if (com.tacz.guns.api.DefaultAssets.EMPTY_ATTACHMENT_ID.equals(scopeId)) {
            scopeId = iGun.getBuiltInAttachmentId(mainHandItem, AttachmentType.SCOPE);
            if (com.tacz.guns.api.DefaultAssets.EMPTY_ATTACHMENT_ID.equals(scopeId)) {
                return;
            }
        }

        String scopeIdStr = scopeId.toString();

        // Чёрный список: у этих прицелов вспышки нет никогда
        if (Config.ScopeFlare.blacklistedScopes != null && Config.ScopeFlare.blacklistedScopes.contains(scopeIdStr)) {
            return;
        }

        // Проверяем, есть ли прицел в белом списке
        boolean isWhitelisted = Config.ScopeFlare.whitelistedScopes != null
                && Config.ScopeFlare.whitelistedScopes.contains(scopeIdStr);

        // Если прицела нет в белом списке, проверяем его кратность приближения (zoom)
        if (!isWhitelisted) {
            float zoom = iGun.getAimingZoom(mainHandItem);
            
            if (zoom <= Config.ScopeFlare.minZoom) {
                return;
            }
        }

        Camera camera = mc.gameRenderer.getMainCamera();
        Vec3 cameraPos = camera.getPosition();
        
        // Use lerped eye position for smooth rendering
        double eyeX = player.xOld + (player.getX() - player.xOld) * event.getPartialTick();
        double eyeY = player.yOld + (player.getY() - player.yOld) * event.getPartialTick() + player.getEyeHeight();
        double eyeZ = player.zOld + (player.getZ() - player.zOld) * event.getPartialTick();
        Vec3 playerEyePos = new Vec3(eyeX, eyeY, eyeZ);

        Vec3 toCamera = cameraPos.subtract(playerEyePos).normalize();
        Vec3 playerLook = player.getViewVector(event.getPartialTick()).normalize();

        double dot = playerLook.dot(toCamera);
        // ~25 degrees viewing angle where flare is visible
        if (dot < 0.90) return;

        double distance = cameraPos.distanceTo(playerEyePos);
        // Do not render if out of normal render distance or fully faded out
        if (distance < Config.ScopeFlare.fadeMinDistance || distance > 250.0) return;

        // Calculate distance fade
        float distanceFade = 1.0f;
        if (distance < Config.ScopeFlare.fadeMaxDistance) {
            distanceFade = (float) ((distance - Config.ScopeFlare.fadeMinDistance) / (Config.ScopeFlare.fadeMaxDistance - Config.ScopeFlare.fadeMinDistance));
        }

        // Base intensity based on view angle (0.0 at edge, 1.0 at center)
        float baseIntensity = (float) ((dot - 0.90) / 0.10);
        
        // Simulating sniper's micro-movements and breathing
        float time = player.tickCount + event.getPartialTick();
        
        // Slow breathing (10% amplitude)
        float breath = (float) Math.sin(time * 0.05f) * 0.1f;
        // Fast hand jitter (15% amplitude)
        float handJitter = (float) Math.sin(time * 0.8f) * 0.15f;
        // High frequency micro-flicker (5% amplitude)
        float microFlicker = (float) Math.sin(time * 2.3f) * 0.05f;
        
        float noise = 1.0f + breath + handJitter + microFlicker;
        
        // Total intensity
        float intensity = baseIntensity * noise * distanceFade;
        intensity = Math.min(1.0f, Math.max(0.0f, intensity));

        PoseStack poseStack = event.getPoseStack();
        MultiBufferSource buffer = event.getMultiBufferSource();

        poseStack.pushPose();
        
        // Translate to eye height
        poseStack.translate(0, player.getEyeHeight(), 0);

        // Move forward in look direction by configured offset
        double forwardOffset = Config.ScopeFlare.forwardOffset;
        poseStack.translate(playerLook.x * forwardOffset, playerLook.y * forwardOffset, playerLook.z * forwardOffset);

        // Billboard rotation: face camera
        poseStack.mulPose(camera.rotation());
        // Fix orientation to point towards camera
        poseStack.mulPose(Axis.YP.rotationDegrees(180.0F));

        // Scale based on distance to keep it somewhat visible at range (Battlefield style)
        // Add micro-size changes based on the same noise for realistic breathing/flickering
        float distanceScale = (float) (0.5 + (distance * 0.04));
        float scale = distanceScale * (0.95f + 0.05f * noise);
        poseStack.scale(scale, scale, scale);

        Matrix4f matrix4f = poseStack.last().pose();
        
        // We use our custom render type which combines additive transparency with texture color
        VertexConsumer vertexConsumer = buffer.getBuffer(FLARE_RENDER_TYPE);

        int a = Math.min(255, Math.max(0, (int) (255 * intensity)));
        int light = 15728880; // FULL_BRIGHT

        float size = 0.5f;
        
        vertexConsumer.addVertex(matrix4f, -size, -size, 0.0F).setColor(255, 255, 255, a).setUv(0.0F, 0.0F).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(0.0F, 0.0F, -1.0F);
        vertexConsumer.addVertex(matrix4f, -size, size, 0.0F).setColor(255, 255, 255, a).setUv(0.0F, 1.0F).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(0.0F, 0.0F, -1.0F);
        vertexConsumer.addVertex(matrix4f, size, size, 0.0F).setColor(255, 255, 255, a).setUv(1.0F, 1.0F).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(0.0F, 0.0F, -1.0F);
        vertexConsumer.addVertex(matrix4f, size, -size, 0.0F).setColor(255, 255, 255, a).setUv(1.0F, 0.0F).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(0.0F, 0.0F, -1.0F);

        poseStack.popPose();
    }
}
