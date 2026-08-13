package ru.liko.tacz_mechanics.hitbox;

import com.tacz.guns.api.event.common.EntityHurtByGunEvent;
import net.minecraft.world.entity.Entity;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import ru.liko.tacz_mechanics.Config;
import ru.liko.tacz_mechanics.TaczMechanics;

import java.util.Map;
import java.util.WeakHashMap;

/**
 * Scales gun damage by the bone that was hit.
 *
 * <p>TaCZ builds a {@code TacHitResult} immediately before each {@code onHitEntity} call and posts
 * {@link EntityHurtByGunEvent.Pre} synchronously inside it, so the part recorded at construction is
 * always the one this event belongs to — including when a bullet pierces several players.
 */
@EventBusSubscriber(modid = TaczMechanics.MODID)
public final class LimbDamageHandler {

    // ponytail: single field instead of a per-bullet map — server thread only, set and consumed
    // within one synchronous call. Needs a real map only if TaCZ ever resolves hits off-thread.
    private static PlayerSkeleton.Part pending;

    private LimbDamageHandler() {
    }

    public static void setPending(PlayerSkeleton.Part part) {
        pending = part;
    }

    // ponytail: SuperbWarfare resolves every target's hit before applying any damage, so a single
    // pending field would be overwritten by the next target of a piercing round. Keyed by target
    // instead — one entry per target per tick, consumed by the damage call in the same tick.
    private static final Map<Entity, PlayerSkeleton.Part> sbwPending = new WeakHashMap<>();

    public static void setSbwPending(Entity target, PlayerSkeleton.Part part) {
        sbwPending.put(target, part);
    }

    /** @return {@code damage} scaled by the bone SuperbWarfare's bullet hit, if this mod resolved one */
    public static float sbwScale(Entity target, float damage) {
        PlayerSkeleton.Part part = sbwPending.remove(target);
        return part == null || !Config.Hitbox.enabled
                ? damage
                : damage * PlayerSkeleton.damageMultiplier(part);
    }

    @SubscribeEvent
    public static void onHurtByGun(EntityHurtByGunEvent.Pre event) {
        PlayerSkeleton.Part part = pending;
        pending = null;
        if (part == null || !Config.Hitbox.enabled) {
            return;
        }
        event.setBaseAmount(event.getBaseAmount() * PlayerSkeleton.damageMultiplier(part));
    }
}
