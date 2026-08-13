package ru.liko.tacz_mechanics.client.sound;

import com.tacz.guns.client.sound.GunSoundInstance;
import com.tacz.guns.init.ModSounds;
import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import ru.liko.pjmapi.api.client.SoundFilters;
import ru.liko.tacz_mechanics.Config;
import ru.liko.tacz_mechanics.client.ClientDistantFireSettings;

/**
 * Low-pass muffle on TaCZ native 3rd-person shot sounds based on listener distance
 * (only when distant_fire is enabled on the client).
 */
public final class DistantFireHandler {
    private static final Logger LOGGER = LogUtils.getLogger();

    private DistantFireHandler() {
    }

    @Nullable
    public static GunSoundInstance handleGunSound(
            Entity entity,
            ResourceLocation soundName,
            float volume,
            float pitch,
            int attenuationDistance,
            boolean mono,
            double distanceToPlayer) {

        if (!ClientDistantFireSettings.enabled()) {
            return null;
        }

        double near = ClientDistantFireSettings.nearSoundRange();
        double far = Math.max(ClientDistantFireSettings.muffleFarDistance(), near + 1.0);

        // Линейная рампа приглушения от near до far. Раньше жила в SoundFilterUtil,
        // который вместе с остальным низкочастотным трактом уехал в PJM-API.
        final float muffleAmount = (float) Mth.clamp((distanceToPlayer - near) / (far - near), 0.0, 1.0);
        if (muffleAmount <= 0.01f) {
            return null;
        }

        GunSoundInstance soundInstance = new GunSoundInstance(
            ModSounds.GUN.get(),
            SoundSource.PLAYERS,
            volume,
            pitch,
            entity,
            attenuationDistance,
            soundName,
            mono
        );

        SoundFilters.attach(soundInstance, SoundFilters.muffle(() -> muffleAmount));
        Minecraft.getInstance().getSoundManager().play(soundInstance);

        if (Config.debug) {
            LOGGER.info("[DistantFire] Muffle applied sound={} dist={} muffle={}", soundName, distanceToPlayer, muffleAmount);
        }
        return soundInstance;
    }
}
