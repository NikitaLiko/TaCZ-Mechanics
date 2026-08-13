package ru.liko.tacz_mechanics.client.freeaim;

import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.event.common.GunFireEvent;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.resource.pojo.data.gun.GunRecoilKeyFrame;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.LogicalSide;
import net.neoforged.fml.common.EventBusSubscriber;
import ru.liko.tacz_mechanics.Config;
import ru.liko.tacz_mechanics.TaczMechanics;

import java.util.HashMap;
import java.util.Map;

/**
 * Feeds a recoil impulse into the recoil spring when the local player fires.
 * TaCZ already applies recoil to the camera; this is the visual gun-model kick on top of it.
 * Strength comes from the gun's own TaCZ recoil curve, so a rifle kicks harder than an SMG.
 */
@EventBusSubscriber(modid = TaczMechanics.MODID, value = Dist.CLIENT)
public final class RecoilSource {

    /** Peak recoil of a mid-range rifle; every gun's peak is expressed relative to this. */
    private static final float REFERENCE_PEAK = 0.5f;
    private static final float MIN_FACTOR = 0.3f;
    private static final float MAX_FACTOR = 4f;
    /** Used when the gun index carries no recoil curve at all. */
    private static final float[] FALLBACK = {1f, 0.25f};

    /** gunId -> {pitchFactor, yawFactor}. Recoil curves are static data, so parse once per gun. */
    private static final Map<ResourceLocation, float[]> FACTOR_CACHE = new HashMap<>();

    private RecoilSource() {
    }

    @SubscribeEvent
    public static void onGunFire(GunFireEvent event) {
        if (!Config.FreeAim.enabled || !Config.FreeAim.recoilEnabled) {
            return;
        }
        if (event.getLogicalSide() != LogicalSide.CLIENT) {
            return;
        }
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null || player != event.getShooter()) {
            return;
        }

        float[] factors = factors(event.getGunItemStack());
        float scale = (float) Config.FreeAim.recoilScale;
        // Horizontal kick is randomised per shot: strict alternation reads as mechanical in a burst.
        float yawSign = player.getRandom().nextFloat() * 2f - 1f;
        FreeAimHandler.getInstance().addRecoilImpulse(factors[0] * scale, factors[1] * scale * yawSign);
    }

    /** {pitch, yaw} recoil strength of this gun relative to {@link #REFERENCE_PEAK}. */
    public static float[] factors(ItemStack stack) {
        if (!(stack.getItem() instanceof IGun iGun)) {
            return FALLBACK;
        }
        ResourceLocation gunId = iGun.getGunId(stack);
        float[] cached = FACTOR_CACHE.get(gunId);
        if (cached != null) {
            return cached;
        }
        float[] factors = TimelessAPI.getCommonGunIndex(gunId)
                .map(index -> index.getGunData().getRecoil())
                .map(recoil -> new float[]{peak(recoil.getPitch()), peak(recoil.getYaw())})
                .orElse(FALLBACK);
        FACTOR_CACHE.put(gunId, factors);
        return factors;
    }

    /** Largest absolute keyframe value, normalised against the reference rifle and clamped. */
    private static float peak(GunRecoilKeyFrame[] frames) {
        if (frames == null || frames.length == 0) {
            return MIN_FACTOR;
        }
        float peak = 0f;
        for (GunRecoilKeyFrame frame : frames) {
            for (float value : frame.getValue()) {
                peak = Math.max(peak, Math.abs(value));
            }
        }
        return Mth.clamp(peak / REFERENCE_PEAK, MIN_FACTOR, MAX_FACTOR);
    }

    /** The gun index is server-synced, so drop the cache on disconnect. */
    public static void clearCache() {
        FACTOR_CACHE.clear();
    }
}
