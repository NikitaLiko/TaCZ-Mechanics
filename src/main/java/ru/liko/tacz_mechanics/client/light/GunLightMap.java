package ru.liko.tacz_mechanics.client.light;

import it.unimi.dsi.fastutil.longs.Long2LongMap;
import it.unimi.dsi.fastutil.longs.Long2LongOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;

/**
 * Client-side registry of short-lived "spark" light positions (muzzle flashes,
 * tracer trail points) consumed by the light-engine mixins.
 * A spark holds its level for {@code ttlTicks}, then fades by {@code fadePerTick}
 * light levels each tick (0 = vanish instantly).
 * Values are packed as {@code level << 38 | fadePerTick << 32 | deadlineTick}.
 * Methods are synchronized: the light engine may query from a worker thread
 * while the client tick thread writes.
 */
public final class GunLightMap {

    private static final Long2LongMap SPARKS = new Long2LongOpenHashMap();
    private static final LongArrayList REMOVED_BUF = new LongArrayList(64);
    private static final LongArrayList CHANGED_BUF = new LongArrayList(64);
    private static long currentTick;

    static {
        SPARKS.defaultReturnValue(0L);
    }

    private GunLightMap() {
    }

    private static long pack(int level, int fade, long deadline) {
        return (long) level << 38 | (long) fade << 32 | deadline & 0xFFFFFFFFL;
    }

    /**
     * @return true when the stored light for this position actually changed
     *         (i.e. the caller should poke the light engine)
     */
    public static synchronized boolean putSpark(long packedPos, int level, int ttlTicks, int fadePerTick) {
        if (level <= 0 || ttlTicks <= 0) {
            return false;
        }
        level = Math.min(level, 15);
        fadePerTick = Math.max(0, Math.min(fadePerTick, 15));
        long deadline = currentTick + ttlTicks;
        long existing = SPARKS.get(packedPos);
        if (existing != 0L) {
            int exLevel = (int) (existing >>> 38);
            // brighter wins; same brightness — the one lasting longer wins
            if (level < exLevel || level == exLevel && deadline <= (existing & 0xFFFFFFFFL)) {
                return false;
            }
        }
        SPARKS.put(packedPos, pack(level, fadePerTick, deadline));
        return existing >>> 38 != level;
    }

    /** Called from the light engine mixins. 0 = no dynamic light here. */
    public static synchronized int getLight(long packedPos) {
        return (int) (SPARKS.get(packedPos) >>> 38);
    }

    /** Advances the clock, fades/expires sparks and relights their positions. */
    public static void tick() {
        long[] changed;
        synchronized (GunLightMap.class) {
            currentTick++;
            if (SPARKS.isEmpty()) {
                return;
            }
            REMOVED_BUF.clear();
            CHANGED_BUF.clear();
            for (Long2LongMap.Entry e : SPARKS.long2LongEntrySet()) {
                long v = e.getLongValue();
                if ((v & 0xFFFFFFFFL) > currentTick) {
                    continue;
                }
                int fade = (int) (v >>> 32) & 0x3F;
                int level = fade == 0 ? 0 : (int) (v >>> 38) - fade;
                if (level <= 0) {
                    REMOVED_BUF.add(e.getLongKey());
                } else {
                    e.setValue(pack(level, fade, currentTick + 1));
                    CHANGED_BUF.add(e.getLongKey());
                }
            }
            if (REMOVED_BUF.isEmpty() && CHANGED_BUF.isEmpty()) {
                return;
            }
            for (int i = 0; i < REMOVED_BUF.size(); i++) {
                SPARKS.remove(REMOVED_BUF.getLong(i));
            }
            CHANGED_BUF.addAll(REMOVED_BUF);
            changed = CHANGED_BUF.toLongArray();
            REMOVED_BUF.clear();
            CHANGED_BUF.clear();
        }
        ClientLevel level = Minecraft.getInstance().level;
        if (level != null) {
            for (long key : changed) {
                level.getLightEngine().checkBlock(BlockPos.of(key));
            }
        }
    }

    public static synchronized void clear() {
        SPARKS.clear();
        REMOVED_BUF.clear();
        CHANGED_BUF.clear();
    }
}
