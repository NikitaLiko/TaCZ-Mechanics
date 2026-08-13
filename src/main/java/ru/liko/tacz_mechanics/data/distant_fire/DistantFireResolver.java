package ru.liko.tacz_mechanics.data.distant_fire;

/**
 * Stateless resolver: given a sound, bands and a listener distance, returns up to two layers
 * with their crossfade volumes. The only place where distant-fire selection logic lives.
 */
public final class DistantFireResolver {

    /** Single resolution result. {@code -1} indicates "no sound for this slot". */
    public record Resolution(int primaryIndex, float primaryVolume, int secondaryIndex, float secondaryVolume) {
        public static final Resolution SILENT = new Resolution(-1, 0f, -1, 0f);

        public boolean hasPrimary() {
            return primaryIndex >= 0 && primaryVolume > 0.01f;
        }

        public boolean hasSecondary() {
            return secondaryIndex >= 0 && secondaryVolume > 0.01f;
        }

        public boolean isSilent() {
            return !hasPrimary() && !hasSecondary();
        }
    }

    private DistantFireResolver() {
    }

    public static Resolution resolve(DistantFireSound sound, DistantFireBands rawBands, double distance, boolean suppressed) {
        DistantFireBands bands = rawBands.clampToLayers(sound.layerCount(suppressed));
        int near = bands.taczNearRange();
        int t = Math.max(0, bands.transitionBlocks());
        int n = sound.layerCount(suppressed);

        if (distance <= near - t) {
            return Resolution.SILENT;
        }

        if (t > 0 && distance < near + t) {
            float ratio = clamp01((float) ((distance - (near - t)) / (2.0 * t)));
            float vol = ratio * sound.layer(0, suppressed).volume();
            return new Resolution(-1, 0f, 0, vol);
        }

        for (int i = 0; i < n - 1; i++) {
            int bound = bands.rawUpperBound(i);
            if (distance < bound - t) {
                return new Resolution(i, sound.layer(i, suppressed).volume(), -1, 0f);
            }
            if (t > 0 && distance < bound + t) {
                float ratio = clamp01((float) ((distance - (bound - t)) / (2.0 * t)));
                float curr = (1f - ratio) * sound.layer(i, suppressed).volume();
                float next = ratio * sound.layer(i + 1, suppressed).volume();
                return new Resolution(i, curr, i + 1, next);
            }
            if (t == 0 && distance <= bound) {
                return new Resolution(i, sound.layer(i, suppressed).volume(), -1, 0f);
            }
        }

        return new Resolution(n - 1, sound.layer(n - 1, suppressed).volume(), -1, 0f);
    }

    private static float clamp01(float v) {
        if (v < 0f) return 0f;
        if (v > 1f) return 1f;
        return v;
    }
}
