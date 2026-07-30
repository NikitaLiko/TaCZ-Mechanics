package ru.liko.tacz_mechanics.client.particle;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import ru.liko.tacz_mechanics.data.codec.CodecUtils;
import ru.liko.tacz_mechanics.data.core.BlockTestable;

import java.util.List;
import java.util.Locale;

/**
 * One material's impact effect, loaded from {@code assets/tacz_mechanics/impact_fx/*.json}.
 *
 * <p>The layer fields are a port of the MTS/Immersive Vehicles bullet particle model, converted to
 * Minecraft units at authoring time: distances in blocks, times in ticks, {@code gravity} in
 * blocks/tick². Tuning happens by editing the JSON and pressing F3+T — nothing here needs a rebuild.
 */
public record ImpactFx(List<BlockTestable> blocks, int priority, List<Layer> layers) {

    public static final Codec<ImpactFx> CODEC = RecordCodecBuilder.create(instance -> instance.group(
        CodecUtils.strictOptionalFieldOf(Codec.list(BlockTestable.CODEC), "blocks", List.of()).forGetter(ImpactFx::blocks),
        CodecUtils.strictOptionalFieldOf(Codec.INT, "priority", 0).forGetter(ImpactFx::priority),
        Codec.list(Layer.CODEC).fieldOf("layers").forGetter(ImpactFx::layers)
    ).apply(instance, ImpactFx::new));

    /** An empty block list is the catch-all fallback; give those files a low priority. */
    public boolean matches(Level level, BlockPos pos, BlockState state) {
        if (blocks.isEmpty()) return true;
        for (BlockTestable testable : blocks) {
            if (testable.test(level, pos, state)) return true;
        }
        return false;
    }

    /** Quad half-width in blocks, lerped from {@code from} to {@code to} across the particle's life. */
    public record Size(float from, float to) {
        public static final Codec<Size> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.FLOAT.fieldOf("from").forGetter(Size::from),
            CodecUtils.strictOptionalFieldOf(Codec.FLOAT, "to", -1.0f).forGetter(Size::to)
        ).apply(instance, Size::new));

        public float resolvedTo() {
            return to < 0.0f ? from : to;
        }
    }

    /**
     * Launch velocity in the struck face's frame: {@code velocity} along the outward normal,
     * {@code spreadNormal} the random ± added to it, {@code spreadTangent} the random ± in both
     * directions across the surface. Mirrors MTS {@code initialVelocity}/{@code spreadRandomness},
     * whose X and Z spreads are always equal in the official configs.
     */
    public record Motion(double velocity, double spreadNormal, double spreadTangent) {
        public static final Codec<Motion> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            CodecUtils.strictOptionalFieldOf(Codec.DOUBLE, "velocity", 0.0).forGetter(Motion::velocity),
            CodecUtils.strictOptionalFieldOf(Codec.DOUBLE, "spreadNormal", 0.0).forGetter(Motion::spreadNormal),
            CodecUtils.strictOptionalFieldOf(Codec.DOUBLE, "spreadTangent", 0.0).forGetter(Motion::spreadTangent)
        ).apply(instance, Motion::new));

        public static final Motion NONE = new Motion(0.0, 0.0, 0.0);

        /**
         * Draws one launch velocity in the struck face's frame. The normal is assumed unit length;
         * the tangent basis is built from whichever world axis is least parallel to it, so the
         * math holds for floors and ceilings as well as walls.
         */
        public Vec3 sample(Vec3 normal, RandomSource random) {
            Vec3 helper = Math.abs(normal.y) > 0.99 ? new Vec3(1.0, 0.0, 0.0) : new Vec3(0.0, 1.0, 0.0);
            Vec3 u = normal.cross(helper).normalize();
            Vec3 v = normal.cross(u).normalize();
            return normal.scale(velocity + jitter(random, spreadNormal))
                .add(u.scale(jitter(random, spreadTangent)))
                .add(v.scale(jitter(random, spreadTangent)));
        }

        private static double jitter(RandomSource random, double amount) {
            return amount == 0.0 ? 0.0 : (random.nextDouble() * 2.0 - 1.0) * amount;
        }
    }

    public record Layer(
        String sprites,
        int count,
        int life,
        Size size,
        float alpha,
        int fadeOut,
        double offset,
        Motion motion,
        double gravity,
        double drag,
        int moveTicks,
        boolean stopOnGround,
        boolean bright,
        boolean animated,
        List<Integer> colors
    ) {
        /** Hex string like {@code "BAB0A7"}, matching the MTS colour lists. */
        private static final Codec<Integer> COLOR = Codec.STRING.flatXmap(
            s -> {
                try {
                    return DataResult.success(Integer.parseInt(s.replace("#", ""), 16));
                } catch (NumberFormatException e) {
                    return DataResult.error(() -> "Not a hex colour: " + s);
                }
            },
            i -> DataResult.success(String.format(Locale.ROOT, "%06X", i))
        );

        public static final Codec<Layer> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.STRING.fieldOf("sprites").forGetter(Layer::sprites),
            CodecUtils.strictOptionalFieldOf(Codec.INT, "count", 1).forGetter(Layer::count),
            Codec.INT.fieldOf("life").forGetter(Layer::life),
            Size.CODEC.fieldOf("size").forGetter(Layer::size),
            CodecUtils.strictOptionalFieldOf(Codec.FLOAT, "alpha", 1.0f).forGetter(Layer::alpha),
            CodecUtils.strictOptionalFieldOf(Codec.INT, "fadeOut", 0).forGetter(Layer::fadeOut),
            CodecUtils.strictOptionalFieldOf(Codec.DOUBLE, "offset", 0.0).forGetter(Layer::offset),
            CodecUtils.strictOptionalFieldOf(Motion.CODEC, "motion", Motion.NONE).forGetter(Layer::motion),
            CodecUtils.strictOptionalFieldOf(Codec.DOUBLE, "gravity", 0.0).forGetter(Layer::gravity),
            CodecUtils.strictOptionalFieldOf(Codec.DOUBLE, "drag", 1.0).forGetter(Layer::drag),
            CodecUtils.strictOptionalFieldOf(Codec.INT, "moveTicks", -1).forGetter(Layer::moveTicks),
            CodecUtils.strictOptionalFieldOf(Codec.BOOL, "stopOnGround", false).forGetter(Layer::stopOnGround),
            CodecUtils.strictOptionalFieldOf(Codec.BOOL, "bright", false).forGetter(Layer::bright),
            CodecUtils.strictOptionalFieldOf(Codec.BOOL, "animated", false).forGetter(Layer::animated),
            CodecUtils.strictOptionalFieldOf(Codec.list(COLOR), "colors", List.of()).forGetter(Layer::colors)
        ).apply(instance, Layer::new));

        /** -1 when the layer has no tint and the sprite should render at its own colour. */
        public int pickColor(RandomSource random) {
            if (colors.isEmpty()) return -1;
            return colors.get(random.nextInt(colors.size()));
        }
    }
}
