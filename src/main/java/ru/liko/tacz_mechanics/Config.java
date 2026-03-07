package ru.liko.tacz_mechanics;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.config.ModConfigEvent;
import net.neoforged.neoforge.common.ModConfigSpec;

@EventBusSubscriber(modid = TaczMechanics.MODID, bus = EventBusSubscriber.Bus.MOD)
public class Config {
    private static final ModConfigSpec.Builder CLIENT_BUILDER = new ModConfigSpec.Builder();
    private static final ModConfigSpec.Builder SERVER_BUILDER = new ModConfigSpec.Builder();

    private static final ModConfigSpec.BooleanValue DEBUG = SERVER_BUILDER
            .comment("Enable debug logging for all mechanics (spams logs, use only for testing)")
            .define("debug", false);

    public static boolean debug;

    public static final class Tweaks {
        private static final ModConfigSpec.BooleanValue ALWAYS_FILTER_BY_HAND = SERVER_BUILDER
                .comment("Always enable filter by hand option in the gun smith table")
                .define("tweaks.alwaysFilterByHand", true);
        private static final ModConfigSpec.BooleanValue SUPPRESS_HEAD_HIT_SOUNDS = SERVER_BUILDER
                .comment("Suppresses the sound that plays when you land a headshot")
                .define("tweaks.suppressHeadHitSounds", false);
        private static final ModConfigSpec.BooleanValue SUPPRESS_FLESH_HIT_SOUNDS = SERVER_BUILDER
                .comment("Suppresses the sound that plays when you land a shot that is not a headshot")
                .define("tweaks.suppressFleshHitSounds", false);
        private static final ModConfigSpec.BooleanValue SUPPRESS_KILL_SOUNDS = SERVER_BUILDER
                .comment("Suppresses the sound that plays when you kill an entity with a gun")
                .define("tweaks.suppressKillSounds", false);
        private static final ModConfigSpec.BooleanValue HIDE_HIT_MARKERS = SERVER_BUILDER
                .comment("Hides hit markers when hitting entities")
                .define("tweaks.hideHitMarkers", false);
        private static final ModConfigSpec.BooleanValue HIDE_GUN_CROSSHAIR = SERVER_BUILDER
                .comment("Completely hides the gun crosshair (TACZ crosshair)")
                .define("tweaks.hideGunCrosshair", false);

        public static boolean alwaysFilterByHand;
        public static boolean suppressHeadHitSounds;
        public static boolean suppressFleshHitSounds;
        public static boolean suppressKillSounds;
        public static boolean hideHitMarkers;
        public static boolean hideGunCrosshair;

        private static void load() {
            alwaysFilterByHand = ALWAYS_FILTER_BY_HAND.get();
            suppressHeadHitSounds = SUPPRESS_HEAD_HIT_SOUNDS.get();
            suppressFleshHitSounds = SUPPRESS_FLESH_HIT_SOUNDS.get();
            suppressKillSounds = SUPPRESS_KILL_SOUNDS.get();
            hideHitMarkers = HIDE_HIT_MARKERS.get();
            hideGunCrosshair = HIDE_GUN_CROSSHAIR.get();
        }

        static void init() {
        }

        private Tweaks() {
        }
    }

    public static final class DistantFire {
        private static final ModConfigSpec.BooleanValue ENABLED = SERVER_BUILDER
                .comment("Enable distant fire sounds (hear gunshots from far away)")
                .define("distantFire.enabled", true);
        private static final ModConfigSpec.IntValue MIN_DISTANCE = SERVER_BUILDER
                .comment("Minimum distance (blocks) for distant fire effect to start")
                .defineInRange("distantFire.minDistance", 64, 16, 500);
        private static final ModConfigSpec.IntValue MAX_DISTANCE = SERVER_BUILDER
                .comment("Maximum distance (blocks) to hear distant fire")
                .defineInRange("distantFire.maxDistance", 300, 100, 1000);
        private static final ModConfigSpec.DoubleValue VOLUME_MULTIPLIER = SERVER_BUILDER
                .comment("Volume multiplier for distant fire sounds")
                .defineInRange("distantFire.volumeMultiplier", 0.7, 0.1, 1.0);

        public static boolean enabled;
        public static int minDistance;
        public static int maxDistance;
        public static double volumeMultiplier;

        private static void load() {
            enabled = ENABLED.get();
            minDistance = MIN_DISTANCE.get();
            maxDistance = MAX_DISTANCE.get();
            volumeMultiplier = VOLUME_MULTIPLIER.get();
        }

        static void init() {
        }

        private DistantFire() {
        }
    }

    public static final class Whizz {
        private static final ModConfigSpec.BooleanValue ENABLED = SERVER_BUILDER
                .comment("Enable bullet whizz sounds (hear bullets passing by)")
                .define("whizz.enabled", true);

        public static boolean enabled;

        private static void load() {
            enabled = ENABLED.get();
        }

        static void init() {
        }

        private Whizz() {
        }
    }

    public static final class Suppression {
        private static final ModConfigSpec.BooleanValue ENABLED = SERVER_BUILDER
                .comment("Enable suppression visual effect when bullets fly near or impact nearby")
                .define("suppression.enabled", true);
        private static final ModConfigSpec.DoubleValue DETECTION_RADIUS = SERVER_BUILDER
                .comment("Maximum distance (blocks) for suppression detection")
                .defineInRange("suppression.detectionRadius", 10.0, 1.0, 50.0);
        private static final ModConfigSpec.DoubleValue FLYBY_INTENSITY = SERVER_BUILDER
                .comment("Base intensity added per bullet fly-by at closest range (0.0-1.0)")
                .defineInRange("suppression.flybyIntensity", 0.25, 0.01, 1.0);
        private static final ModConfigSpec.DoubleValue IMPACT_INTENSITY_MULTIPLIER = SERVER_BUILDER
                .comment("Intensity multiplier for bullet impacts near player (relative to fly-by)")
                .defineInRange("suppression.impactIntensityMultiplier", 0.7, 0.1, 2.0);
        private static final ModConfigSpec.DoubleValue SHAKE_INTENSITY = CLIENT_BUILDER
                .comment("Camera shake intensity multiplier (0.0-3.0)")
                .defineInRange("suppression.shakeIntensity", 1.0, 0.0, 3.0);
        private static final ModConfigSpec.DoubleValue SHAKE_SPEED = CLIENT_BUILDER
                .comment("Camera shake speed (frequency of shakes)")
                .defineInRange("suppression.shakeSpeed", 1.0, 0.0, 3.0);
        private static final ModConfigSpec.DoubleValue DECAY_RATE = CLIENT_BUILDER
                .comment("Suppression decay per tick (how fast the effect fades)")
                .defineInRange("suppression.decayRate", 0.015, 0.001, 0.2);
        private static final ModConfigSpec.DoubleValue MAX_INTENSITY = SERVER_BUILDER
                .comment("Maximum suppression intensity (0.0-1.0)")
                .defineInRange("suppression.maxIntensity", 1.0, 0.1, 1.0);
        private static final ModConfigSpec.DoubleValue BLUR_STRENGTH = SERVER_BUILDER
                .comment("Blur effect strength multiplier")
                .defineInRange("suppression.blurStrength", 1.0, 0.0, 3.0);
        private static final ModConfigSpec.DoubleValue VIGNETTE_STRENGTH = SERVER_BUILDER
                .comment("Vignette darkening strength multiplier")
                .defineInRange("suppression.vignetteStrength", 1.0, 0.0, 3.0);
        private static final ModConfigSpec.DoubleValue DESATURATION_STRENGTH = SERVER_BUILDER
                .comment("Color desaturation strength multiplier")
                .defineInRange("suppression.desaturationStrength", 1.0, 0.0, 3.0);

        public static boolean enabled;
        public static double detectionRadius;
        public static double flybyIntensity;
        public static double impactIntensityMultiplier;
        public static double shakeIntensity;
        public static double shakeSpeed;
        public static double decayRate;
        public static double maxIntensity;
        public static double blurStrength;
        public static double vignetteStrength;
        public static double desaturationStrength;

        private static void loadServer() {
            enabled = ENABLED.get();
            detectionRadius = DETECTION_RADIUS.get();
            flybyIntensity = FLYBY_INTENSITY.get();
            impactIntensityMultiplier = IMPACT_INTENSITY_MULTIPLIER.get();
        }

        private static void loadClient() {
            decayRate = DECAY_RATE.get();
            shakeIntensity = SHAKE_INTENSITY.get();
            shakeSpeed = SHAKE_SPEED.get();
            maxIntensity = MAX_INTENSITY.get();
            blurStrength = BLUR_STRENGTH.get();
            vignetteStrength = VIGNETTE_STRENGTH.get();
            desaturationStrength = DESATURATION_STRENGTH.get();
        }

        static void init() {
        }

        private Suppression() {
        }
    }

    public static final class Ricochet {
        private static final ModConfigSpec.BooleanValue ENABLED = SERVER_BUILDER
                .comment("Enable bullet ricochet off blocks")
                .define("ricochet.enabled", true);
        private static final ModConfigSpec.BooleanValue DEMO_PRESET = SERVER_BUILDER
                .comment("Enable demo ricochet preset (forces 100% ricochet-friendly values)")
                .define("ricochet.demoPreset", false);
        private static final ModConfigSpec.BooleanValue DEBUG = SERVER_BUILDER
                .comment("Enable debug logging for ricochet decisions")
                .define("ricochet.debug", false);
        private static final ModConfigSpec.DoubleValue MIN_SPEED = SERVER_BUILDER
                .comment("Minimum bullet speed required to ricochet")
                .defineInRange("ricochet.minSpeed", 1.0, 0.05, 100.0);
        private static final ModConfigSpec.DoubleValue MIN_ANGLE = SERVER_BUILDER
                .comment("Minimum incidence angle (degrees) from surface normal to allow ricochet")
                .defineInRange("ricochet.minAngle", 60.0, 10.0, 89.0);
        private static final ModConfigSpec.IntValue MAX_BOUNCES = SERVER_BUILDER
                .comment("Maximum number of ricochets per bullet")
                .defineInRange("ricochet.maxBounces", 1, 0, 10);
        private static final ModConfigSpec.DoubleValue SPEED_MULTIPLIER = SERVER_BUILDER
                .comment("Speed multiplier applied after ricochet")
                .defineInRange("ricochet.speedMultiplier", 0.6, 0.1, 1.0);
        private static final ModConfigSpec.DoubleValue CHANCE = SERVER_BUILDER
                .comment("Chance of ricochet when conditions are met (0.0-1.0)")
                .defineInRange("ricochet.chance", 0.35, 0.0, 1.0);

        public static boolean enabled;
        public static boolean demoPreset;
        public static boolean debug;
        public static double minSpeed;
        public static double minAngle;
        public static int maxBounces;
        public static double speedMultiplier;
        public static double chance;

        private static void load() {
            enabled = ENABLED.get();
            demoPreset = DEMO_PRESET.get();
            debug = DEBUG.get();
            if (demoPreset) {
                minSpeed = 0.05;
                minAngle = 10.0;
                maxBounces = 3;
                speedMultiplier = 0.8;
                chance = 1.0;
            } else {
                minSpeed = MIN_SPEED.get();
                minAngle = MIN_ANGLE.get();
                maxBounces = MAX_BOUNCES.get();
                speedMultiplier = SPEED_MULTIPLIER.get();
                chance = CHANCE.get();
            }
        }

        static void init() {
        }

        private Ricochet() {
        }
    }

    public static final class FreeAim {
        private static final ModConfigSpec.BooleanValue ENABLED = SERVER_BUILDER
                .comment("Enable free aim - gun direction lags behind camera movement")
                .define("freeAim.enabled", true);
        private static final ModConfigSpec.DoubleValue MAX_ANGLE = SERVER_BUILDER
                .comment("Maximum angle (degrees) the gun can deviate from view direction")
                .defineInRange("freeAim.maxAngle", 2.5, 0.5, 25.0);
        private static final ModConfigSpec.DoubleValue LERP_SPEED = SERVER_BUILDER
                .comment("Speed at which the gun catches up to view direction (0.0-1.0, higher = faster)")
                .defineInRange("freeAim.lerpSpeed", 0.15, 0.01, 1.0);
        private static final ModConfigSpec.BooleanValue DISABLE_WHEN_AIMING = SERVER_BUILDER
                .comment("Disable free aim when aiming down sights")
                .define("freeAim.disableWhenAiming", true);
        private static final ModConfigSpec.DoubleValue CROSSHAIR_SCALE = SERVER_BUILDER
                .comment("Scale factor for converting free aim angle to screen pixels")
                .defineInRange("freeAim.crosshairScale", 10.0, 1.0, 50.0);
        private static final ModConfigSpec.BooleanValue DISABLE_CROSSHAIR_MOVEMENT = SERVER_BUILDER
                .comment("Disable crosshair movement with free aim (crosshair stays centered)")
                .define("freeAim.disableCrosshairMovement", false);

        public static boolean enabled;
        public static double maxAngle;
        public static double lerpSpeed;
        public static boolean disableWhenAiming;
        public static double crosshairScale;
        public static boolean disableCrosshairMovement;

        private static void loadServer() {
            enabled = ENABLED.get();
            maxAngle = MAX_ANGLE.get();
            lerpSpeed = LERP_SPEED.get();
            disableWhenAiming = DISABLE_WHEN_AIMING.get();
        }

        private static void loadClient() {
            crosshairScale = CROSSHAIR_SCALE.get();
            disableCrosshairMovement = DISABLE_CROSSHAIR_MOVEMENT.get();
        }

        static void init() {
        }

        private FreeAim() {
        }
    }

    public static final class Movement {
        private static final ModConfigSpec.BooleanValue ENABLED = SERVER_BUILDER
                .comment("Enable advanced movement mechanics (sitting, crawling, leaning, sliding)")
                .define("movement.enabled", true);
        private static final ModConfigSpec.BooleanValue SIT_ENABLED = SERVER_BUILDER
                .comment("Enable sitting/crouching pose")
                .define("movement.sitEnabled", true);
        private static final ModConfigSpec.BooleanValue CRAWL_ENABLED = SERVER_BUILDER
                .comment("Enable crawling/prone pose")
                .define("movement.crawlEnabled", true);
        private static final ModConfigSpec.BooleanValue SLIDE_ENABLED = SERVER_BUILDER
                .comment("Enable sliding when sprinting + sit key")
                .define("movement.slideEnabled", true);
        private static final ModConfigSpec.DoubleValue SLIDE_MAX_FORCE = SERVER_BUILDER
                .comment("Maximum sliding force")
                .defineInRange("movement.slideMaxForce", 1.0, 0.1, 3.0);
        private static final ModConfigSpec.BooleanValue LEAN_AUTO_HOLD = SERVER_BUILDER
                .comment("Auto-hold lean position (toggle mode)")
                .define("movement.leanAutoHold", false);
        private static final ModConfigSpec.BooleanValue LEAN_MOUSE_CORRECTION = SERVER_BUILDER
                .comment("Correct mouse input when leaning")
                .define("movement.leanMouseCorrection", true);
        private static final ModConfigSpec.BooleanValue CRAWL_BLOCK_VIEW = SERVER_BUILDER
                .comment("Limit view angle when crawling")
                .define("movement.crawlBlockView", true);
        private static final ModConfigSpec.DoubleValue CRAWL_BLOCK_ANGLE = SERVER_BUILDER
                .comment("Maximum view angle when crawling (in degrees)")
                .defineInRange("movement.crawlBlockAngle", 90.0, 30.0, 180.0);
        private static final ModConfigSpec.DoubleValue SIT_COOLDOWN = SERVER_BUILDER
                .comment("Cooldown between sit actions (seconds)")
                .defineInRange("movement.sitCooldown", 0.75, 0.0, 5.0);
        private static final ModConfigSpec.DoubleValue CRAWL_COOLDOWN = SERVER_BUILDER
                .comment("Cooldown between crawl actions (seconds)")
                .defineInRange("movement.crawlCooldown", 0.75, 0.0, 5.0);
        private static final ModConfigSpec.DoubleValue LEAN_COOLDOWN = SERVER_BUILDER
                .comment("Cooldown between lean actions (seconds)")
                .defineInRange("movement.leanCooldown", 0.0, 0.0, 5.0);
        private static final ModConfigSpec.BooleanValue SIT_AUTO_HOLD = SERVER_BUILDER
                .comment("Auto-hold sit position (toggle mode)")
                .define("movement.sitAutoHold", true);
        private static final ModConfigSpec.DoubleValue SIT_HEIGHT = SERVER_BUILDER
                .comment("Hitbox height when sitting/crouching")
                .defineInRange("movement.sitHeight", 1.2, 0.2, 3.0);
        private static final ModConfigSpec.DoubleValue SIT_WIDTH = SERVER_BUILDER
                .comment("Hitbox width when sitting/crouching")
                .defineInRange("movement.sitWidth", 0.6, 0.1, 3.0);
        private static final ModConfigSpec.DoubleValue SIT_EYE_HEIGHT = SERVER_BUILDER
                .comment("Eye height when sitting/crouching")
                .defineInRange("movement.sitEyeHeight", 1.0, 0.1, 3.0);
        private static final ModConfigSpec.DoubleValue CRAWL_HEIGHT = SERVER_BUILDER
                .comment("Hitbox height when crawling")
                .defineInRange("movement.crawlHeight", 0.6, 0.1, 3.0);
        private static final ModConfigSpec.DoubleValue CRAWL_WIDTH = SERVER_BUILDER
                .comment("Hitbox width when crawling")
                .defineInRange("movement.crawlWidth", 0.6, 0.1, 3.0);
        private static final ModConfigSpec.DoubleValue CRAWL_EYE_HEIGHT = SERVER_BUILDER
                .comment("Eye height when crawling")
                .defineInRange("movement.crawlEyeHeight", 0.4, 0.1, 3.0);
        private static final ModConfigSpec.DoubleValue SIT_Y_OFFSET = SERVER_BUILDER
                .comment("Vertical offset for sitting hitbox (positive = up, negative = down)")
                .defineInRange("movement.sitYOffset", 0.0, -5.0, 5.0);
        private static final ModConfigSpec.DoubleValue CRAWL_Y_OFFSET = SERVER_BUILDER
                .comment("Vertical offset for crawling hitbox (positive = up, negative = down)")
                .defineInRange("movement.crawlYOffset", 0.0, -5.0, 5.0);

        public static boolean enabled;
        public static boolean sitEnabled;
        public static boolean crawlEnabled;
        public static boolean slideEnabled;
        public static double slideMaxForce;
        public static boolean leanAutoHold;
        public static boolean leanMouseCorrection;
        public static boolean crawlBlockView;
        public static double crawlBlockAngle;
        public static double sitCooldown;
        public static double crawlCooldown;
        public static double leanCooldown;
        public static boolean sitAutoHold;
        public static float sitHeight;
        public static float sitWidth;
        public static float sitEyeHeight;
        public static float crawlHeight;
        public static float crawlWidth;
        public static float crawlEyeHeight;
        public static float sitYOffset;
        public static float crawlYOffset;

        private static void load() {
            enabled = ENABLED.get();
            sitEnabled = SIT_ENABLED.get();
            crawlEnabled = CRAWL_ENABLED.get();
            slideEnabled = SLIDE_ENABLED.get();
            slideMaxForce = SLIDE_MAX_FORCE.get();
            leanAutoHold = LEAN_AUTO_HOLD.get();
            leanMouseCorrection = LEAN_MOUSE_CORRECTION.get();
            crawlBlockView = CRAWL_BLOCK_VIEW.get();
            crawlBlockAngle = CRAWL_BLOCK_ANGLE.get();
            sitCooldown = SIT_COOLDOWN.get();
            crawlCooldown = CRAWL_COOLDOWN.get();
            leanCooldown = LEAN_COOLDOWN.get();
            sitAutoHold = SIT_AUTO_HOLD.get();
            sitHeight = SIT_HEIGHT.get().floatValue();
            sitWidth = SIT_WIDTH.get().floatValue();
            sitEyeHeight = SIT_EYE_HEIGHT.get().floatValue();
            crawlHeight = CRAWL_HEIGHT.get().floatValue();
            crawlWidth = CRAWL_WIDTH.get().floatValue();
            crawlEyeHeight = CRAWL_EYE_HEIGHT.get().floatValue();
            sitYOffset = SIT_Y_OFFSET.get().floatValue();
            crawlYOffset = CRAWL_Y_OFFSET.get().floatValue();
        }

        static void init() {
        }

        private Movement() {
        }
    }

    static {
        Tweaks.init();
        DistantFire.init();
        Whizz.init();
        Suppression.init();
        Ricochet.init();
        FreeAim.init();
        Movement.init();
    }

    static final ModConfigSpec CLIENT_SPEC = CLIENT_BUILDER.build();
    static final ModConfigSpec SERVER_SPEC = SERVER_BUILDER.build();

    @SubscribeEvent
    static void onLoad(final ModConfigEvent event) {
        // Do not read config values on Unloading (e.g. server stop) — they are no
        // longer available
        if (event instanceof ModConfigEvent.Unloading) {
            return;
        }
        if (event.getConfig().getSpec() == CLIENT_SPEC) {
            // All gameplay config is server-side
        }

        if (event.getConfig().getSpec() == SERVER_SPEC) {
            debug = DEBUG.get();
            Tweaks.load();
            DistantFire.load();
            Whizz.load();
            Ricochet.load();
            Suppression.loadServer();
            Suppression.loadClient();
            FreeAim.loadServer();
            FreeAim.loadClient();
            Movement.load();
        }
    }
}
