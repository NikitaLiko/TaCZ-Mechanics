package ru.liko.tacz_mechanics.client.freeaim;

/**
 * Spring-damper physics for one rotation axis (degrees).
 * Semi-implicit Euler integration for stability. No Minecraft dependencies (unit-testable).
 */
public final class SwaySpring {

    /** Fraction of maxAngle that stays perfectly linear before the soft limit starts compressing. */
    private static final float KNEE = 0.8f;
    /** Multiple of maxAngle at which the raw spring state is stopped, purely to bound the integrator. */
    private static final float RAW_LIMIT_FACTOR = 3f;

    private float position;
    private float velocity;
    private float prevPosition;

    private float stiffness = 0.2f;
    private float damping = 0.5f;
    private float maxAngle = 5f;

    public void setParams(float stiffness, float damping, float maxAngle) {
        this.stiffness = stiffness;
        this.damping = damping;
        this.maxAngle = maxAngle;
    }

    public void addImpulse(float impulse) {
        velocity += impulse;
    }

    public void update(float dt) {
        update(dt, 1);
    }

    /**
     * Integrates one tick as {@code substeps} smaller steps. Explicit damping is only stable while
     * {@code damping * step < 1}, so a single dt=1 step caps how snappy the spring can be tuned;
     * substepping lifts that cap and lets stiff/heavily damped presets settle without fluttering.
     */
    public void update(float dt, int substeps) {
        prevPosition = position;
        float h = dt / substeps;
        for (int i = 0; i < substeps; i++) {
            step(h);
        }
    }

    private void step(float h) {
        float accel = -stiffness * position - damping * velocity;
        velocity += accel * h;
        position += velocity * h;
        // Backstop on the raw state, well outside the visible range. Explicit integration diverges
        // once damping * step > 1, which config extremes allow, and the old hard wall was silently
        // doubling as that safety net. At RAW_LIMIT the soft limit has long since flattened out, so
        // stopping dead here is invisible - unlike stopping dead at maxAngle, which twitched.
        float rawLimit = maxAngle * RAW_LIMIT_FACTOR;
        if (position > rawLimit) {
            position = rawLimit;
            velocity = 0f;
        } else if (position < -rawLimit) {
            position = -rawLimit;
            velocity = 0f;
        }
    }

    public float getValue() {
        return softLimit(position);
    }

    public float getInterpolated(float pt) {
        return softLimit(prevPosition + (position - prevPosition) * pt);
    }

    /**
     * Bounded output with a soft knee. The spring itself runs unclamped — a linear spring cannot
     * diverge — and only the visible angle is squashed. Killing the velocity at a hard wall stopped
     * the motion dead and read as a twitch; this decelerates into the limit instead, and the last
     * {@link #KNEE} of travel is where all the compression happens, so normal sway stays untouched.
     */
    private float softLimit(float value) {
        float knee = maxAngle * KNEE;
        float magnitude = Math.abs(value);
        if (magnitude <= knee) {
            return value;
        }
        float range = maxAngle - knee;
        if (range <= 0f) {
            return Math.signum(value) * maxAngle;
        }
        float soft = range * (float) Math.tanh((magnitude - knee) / range);
        return Math.signum(value) * (knee + soft);
    }

    public void reset() {
        position = 0f;
        velocity = 0f;
        prevPosition = 0f;
    }
}
