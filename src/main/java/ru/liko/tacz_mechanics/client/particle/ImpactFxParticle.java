package ru.liko.tacz_mechanics.client.particle;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

/**
 * One particle of an {@link ImpactFx.Layer}. Reproduces the MTS motion model that vanilla particle
 * types cannot: size lerp across the life, a fade-out window at the end, per-layer gravity and drag,
 * an optional "stops moving after N ticks" window for hanging dust, and either a random sprite from
 * the family or the family played as an animation.
 */
@OnlyIn(Dist.CLIENT)
public final class ImpactFxParticle extends TextureSheetParticle {

    private final ImpactFx.Layer layer;
    private final SpriteSet sprites;
    private final float sizeFrom;
    private final float sizeTo;
    private final float baseAlpha;

    ImpactFxParticle(ClientLevel level, double x, double y, double z, Vec3 velocity,
                     ImpactFx.Layer layer, SpriteSet sprites) {
        // The 3-arg ctor is deliberate: the 6-arg one perturbs the velocity by ±0.4, which would
        // throw the debris off the face normal we just computed.
        super(level, x, y, z);
        this.layer = layer;
        this.sprites = sprites;
        this.xd = velocity.x;
        this.yd = velocity.y;
        this.zd = velocity.z;
        this.lifetime = Math.max(1, layer.life());
        this.gravity = 0.0f;      // integrated in tick(): layer gravity is world-space blocks/tick²
        this.friction = 1.0f;     // ditto for drag
        this.hasPhysics = layer.stopOnGround();
        this.sizeFrom = layer.size().from();
        this.sizeTo = layer.size().resolvedTo();
        this.quadSize = this.sizeFrom;
        this.baseAlpha = layer.alpha();
        this.alpha = this.baseAlpha;

        if (layer.animated()) {
            setSpriteFromAge(sprites);
        } else {
            setSprite(sprites.get(this.random));
        }

        int rgb = layer.pickColor(this.random);
        if (rgb >= 0) {
            setColor(((rgb >> 16) & 0xFF) / 255.0f, ((rgb >> 8) & 0xFF) / 255.0f, (rgb & 0xFF) / 255.0f);
        }

        this.roll = this.random.nextFloat() * Mth.TWO_PI;
        this.oRoll = this.roll;
    }

    @Override
    public void tick() {
        this.xo = this.x;
        this.yo = this.y;
        this.zo = this.z;
        this.oRoll = this.roll;

        if (this.age++ >= this.lifetime) {
            remove();
            return;
        }

        if (this.layer.animated()) {
            setSpriteFromAge(this.sprites);
        }

        this.quadSize = Mth.lerp((float) this.age / this.lifetime, this.sizeFrom, this.sizeTo);

        int remaining = this.lifetime - this.age;
        this.alpha = this.layer.fadeOut() > 0 && remaining < this.layer.fadeOut()
            ? this.baseAlpha * remaining / this.layer.fadeOut()
            : this.baseAlpha;

        if (this.layer.moveTicks() >= 0 && this.age > this.layer.moveTicks()) {
            return;
        }

        this.yd += this.layer.gravity();
        move(this.xd, this.yd, this.zd);

        if (this.layer.stopOnGround() && this.onGround) {
            this.xd = 0.0;
            this.yd = 0.0;
            this.zd = 0.0;
        } else {
            this.xd *= this.layer.drag();
            this.yd *= this.layer.drag();
            this.zd *= this.layer.drag();
            this.roll += (float) (this.xd + this.zd);
        }
    }

    @Override
    protected int getLightColor(float partialTick) {
        return this.layer.bright() ? LightTexture.FULL_BRIGHT : super.getLightColor(partialTick);
    }

    @Override
    public ParticleRenderType getRenderType() {
        // ponytail: one blend mode for everything. MTS renders its "_translucent" sparks additively;
        // fullbright alpha is close enough here. Add an additive render type if sparks read as flat.
        return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT;
    }
}
