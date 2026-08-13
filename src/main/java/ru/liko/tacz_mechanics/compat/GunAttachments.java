package ru.liko.tacz_mechanics.compat;

import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.resource.modifier.AttachmentCacheProperty;
import com.tacz.guns.resource.modifier.custom.SilenceModifier;
import it.unimi.dsi.fastutil.Pair;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

/**
 * Common (client + server safe) helpers for reading TaCZ attachment state off a gun stack.
 */
public final class GunAttachments {

    private GunAttachments() {
    }

    /** True when the gun has a silencer/suppressor muzzle attachment installed. */
    // ponytail: evaluated per shot without a cache; memoize per gun stack if full-auto profiling shows it
    public static boolean isSilenced(ItemStack gun) {
        if (!(gun.getItem() instanceof IGun iGun)) {
            return false;
        }
        ResourceLocation gunId = iGun.getGunId(gun);
        if (gunId == null) {
            return false;
        }
        return TimelessAPI.getCommonGunIndex(gunId).map(index -> {
            AttachmentCacheProperty cache = new AttachmentCacheProperty();
            cache.eval(gun, index.getGunData());
            return cache.getCache(SilenceModifier.ID) instanceof Pair<?, ?> pair
                    && pair.right() instanceof Boolean b && b;
        }).orElse(false);
    }
}
