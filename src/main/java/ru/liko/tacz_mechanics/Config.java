package ru.liko.tacz_mechanics;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.config.ModConfigEvent;
import net.neoforged.neoforge.common.ModConfigSpec;

@EventBusSubscriber(modid = TaczMechanics.MODID, bus = EventBusSubscriber.Bus.MOD)
public class Config {
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
        private static final ModConfigSpec.BooleanValue INCLUDE_SHOOTER = SERVER_BUILDER
                .comment(
                    "If true, the shooting player also receives distant fire packets (solo testing; may overlap with normal TaCZ shot sounds)")
                .define("distantFire.includeShooter", false);
        private static final ModConfigSpec.IntValue CLOSE_MAX_DISTANCE = SERVER_BUILDER
                .comment(
                    "Upper bound (blocks) for the \"close\" distant layer (above vanilla TaCZ range). Sent to clients; should stay <= maxDistance for audible far tiers.")
                .defineInRange("distantFire.closeMaxDistance", 100, 16, 1000);
        private static final ModConfigSpec.IntValue MID_MAX_DISTANCE = SERVER_BUILDER
                .comment("Upper bound (blocks) for the \"mid\" distant layer")
                .defineInRange("distantFire.midMaxDistance", 200, 32, 1500);
        private static final ModConfigSpec.IntValue FAR_MAX_DISTANCE = SERVER_BUILDER
                .comment(
                    "Upper bound (blocks) for the \"far\" layer; beyond this uses very_far sound if defined. Prefer maxDistance >= this value.")
                .defineInRange("distantFire.farMaxDistance", 400, 64, 2000);
        private static final ModConfigSpec.IntValue TRANSITION_BLOCKS = SERVER_BUILDER
                .comment("Half-width in blocks for crossfading between distant layers")
                .defineInRange("distantFire.transitionBlocks", 20, 1, 80);
        private static final ModConfigSpec.IntValue NEAR_SOUND_RANGE = SERVER_BUILDER
                .comment(
                    "Within this radius (blocks) from the shot, TaCZ handles the main gunshot tail; distant layer packets are not sent to closer listeners. Also the distance at which low-pass muffle on TaCZ 3P sounds starts.")
                .defineInRange("distantFire.nearSoundRange", 32, 8, 128);
        private static final ModConfigSpec.IntValue ANTI_SPAM_TICKS = SERVER_BUILDER
                .comment(
                    "Minimum server ticks between distant-fire packets for the same listener+shooter pair (reduces stacking on full auto). 0 = off.")
                .defineInRange("distantFire.antiSpamTicks", 2, 0, 40);
        private static final ModConfigSpec.BooleanValue SOUND_PROPAGATION = SERVER_BUILDER
                .comment(
                    "Delay distant-fire playback on the client by distance / sound speed (1 block treated as 1 m). "
                        + "Synced to clients on dedicated servers.")
                .define("distantFire.soundPropagation", true);
        private static final ModConfigSpec.DoubleValue SOUND_SPEED_BLOCKS_PER_SEC = SERVER_BUILDER
                .comment("Effective speed of sound in blocks per second (air ~343). Used only if soundPropagation is true.")
                .defineInRange("distantFire.soundSpeedBlocksPerSecond", 343.0, 1.0, 2000.0);
        private static final ModConfigSpec.IntValue SOUND_PROPAGATION_MAX_DELAY_TICKS = SERVER_BUILDER
                .comment(
                    "Max delay in game ticks before distant fire plays (20 ticks = 1 s). 0 = no cap. "
                        + "Prevents extreme ranges from lagging audio far behind.")
                .defineInRange("distantFire.soundPropagationMaxDelayTicks", 120, 0, 1200);

        public static boolean enabled;
        public static int minDistance;
        public static int maxDistance;
        public static double volumeMultiplier;
        public static boolean includeShooter;
        public static int closeMaxDistance;
        public static int midMaxDistance;
        public static int farMaxDistance;
        public static int transitionBlocks;
        public static int nearSoundRange;
        public static int antiSpamTicks;
        public static boolean soundPropagation;
        public static double soundSpeedBlocksPerSecond;
        public static int soundPropagationMaxDelayTicks;

        private static void load() {
            enabled = ENABLED.get();
            minDistance = MIN_DISTANCE.get();
            maxDistance = MAX_DISTANCE.get();
            volumeMultiplier = VOLUME_MULTIPLIER.get();
            includeShooter = INCLUDE_SHOOTER.get();
            closeMaxDistance = CLOSE_MAX_DISTANCE.get();
            midMaxDistance = MID_MAX_DISTANCE.get();
            farMaxDistance = FAR_MAX_DISTANCE.get();
            transitionBlocks = TRANSITION_BLOCKS.get();
            nearSoundRange = NEAR_SOUND_RANGE.get();
            antiSpamTicks = ANTI_SPAM_TICKS.get();
            soundPropagation = SOUND_PROPAGATION.get();
            soundSpeedBlocksPerSecond = SOUND_SPEED_BLOCKS_PER_SEC.get();
            soundPropagationMaxDelayTicks = SOUND_PROPAGATION_MAX_DELAY_TICKS.get();
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
        private static final ModConfigSpec.DoubleValue MAX_DISTANCE = SERVER_BUILDER
                .comment("Maximum distance (blocks) at which a bullet flying past the player can produce a whizz sound. If the bullet passes farther than this, no sound is played.")
                .defineInRange("whizz.maxDistance", 8.0, 0.5, 64.0);

        public static boolean enabled;
        public static double maxDistance;

        private static void load() {
            enabled = ENABLED.get();
            maxDistance = MAX_DISTANCE.get();
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
        private static final ModConfigSpec.DoubleValue SHAKE_INTENSITY = SERVER_BUILDER
                .comment("Camera shake intensity multiplier (0.0-3.0)")
                .defineInRange("suppression.shakeIntensity", 1.0, 0.0, 3.0);
        private static final ModConfigSpec.DoubleValue SHAKE_SPEED = SERVER_BUILDER
                .comment("Camera shake speed (frequency of shakes)")
                .defineInRange("suppression.shakeSpeed", 1.0, 0.0, 3.0);
        private static final ModConfigSpec.DoubleValue DECAY_RATE = SERVER_BUILDER
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

        private static void load() {
            enabled = ENABLED.get();
            detectionRadius = DETECTION_RADIUS.get();
            flybyIntensity = FLYBY_INTENSITY.get();
            impactIntensityMultiplier = IMPACT_INTENSITY_MULTIPLIER.get();
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
                .comment("Speed multiplier applied after ricochet (real-world: ~35% loss = 0.65)")
                .defineInRange("ricochet.speedMultiplier", 0.65, 0.1, 1.0);
        private static final ModConfigSpec.DoubleValue FLATTEN_REFLECTION = SERVER_BUILDER
                .comment("How much the reflection flattens along the surface (0=perfect mirror, 0.2=realistic)")
                .defineInRange("ricochet.flattenReflection", 0.15, 0.0, 0.5);
        private static final ModConfigSpec.DoubleValue CHANCE = SERVER_BUILDER
                .comment("Chance of ricochet when conditions are met (0.0-1.0)")
                .defineInRange("ricochet.chance", 0.5, 0.0, 1.0);

        public static boolean enabled;
        public static boolean demoPreset;
        public static boolean debug;
        public static double minSpeed;
        public static double minAngle;
        public static int maxBounces;
        public static double speedMultiplier;
        public static double flattenReflection;
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
                flattenReflection = 0.0;
                chance = 1.0;
            } else {
                minSpeed = MIN_SPEED.get();
                minAngle = MIN_ANGLE.get();
                maxBounces = MAX_BOUNCES.get();
                speedMultiplier = SPEED_MULTIPLIER.get();
                flattenReflection = FLATTEN_REFLECTION.get();
                chance = CHANCE.get();
            }
        }

        static void init() {
        }

        private Ricochet() {
        }
    }

    public static final class Pierce {
        private static final ModConfigSpec.BooleanValue ENABLED = SERVER_BUILDER
                .comment("Enable bullet block penetration (pierce) system")
                .define("pierce.enabled", true);
        private static final ModConfigSpec.BooleanValue DEBUG = SERVER_BUILDER
                .comment("Enable debug logging for pierce decisions")
                .define("pierce.debug", false);
        private static final ModConfigSpec.IntValue MAX_PIERCES = SERVER_BUILDER
                .comment("Maximum number of blocks a single bullet can pierce. 0 = unlimited.")
                .defineInRange("pierce.maxPierces", 4, 0, 64);
        private static final ModConfigSpec.DoubleValue MIN_SPEED = SERVER_BUILDER
                .comment("Minimum bullet speed required to attempt block pierce")
                .defineInRange("pierce.minSpeed", 0.5, 0.0, 100.0);
        private static final ModConfigSpec.DoubleValue THIN_BLOCK_MAX_THICKNESS = SERVER_BUILDER
                .comment("Pierce any block, even one without a bullet_interactions entry, when the collision material along the bullet path is at most this thick (in blocks).",
                        "0.3 covers fences, fence gates, iron bars, panes, doors, trapdoors, plates and thinner; slabs, stairs and full blocks stay solid. 0 disables this fallback.",
                        "Пробивать любой блок, даже без записи в bullet_interactions, если толщина коллизии по траектории пули не больше этого значения (в блоках).",
                        "0.3 покрывает ограды, калитки, решётки, стеклянные панели, двери, люки, нажимные плиты и всё тоньше; полублоки, ступени и целые блоки остаются непробиваемыми. 0 отключает.")
                .defineInRange("pierce.thinBlockMaxThickness", 0.3, 0.0, 1.0);
        private static final ModConfigSpec.DoubleValue THIN_BLOCK_DAMAGE_MULTIPLIER = SERVER_BUILDER
                .comment("Damage kept after piercing a thin block via the thickness fallback.",
                        "Сколько урона остаётся после пробития тонкого блока по правилу толщины.")
                .defineInRange("pierce.thinBlockDamageMultiplier", 0.9, 0.0, 1.0);
        private static final ModConfigSpec.DoubleValue THIN_BLOCK_SPEED_MULTIPLIER = SERVER_BUILDER
                .comment("Speed kept after piercing a thin block via the thickness fallback.",
                        "Сколько скорости остаётся после пробития тонкого блока по правилу толщины.")
                .defineInRange("pierce.thinBlockSpeedMultiplier", 0.9, 0.05, 1.0);

        public static boolean enabled;
        public static boolean debug;
        public static int maxPierces;
        public static double minSpeed;
        public static double thinBlockMaxThickness;
        public static double thinBlockDamageMultiplier;
        public static double thinBlockSpeedMultiplier;

        private static void load() {
            enabled = ENABLED.get();
            debug = DEBUG.get();
            maxPierces = MAX_PIERCES.get();
            minSpeed = MIN_SPEED.get();
            thinBlockMaxThickness = THIN_BLOCK_MAX_THICKNESS.get();
            thinBlockDamageMultiplier = THIN_BLOCK_DAMAGE_MULTIPLIER.get();
            thinBlockSpeedMultiplier = THIN_BLOCK_SPEED_MULTIPLIER.get();
        }

        static void init() {
        }

        private Pierce() {
        }
    }

    public static final class FreeAim {
        private static final ModConfigSpec.BooleanValue ENABLED = SERVER_BUILDER
                .comment("Master switch for free aim. The gun sways with spring physics driven by camera turning, movement and recoil.",
                        "Главный выключатель free aim. Оружие качается по пружинной физике от поворота камеры, движения и отдачи.")
                .define("freeAim.enabled", true);
        private static final ModConfigSpec.DoubleValue MAX_ANGLE = SERVER_BUILDER
                .comment("Maximum angle (degrees) the gun barrel can deviate from the view center. Also the hard clamp for the sway.",
                        "Максимальный угол (градусы) отклонения ствола от центра экрана. Это же — жёсткий предел качания.")
                .defineInRange("freeAim.maxAngle", 4.0, 0.5, 25.0);
        private static final ModConfigSpec.DoubleValue STIFFNESS = SERVER_BUILDER
                .comment("Spring stiffness: how strongly the gun is pulled back to center. Higher = snappier return, less float.",
                        "Жёсткость пружины: как сильно оружие тянет обратно в центр. Больше = резче возврат, меньше «плавания».")
                .defineInRange("freeAim.spring.stiffness", 0.4, 0.01, 1.0);
        private static final ModConfigSpec.DoubleValue DAMPING = SERVER_BUILDER
                .comment("Spring damping: resistance to motion. Higher = less overshoot/wobble; lower = more bouncy, lively sway.",
                        "Демпфирование пружины: сопротивление движению. Больше = меньше перелёта/дрожи; меньше = живее, с отскоком.")
                .defineInRange("freeAim.spring.damping", 0.9, 0.05, 3.0);
        private static final ModConfigSpec.DoubleValue LOOK_SENSITIVITY = SERVER_BUILDER
                .comment("How strongly camera rotation pushes the gun aside (impulse per degree of mouse turn). Higher = more lag on turns.",
                        "Насколько сильно поворот камеры уводит оружие в сторону (импульс на градус поворота мыши). Больше = сильнее занос при поворотах.")
                .defineInRange("freeAim.look.sensitivity", 0.6, 0.0, 5.0);
        private static final ModConfigSpec.DoubleValue LOOK_SMOOTHING = SERVER_BUILDER
                .comment("Spreads a mouse flick over several ticks instead of one hit. 0 = raw and twitchy, 0.9 = very smooth and laggy.",
                        "Растягивает рывок мыши на несколько тиков вместо одного удара. 0 = сыро и дёргано, 0.9 = очень плавно и с задержкой.")
                .defineInRange("freeAim.look.smoothing", 0.6, 0.0, 0.9);
        private static final ModConfigSpec.DoubleValue ADS_MULTIPLIER = SERVER_BUILDER
                .comment("Sway multiplier while aiming down sights. 0 = fully steady in ADS, 1 = same sway as from the hip.",
                        "Множитель качания при прицеливании (ADS). 0 = полностью неподвижно в прицеле, 1 = как от бедра.")
                .defineInRange("freeAim.adsMultiplier", 0.35, 0.0, 1.0);
        private static final ModConfigSpec.DoubleValue CROSSHAIR_SCALE = SERVER_BUILDER
                .comment("Pixels the crosshair shifts per degree of sway, so the reticle follows where the barrel actually points.",
                        "На сколько пикселей смещается прицел на градус качания, чтобы сетка следовала за реальным направлением ствола.")
                .defineInRange("freeAim.crosshairScale", 10.0, 1.0, 50.0);
        private static final ModConfigSpec.BooleanValue DISABLE_CROSSHAIR_MOVEMENT = SERVER_BUILDER
                .comment("If true, the crosshair stays centered and does not follow the gun sway (gun model still moves).",
                        "Если true, прицел остаётся в центре и не следует за качанием оружия (сама модель всё равно двигается).")
                .define("freeAim.disableCrosshairMovement", false);
        private static final ModConfigSpec.BooleanValue RECOIL_ENABLED = SERVER_BUILDER
                .comment("Add an extra upward kick to the gun model on each shot (on top of TaCZ's own camera recoil).",
                        "Добавлять дополнительный подброс модели оружия при каждом выстреле (поверх отдачи камеры самого TaCZ).")
                .define("freeAim.recoil.enabled", true);
        private static final ModConfigSpec.DoubleValue RECOIL_SCALE = SERVER_BUILDER
                .comment("Strength of the recoil kick per shot. Multiplied by the gun's own TaCZ recoil data, so a rifle kicks harder than an SMG.",
                        "Сила подброса отдачи за выстрел. Умножается на собственные данные отдачи ствола из TaCZ, поэтому винтовка бьёт сильнее ПП.")
                .defineInRange("freeAim.recoil.scale", 2.0, 0.0, 10.0);
        private static final ModConfigSpec.DoubleValue RECOIL_STIFFNESS = SERVER_BUILDER
                .comment("Stiffness of the separate recoil spring. Higher = the kick snaps back to center faster (punchier, shorter).",
                        "Жёсткость отдельной пружины отдачи. Больше = подброс быстрее возвращается в центр (резче и короче).")
                .defineInRange("freeAim.recoil.stiffness", 0.9, 0.05, 3.0);
        private static final ModConfigSpec.DoubleValue RECOIL_DAMPING = SERVER_BUILDER
                .comment("Damping of the recoil spring. Higher = no wobble after the kick; lower = the muzzle bounces a couple of times.",
                        "Демпфирование пружины отдачи. Больше = после подброса нет дрожи; меньше = ствол пару раз качнётся.")
                .defineInRange("freeAim.recoil.damping", 1.5, 0.05, 4.0);
        private static final ModConfigSpec.DoubleValue RECOIL_KICKBACK = SERVER_BUILDER
                .comment("How far the gun model is pushed back toward the camera per degree of recoil kick. This is what reads as 'punch' on screen.",
                        "Насколько модель уходит назад к камере на градус подброса. Именно это читается на экране как «удар».")
                .defineInRange("freeAim.recoil.kickback", 0.03, -0.2, 0.2);
        private static final ModConfigSpec.DoubleValue RECOIL_ROLL = SERVER_BUILDER
                .comment("Degrees the gun model rolls per degree of horizontal recoil kick. Adds a twist to the shot instead of a flat rise.",
                        "На сколько градусов модель кренится на градус горизонтального кика. Добавляет выстрелу «закрутку» вместо плоского подъёма.")
                .defineInRange("freeAim.recoil.roll", 1.0, 0.0, 5.0);
        private static final ModConfigSpec.BooleanValue MOVEMENT_ENABLED = SERVER_BUILDER
                .comment("Add gun sway from walking, sprinting and jumping/landing.",
                        "Добавлять качание оружия от ходьбы, бега и прыжков/приземления.")
                .define("freeAim.movement.enabled", true);
        private static final ModConfigSpec.DoubleValue MOVEMENT_WALK_SCALE = SERVER_BUILDER
                .comment("Sway amplitude while walking. Higher = more pronounced bob on the gun.",
                        "Амплитуда качания при ходьбе. Больше = заметнее покачивание оружия.")
                .defineInRange("freeAim.movement.walkScale", 0.15, 0.0, 5.0);
        private static final ModConfigSpec.DoubleValue MOVEMENT_SPRINT_SCALE = SERVER_BUILDER
                .comment("Sway amplitude while sprinting. Usually larger than walkScale for a heavier run feel.",
                        "Амплитуда качания при беге (спринте). Обычно больше walkScale — для ощущения тяжёлого бега.")
                .defineInRange("freeAim.movement.sprintScale", 0.35, 0.0, 5.0);
        private static final ModConfigSpec.DoubleValue MOVEMENT_JUMP_SCALE = SERVER_BUILDER
                .comment("Impulse applied on jump (barrel dips) and on landing (barrel kicks up). Higher = stronger jolt.",
                        "Импульс при прыжке (ствол ныряет вниз) и при приземлении (ствол подбрасывает вверх). Больше = резче толчок.")
                .defineInRange("freeAim.movement.jumpScale", 1.2, 0.0, 10.0);
        private static final ModConfigSpec.BooleanValue THIRD_PERSON_ENABLED = SERVER_BUILDER
                .comment("Show the free-aim sway on other players' guns in third person (synced over the network).",
                        "Показывать качание free aim на оружии других игроков в третьем лице (синхронизируется по сети).")
                .define("freeAim.thirdPerson.enabled", true);
        private static final ModConfigSpec.BooleanValue TREMOR_ENABLED = SERVER_BUILDER
                .comment("Add hand-tremor jitter to the gun while aiming down sights (arm fatigue).",
                        "Добавлять дрожь оружия при прицеливании (ADS) — имитация усталости руки.")
                .define("freeAim.tremor.enabled", true);
        private static final ModConfigSpec.DoubleValue TREMOR_SCALE = SERVER_BUILDER
                .comment("Tremor amplitude in DEGREES at full fatigue with iron sights. A scope magnifies this on screen.",
                        "Амплитуда дрожи в ГРАДУСАХ при полной усталости с открытым прицелом. Оптика увеличивает её на экране.")
                .defineInRange("freeAim.tremor.scale", 0.3, 0.0, 5.0);
        private static final ModConfigSpec.DoubleValue TREMOR_ZOOM_SCALE = SERVER_BUILDER
                .comment("Extra tremor per point of scope magnification, on top of the magnification itself.",
                        "Добавка к дрожи на каждую единицу кратности прицела — сверх самого увеличения оптики.",
                        "0 = a 8x scope shakes as much as iron sights in world angle.",
                        "0 = 8-кратная оптика дрожит так же, как открытый прицел (по углу в мире).")
                .defineInRange("freeAim.tremor.zoomScale", 0.15, 0.0, 2.0);
        private static final ModConfigSpec.IntValue TREMOR_BREATH_TICKS = SERVER_BUILDER
                .comment("Ticks per breath cycle — the slow rise and fall the sights ride on. 20 ticks = 1 second.",
                        "Тиков на один цикл дыхания — медленный подъём и спад, на котором ходит прицел. 20 тиков = 1 секунда.")
                .defineInRange("freeAim.tremor.breathTicks", 80, 10, 400);
        private static final ModConfigSpec.IntValue TREMOR_BUILDUP_TICKS = SERVER_BUILDER
                .comment("How many ticks of continuous ADS it takes for the sway to reach full strength.",
                        "Сколько тиков непрерывного прицеливания нужно, чтобы качание достигло полной силы.")
                .defineInRange("freeAim.tremor.buildupTicks", 60, 1, 1200);
        private static final ModConfigSpec.BooleanValue BREATH_ENABLED = SERVER_BUILDER
                .comment("Hold the sneak key while aiming to hold your breath and steady the sights.",
                        "Держать клавишу приседания при прицеливании, чтобы задержать дыхание и стабилизировать прицел.")
                .define("freeAim.breath.enabled", true);
        private static final ModConfigSpec.IntValue BREATH_HOLD_TICKS = SERVER_BUILDER
                .comment("How many ticks of breath the player has. 20 ticks = 1 second.",
                        "Сколько тиков дыхания есть у игрока. 20 тиков = 1 секунда.")
                .defineInRange("freeAim.breath.holdTicks", 100, 1, 600);
        private static final ModConfigSpec.IntValue BREATH_RECOVER_TICKS = SERVER_BUILDER
                .comment("Ticks needed to refill the breath bar from empty. Larger than holdTicks = recovery costs more than the hold.",
                        "Сколько тиков нужно, чтобы восстановить дыхание с нуля. Больше holdTicks = восстановление дороже задержки.")
                .defineInRange("freeAim.breath.recoverTicks", 160, 1, 1200);
        private static final ModConfigSpec.DoubleValue BREATH_STEADINESS = SERVER_BUILDER
                .comment("Tremor multiplier while the breath is held. 0 = perfectly still, 1 = holding breath does nothing.",
                        "Множитель дрожи при задержке дыхания. 0 = полная неподвижность, 1 = задержка ничего не даёт.")
                .defineInRange("freeAim.breath.steadiness", 0.15, 0.0, 1.0);
        private static final ModConfigSpec.DoubleValue BREATH_EXHAUSTED_TREMOR = SERVER_BUILDER
                .comment("Tremor multiplier after the breath runs out, until half the bar has refilled. Above 1 = worse than normal.",
                        "Множитель дрожи после того, как дыхание кончилось, пока не восстановится половина шкалы. Больше 1 = хуже обычного.")
                .defineInRange("freeAim.breath.exhaustedTremor", 1.8, 0.0, 5.0);
        private static final ModConfigSpec.IntValue BREATH_SETTLE_TICKS = SERVER_BUILDER
                .comment("Ticks it takes to settle into (and out of) a held breath. Lower = snappier, higher = more gradual.",
                        "За сколько тиков прицел успокаивается при задержке дыхания (и возвращается обратно). Меньше = резче.")
                .defineInRange("freeAim.breath.settleTicks", 8, 1, 100);

        public static boolean enabled;
        public static double maxAngle;
        public static double stiffness;
        public static double damping;
        public static double lookSensitivity;
        public static double lookSmoothing;
        public static double adsMultiplier;
        public static double crosshairScale;
        public static boolean disableCrosshairMovement;
        public static boolean recoilEnabled;
        public static double recoilScale;
        public static double recoilStiffness;
        public static double recoilDamping;
        public static double recoilKickback;
        public static double recoilRoll;
        public static boolean movementEnabled;
        public static double movementWalkScale;
        public static double movementSprintScale;
        public static double movementJumpScale;
        public static boolean thirdPersonEnabled;
        public static boolean tremorEnabled;
        public static double tremorScale;
        public static double tremorZoomScale;
        public static int tremorBreathTicks;
        public static int tremorBuildupTicks;
        public static boolean breathEnabled;
        public static int breathHoldTicks;
        public static int breathRecoverTicks;
        public static double breathSteadiness;
        public static double breathExhaustedTremor;
        public static int breathSettleTicks;

        private static void load() {
            enabled = ENABLED.get();
            maxAngle = MAX_ANGLE.get();
            stiffness = STIFFNESS.get();
            damping = DAMPING.get();
            lookSensitivity = LOOK_SENSITIVITY.get();
            lookSmoothing = LOOK_SMOOTHING.get();
            adsMultiplier = ADS_MULTIPLIER.get();
            crosshairScale = CROSSHAIR_SCALE.get();
            disableCrosshairMovement = DISABLE_CROSSHAIR_MOVEMENT.get();
            recoilEnabled = RECOIL_ENABLED.get();
            recoilScale = RECOIL_SCALE.get();
            recoilStiffness = RECOIL_STIFFNESS.get();
            recoilDamping = RECOIL_DAMPING.get();
            recoilKickback = RECOIL_KICKBACK.get();
            recoilRoll = RECOIL_ROLL.get();
            movementEnabled = MOVEMENT_ENABLED.get();
            movementWalkScale = MOVEMENT_WALK_SCALE.get();
            movementSprintScale = MOVEMENT_SPRINT_SCALE.get();
            movementJumpScale = MOVEMENT_JUMP_SCALE.get();
            thirdPersonEnabled = THIRD_PERSON_ENABLED.get();
            tremorEnabled = TREMOR_ENABLED.get();
            tremorScale = TREMOR_SCALE.get();
            tremorZoomScale = TREMOR_ZOOM_SCALE.get();
            tremorBreathTicks = TREMOR_BREATH_TICKS.get();
            tremorBuildupTicks = TREMOR_BUILDUP_TICKS.get();
            breathEnabled = BREATH_ENABLED.get();
            breathHoldTicks = BREATH_HOLD_TICKS.get();
            breathRecoverTicks = BREATH_RECOVER_TICKS.get();
            breathSteadiness = BREATH_STEADINESS.get();
            breathExhaustedTremor = BREATH_EXHAUSTED_TREMOR.get();
            breathSettleTicks = BREATH_SETTLE_TICKS.get();
        }

        static void init() {
        }

        private FreeAim() {
        }
    }

    public static final class Movement {
        private static final ModConfigSpec.BooleanValue ENABLED = SERVER_BUILDER
                .comment("Enable advanced movement mechanics (leaning).",
                        "Включить продвинутую механику движения (наклон).")
                .define("movement.enabled", true);
        private static final ModConfigSpec.BooleanValue LEAN_AUTO_HOLD = SERVER_BUILDER
                .comment("Auto-hold lean position (toggle mode): tap to lean and stay, tap again to return.",
                        "Удерживать наклон автоматически (режим переключения): нажал — наклонился и держишь, нажал снова — вернулся.")
                .define("movement.leanAutoHold", false);
        private static final ModConfigSpec.BooleanValue LEAN_MOUSE_CORRECTION = SERVER_BUILDER
                .comment("Correct mouse input while leaning so the crosshair stays on target.",
                        "Корректировать ввод мыши при наклоне, чтобы прицел оставался на цели.")
                .define("movement.leanMouseCorrection", true);
        private static final ModConfigSpec.DoubleValue LEAN_COOLDOWN = SERVER_BUILDER
                .comment("Cooldown between lean actions (seconds).",
                        "Задержка между действиями наклона (секунды).")
                .defineInRange("movement.leanCooldown", 0.0, 0.0, 5.0);
        private static final ModConfigSpec.DoubleValue LEAN_HAND_ROLL = SERVER_BUILDER
                .comment("Degrees the first-person hand/gun tilts at full lean. 0 = hand stays locked to the camera.",
                        "На сколько градусов наклоняется рука/оружие от первого лица при полном наклоне. 0 = рука жёстко привязана к камере.")
                .defineInRange("movement.leanHandRoll", 14.0, -30.0, 30.0);
        private static final ModConfigSpec.DoubleValue LEAN_HAND_OFFSET = SERVER_BUILDER
                .comment("Sideways shift of the first-person hand/gun at full lean, in blocks. 0 = no shift.",
                        "Боковое смещение руки/оружия от первого лица при полном наклоне, в блоках. 0 = без смещения.")
                .defineInRange("movement.leanHandOffset", 0.12, -1.0, 1.0);
        private static final ModConfigSpec.DoubleValue LEAN_DISTANCE = SERVER_BUILDER
                .comment("How far sideways (blocks) a full lean moves the eye.",
                        "Everything else follows from this one number: the body tilts by exactly the",
                        "angle needed to carry the eye that far, and the hitbox tilts with it.",
                        "Lower it if the model leans further than you want -- 0.6 needs about 22 degrees.",
                        "На сколько блоков вбок полный наклон смещает глаз.",
                        "Всё остальное выводится из этого числа: тело кренится ровно на тот угол,",
                        "который нужен, чтобы унести глаз на это расстояние, а хитбокс кренится вместе с ним.",
                        "Уменьшите, если модель наклоняется сильнее, чем хочется — 0.6 требует около 22 градусов.")
                .defineInRange("movement.leanDistance", 0.6, 0.0, 2.0);
        private static final ModConfigSpec.BooleanValue LEAN_BODY_ROLL = SERVER_BUILDER
                .comment("Tilt the whole third-person body when leaning, instead of only bending a leg.",
                        "This is what makes leaning actually expose you to fire: the hitbox leans with the model.",
                        "Off keeps the original look, where leaning moves only the camera and one leg.",
                        "Наклонять всё тело в третьем лице при наклоне, а не только сгибать ногу.",
                        "Именно это заставляет наклон реально подставлять под огонь: хитбокс наклоняется вместе с моделью.",
                        "Выключено — прежний вид, где наклон двигает только камеру и одну ногу.")
                .define("movement.leanBodyRoll", true);
        private static final ModConfigSpec.BooleanValue SIT_ENABLED = SERVER_BUILDER
                .comment("Enable the sit/slide posture (C by default).",
                        "Включить позу sit/слайд (по умолчанию C).")
                .define("movement.sitEnabled", true);
        private static final ModConfigSpec.BooleanValue PRONE_ENABLED = SERVER_BUILDER
                .comment("Enable the prone/crawl posture (Z by default).",
                        "Включить позу prone/лёжа (по умолчанию Z).")
                .define("movement.proneEnabled", true);
        private static final ModConfigSpec.DoubleValue SIT_COOLDOWN = SERVER_BUILDER
                .comment("Cooldown between sit toggles (seconds).",
                        "Задержка между переключениями sit (секунды).")
                .defineInRange("movement.sitCooldown", 0.75, 0.0, 5.0);
        private static final ModConfigSpec.DoubleValue PRONE_COOLDOWN = SERVER_BUILDER
                .comment("Cooldown between prone toggles (seconds).",
                        "Задержка между переключениями prone (секунды).")
                .defineInRange("movement.proneCooldown", 0.75, 0.0, 5.0);
        private static final ModConfigSpec.BooleanValue SLIDE_ENABLED = SERVER_BUILDER
                .comment("Slide forward when entering sit while sprinting.",
                        "Скользить вперёд при входе в sit во время спринта.")
                .define("movement.slideEnabled", true);
        private static final ModConfigSpec.DoubleValue SLIDE_MAX_FORCE = SERVER_BUILDER
                .comment("Initial slide force at full sprint charge.",
                        "Начальная сила слайда при полном заряде спринта.")
                .defineInRange("movement.slideMaxForce", 1.0, 0.0, 3.0);
        private static final ModConfigSpec.DoubleValue SLIDE_DECAY = SERVER_BUILDER
                .comment("How much slide force is lost per tick on the ground.",
                        "На сколько падает сила слайда за тик на земле.")
                .defineInRange("movement.slideDecay", 0.1, 0.01, 1.0);
        private static final ModConfigSpec.BooleanValue DIVE_ENABLED = SERVER_BUILDER
                .comment("Dive forward+up when entering prone while sprinting.",
                        "Нырок вперёд+вверх при входе в prone во время спринта.")
                .define("movement.diveEnabled", true);
        private static final ModConfigSpec.BooleanValue PRONE_VIEW_CLAMP = SERVER_BUILDER
                .comment("Limit how far you can turn while prone.",
                        "Ограничить угол поворота в позе prone.")
                .define("movement.proneViewClamp", true);
        private static final ModConfigSpec.DoubleValue PRONE_VIEW_ANGLE = SERVER_BUILDER
                .comment("Max yaw (degrees) either side of the prone facing direction.",
                        "Максимальный поворот (градусы) в каждую сторону от направления prone.")
                .defineInRange("movement.proneViewAngle", 100.0, 10.0, 180.0);
        private static final ModConfigSpec.BooleanValue MUFFLE_STEPS = SERVER_BUILDER
                .comment("Silence footstep sounds while sitting or prone.",
                        "Глушить звук шагов в позе sit или prone.")
                .define("movement.muffleSteps", true);
        private static final ModConfigSpec.DoubleValue SIT_SPEED = SERVER_BUILDER
                .comment("Movement input multiplier while sitting.",
                        "Множитель ввода движения в позе sit.")
                .defineInRange("movement.sitSpeed", 0.3, 0.0, 1.0);
        private static final ModConfigSpec.DoubleValue PRONE_SPEED = SERVER_BUILDER
                .comment("Movement input multiplier while prone.",
                        "Множитель ввода движения в позе prone.")
                .defineInRange("movement.proneSpeed", 0.4, 0.0, 1.0);
        private static final ModConfigSpec.BooleanValue DEBUG = SERVER_BUILDER
                .comment("Log the player's actual hitbox (bounding box, dimensions, eye position) on both client and server.",
                        "Логировать фактический хитбокс игрока (бокс, габариты, позицию глаз) на клиенте и сервере.")
                .define("movement.debug", false);

        public static boolean enabled;
        public static boolean leanAutoHold;
        public static boolean leanMouseCorrection;
        public static double leanCooldown;
        public static double leanHandRoll;
        public static double leanHandOffset;
        public static double leanDistance;
        public static boolean leanBodyRoll;
        public static boolean sitEnabled;
        public static boolean proneEnabled;
        public static double sitCooldown;
        public static double proneCooldown;
        public static boolean slideEnabled;
        public static double slideMaxForce;
        public static double slideDecay;
        public static boolean diveEnabled;
        public static boolean proneViewClamp;
        public static double proneViewAngle;
        public static boolean muffleSteps;
        public static double sitSpeed;
        public static double proneSpeed;
        public static boolean debug;

        private static void load() {
            enabled = ENABLED.get();
            leanAutoHold = LEAN_AUTO_HOLD.get();
            leanMouseCorrection = LEAN_MOUSE_CORRECTION.get();
            leanCooldown = LEAN_COOLDOWN.get();
            leanHandRoll = LEAN_HAND_ROLL.get();
            leanHandOffset = LEAN_HAND_OFFSET.get();
            leanDistance = LEAN_DISTANCE.get();
            leanBodyRoll = LEAN_BODY_ROLL.get();
            sitEnabled = SIT_ENABLED.get();
            proneEnabled = PRONE_ENABLED.get();
            sitCooldown = SIT_COOLDOWN.get();
            proneCooldown = PRONE_COOLDOWN.get();
            slideEnabled = SLIDE_ENABLED.get();
            slideMaxForce = SLIDE_MAX_FORCE.get();
            slideDecay = SLIDE_DECAY.get();
            diveEnabled = DIVE_ENABLED.get();
            proneViewClamp = PRONE_VIEW_CLAMP.get();
            proneViewAngle = PRONE_VIEW_ANGLE.get();
            muffleSteps = MUFFLE_STEPS.get();
            sitSpeed = SIT_SPEED.get();
            proneSpeed = PRONE_SPEED.get();
            debug = DEBUG.get();
        }

        static void init() {
        }

        private Movement() {
        }
    }

    public static final class GunLights {
        private static final ModConfigSpec.BooleanValue ENABLED = SERVER_BUILDER
                .comment("Enable dynamic Minecraft block light from gunshots and tracer bullets (client-side visual).",
                        "Включить динамический майнкрафтовский свет от выстрелов и трассеров (клиентский визуал).")
                .define("gunLights.enabled", true);
        private static final ModConfigSpec.BooleanValue MUZZLE_FLASH_ENABLED = SERVER_BUILDER
                .comment("Emit a short light flash at the muzzle on every shot.",
                        "Излучать короткую вспышку света у дула при каждом выстреле.")
                .define("gunLights.muzzleFlash.enabled", true);
        private static final ModConfigSpec.IntValue MUZZLE_LIGHT_LEVEL = SERVER_BUILDER
                .comment("Light level of an unsuppressed muzzle flash (1-15).",
                        "Уровень света вспышки без глушителя (1-15).")
                .defineInRange("gunLights.muzzleFlash.lightLevel", 14, 1, 15);
        private static final ModConfigSpec.IntValue SILENCED_LIGHT_LEVEL = SERVER_BUILDER
                .comment("Light level when a silencer is attached (0 = no light).",
                        "Уровень света с установленным глушителем (0 = без света).")
                .defineInRange("gunLights.muzzleFlash.silencedLightLevel", 7, 0, 15);
        private static final ModConfigSpec.IntValue MUZZLE_DURATION_TICKS = SERVER_BUILDER
                .comment("How many client ticks the muzzle flash stays at full brightness (20 ticks = 1 s).",
                        "Сколько клиентских тиков вспышка горит на полной яркости (20 тиков = 1 с).")
                .defineInRange("gunLights.muzzleFlash.durationTicks", 2, 1, 20);
        private static final ModConfigSpec.IntValue MUZZLE_FADE_PER_TICK = SERVER_BUILDER
                .comment("Light levels lost per tick after durationTicks (smooth fade-out). 0 = vanish instantly.",
                        "На сколько уровней свет гаснет за тик после durationTicks (плавное затухание). 0 = гаснет мгновенно.")
                .defineInRange("gunLights.muzzleFlash.fadePerTick", 4, 0, 15);
        private static final ModConfigSpec.BooleanValue TRACER_ENABLED = SERVER_BUILDER
                .comment("Emit trail light along bullets in flight.",
                        "Излучать свет вдоль летящих пуль.")
                .define("gunLights.tracer.enabled", true);
        private static final ModConfigSpec.BooleanValue TRACER_AMMO_ONLY = SERVER_BUILDER
                .comment("Only light up bullets that are actual tracer ammo. If false, all bullets glow.",
                        "Подсвечивать только трассирующие патроны. Если false — светятся все пули.")
                .define("gunLights.tracer.tracerAmmoOnly", true);
        private static final ModConfigSpec.IntValue TRACER_LIGHT_LEVEL = SERVER_BUILDER
                .comment("Light level of each trail spark (1-15).",
                        "Уровень света каждой точки следа (1-15).")
                .defineInRange("gunLights.tracer.lightLevel", 10, 1, 15);
        private static final ModConfigSpec.IntValue TRACER_TTL_TICKS = SERVER_BUILDER
                .comment("How many ticks each trail spark stays at full brightness.",
                        "Сколько тиков каждая точка следа горит на полной яркости.")
                .defineInRange("gunLights.tracer.ttlTicks", 3, 1, 20);
        private static final ModConfigSpec.IntValue TRACER_FADE_PER_TICK = SERVER_BUILDER
                .comment("Light levels lost per tick after ttlTicks (comet-tail fade). 0 = vanish instantly.",
                        "На сколько уровней свет гаснет за тик после ttlTicks (хвост кометы). 0 = гаснет мгновенно.")
                .defineInRange("gunLights.tracer.fadePerTick", 3, 0, 15);
        private static final ModConfigSpec.DoubleValue TRACER_STEP_BLOCKS = SERVER_BUILDER
                .comment("Distance in blocks between consecutive trail sparks.",
                        "Расстояние в блоках между соседними точками следа.")
                .defineInRange("gunLights.tracer.stepBlocks", 3.0, 0.5, 16.0);

        public static boolean enabled;
        public static boolean muzzleFlashEnabled;
        public static int muzzleLightLevel;
        public static int silencedLightLevel;
        public static int muzzleDurationTicks;
        public static int muzzleFadePerTick;
        public static boolean tracerEnabled;
        public static boolean tracerAmmoOnly;
        public static int tracerLightLevel;
        public static int tracerTtlTicks;
        public static int tracerFadePerTick;
        public static double tracerStepBlocks;

        private static void load() {
            enabled = ENABLED.get();
            muzzleFlashEnabled = MUZZLE_FLASH_ENABLED.get();
            muzzleLightLevel = MUZZLE_LIGHT_LEVEL.get();
            silencedLightLevel = SILENCED_LIGHT_LEVEL.get();
            muzzleDurationTicks = MUZZLE_DURATION_TICKS.get();
            muzzleFadePerTick = MUZZLE_FADE_PER_TICK.get();
            tracerEnabled = TRACER_ENABLED.get();
            tracerAmmoOnly = TRACER_AMMO_ONLY.get();
            tracerLightLevel = TRACER_LIGHT_LEVEL.get();
            tracerTtlTicks = TRACER_TTL_TICKS.get();
            tracerFadePerTick = TRACER_FADE_PER_TICK.get();
            tracerStepBlocks = TRACER_STEP_BLOCKS.get();
        }

        static void init() {
        }

        private GunLights() {
        }
    }

    public static final class ScopeFlare {
        private static final ModConfigSpec.BooleanValue ENABLED = SERVER_BUILDER
                .comment("Enable scope flare effect")
                .define("scopeFlare.enabled", true);
        private static final ModConfigSpec.DoubleValue MIN_ZOOM = SERVER_BUILDER
                .comment("Minimum zoom level for a scope to produce a flare. Scopes with zoom greater than this will have a flare.")
                .defineInRange("scopeFlare.minZoom", 1.5, 1.0, 20.0);
        private static final ModConfigSpec.ConfigValue<java.util.List<? extends String>> WHITELISTED_SCOPES = SERVER_BUILDER
                .comment("List of specific scope IDs that will ALWAYS produce a flare, regardless of their zoom level. (e.g. \"tacz:scope_acog_ta31\")")
                .defineList("scopeFlare.whitelistedScopes", java.util.List.of(), obj -> obj instanceof String);
        private static final ModConfigSpec.ConfigValue<java.util.List<? extends String>> BLACKLISTED_SCOPES = SERVER_BUILDER
                .comment("List of specific scope IDs that will NEVER produce a flare, regardless of zoom or whitelist. (e.g. \"tacz:scope_acog_ta31\")")
                .defineList("scopeFlare.blacklistedScopes", java.util.List.of(), obj -> obj instanceof String);
        private static final ModConfigSpec.DoubleValue FORWARD_OFFSET = SERVER_BUILDER
                .comment("Distance (blocks) to render the flare in front of the player's eyes.")
                .defineInRange("scopeFlare.forwardOffset", 0.6, 0.0, 5.0);
        private static final ModConfigSpec.DoubleValue FADE_MIN_DISTANCE = SERVER_BUILDER
                .comment("Distance (blocks) at which the flare starts fading out when getting closer to the player.")
                .defineInRange("scopeFlare.fadeMinDistance", 3.0, 0.0, 50.0);
        private static final ModConfigSpec.DoubleValue FADE_MAX_DISTANCE = SERVER_BUILDER
                .comment("Distance (blocks) at which the flare is fully visible. Between min and max it will smoothly fade.")
                .defineInRange("scopeFlare.fadeMaxDistance", 10.0, 1.0, 100.0);

        public static boolean enabled;
        public static double minZoom;
        public static java.util.List<? extends String> whitelistedScopes;
        public static java.util.List<? extends String> blacklistedScopes;
        public static double forwardOffset;
        public static double fadeMinDistance;
        public static double fadeMaxDistance;

        private static void load() {
            enabled = ENABLED.get();
            minZoom = MIN_ZOOM.get();
            whitelistedScopes = WHITELISTED_SCOPES.get();
            blacklistedScopes = BLACKLISTED_SCOPES.get();
            forwardOffset = FORWARD_OFFSET.get();
            fadeMinDistance = FADE_MIN_DISTANCE.get();
            fadeMaxDistance = Math.max(FADE_MAX_DISTANCE.get(), fadeMinDistance + 0.1);
        }

        static void init() {
        }

        private ScopeFlare() {
        }
    }

    public static final class Hitbox {
        private static final ModConfigSpec.BooleanValue ENABLED = SERVER_BUILDER
                .comment("Resolve gun hits against oriented boxes on the player's skeleton (head, torso, arms, legs)",
                        "instead of TaCZ's single bounding box. Shots that slip between the limbs now miss.",
                        "Решать попадания из оружия по ориентированным боксам на скелете игрока (голова, торс, руки, ноги)",
                        "вместо одного бокса TaCZ. Выстрелы, прошедшие между конечностями, теперь промахиваются.")
                .define("hitbox.enabled", true);
        private static final ModConfigSpec.DoubleValue TORSO_MULTIPLIER = SERVER_BUILDER
                .comment("Damage multiplier for torso hits.",
                        "Множитель урона при попадании в торс.")
                .defineInRange("hitbox.torsoMultiplier", 1.0, 0.0, 10.0);
        private static final ModConfigSpec.DoubleValue ARM_MULTIPLIER = SERVER_BUILDER
                .comment("Damage multiplier for arm hits.",
                        "Множитель урона при попадании в руку.")
                .defineInRange("hitbox.armMultiplier", 0.75, 0.0, 10.0);
        private static final ModConfigSpec.DoubleValue LEG_MULTIPLIER = SERVER_BUILDER
                .comment("Damage multiplier for leg hits.",
                        "Множитель урона при попадании в ногу.")
                .defineInRange("hitbox.legMultiplier", 0.75, 0.0, 10.0);
        private static final ModConfigSpec.BooleanValue DEBUG_RENDER = SERVER_BUILDER
                .comment("Draw the skeleton boxes around nearby players, to check they line up with the model.",
                        "Рисовать боксы скелета вокруг ближайших игроков, чтобы проверить совпадение с моделью.")
                .define("hitbox.debugRender", false);
        private static final ModConfigSpec.DoubleValue VELOCITY_SLOP = SERVER_BUILDER
                .comment("Stretch skeleton bones along the target's movement by this many ticks of its velocity.",
                        "Covers the gap between where the shooter sees a moving player and where lag",
                        "compensation places the hitbox. A standing target keeps its exact silhouette. 0 = off.",
                        "Растягивать кости скелета вдоль движения цели на столько тиков её скорости.",
                        "Покрывает разрыв между тем, где стрелок видит бегущего игрока, и тем, куда",
                        "лаг-компенсация ставит хитбокс. Стоящая цель сохраняет точный силуэт. 0 — выкл.")
                .defineInRange("hitbox.velocitySlop", 3.0, 0.0, 10.0);

        public static boolean enabled;
        public static double torsoMultiplier;
        public static double armMultiplier;
        public static double legMultiplier;
        public static boolean debugRender;
        public static double velocitySlop;

        private static void load() {
            enabled = ENABLED.get();
            torsoMultiplier = TORSO_MULTIPLIER.get();
            armMultiplier = ARM_MULTIPLIER.get();
            legMultiplier = LEG_MULTIPLIER.get();
            debugRender = DEBUG_RENDER.get();
            velocitySlop = VELOCITY_SLOP.get();
        }

        static void init() {
        }

        private Hitbox() {
        }
    }

    public static final class Recoil {
        private static final ModConfigSpec.BooleanValue ENABLED = SERVER_BUILDER
                .comment("Residual recoil: every shot pushes the real aim off target and it never returns by itself.",
                        "TaCZ's own camera recoil animates back to where it started, so a burst costs the shooter nothing.",
                        "Остаточная отдача: каждый выстрел смещает реальный прицел и он сам не возвращается.",
                        "Родная отдача TaCZ анимируется обратно в исходную точку, поэтому очередь ничего не стоит стрелку.")
                .define("recoil.residualEnabled", true);
        private static final ModConfigSpec.DoubleValue PITCH_DEGREES = SERVER_BUILDER
                .comment("Degrees the aim climbs per shot for a mid-range rifle. Scaled by the gun's own TaCZ recoil curve.",
                        "На сколько градусов прицел уходит вверх за выстрел для средней винтовки. Масштабируется кривой отдачи самого оружия.")
                .defineInRange("recoil.pitchDegrees", 0.35, 0.0, 10.0);
        private static final ModConfigSpec.DoubleValue YAW_DEGREES = SERVER_BUILDER
                .comment("Degrees of horizontal walk per shot. The direction wanders during a burst, so the pattern cannot be memorised.",
                        "На сколько градусов прицел уводит в сторону за выстрел. Направление блуждает внутри очереди, поэтому рисунок не выучить.")
                .defineInRange("recoil.yawDegrees", 0.25, 0.0, 10.0);
        private static final ModConfigSpec.DoubleValue VARIANCE = SERVER_BUILDER
                .comment("Random spread of each kick: 0 = identical every shot, 1 = anywhere from nothing to double.",
                        "Случайный разброс каждого толчка: 0 — одинаково каждый выстрел, 1 — от нуля до двойного.")
                .defineInRange("recoil.variance", 0.5, 0.0, 1.0);
        private static final ModConfigSpec.DoubleValue ADS_MULTIPLIER = SERVER_BUILDER
                .comment("Residual recoil multiplier while fully aiming down sights (a braced stance holds the muzzle down).",
                        "Множитель остаточной отдачи при полном прицеливании (упор держит ствол внизу).")
                .defineInRange("recoil.adsMultiplier", 0.6, 0.0, 1.0);
        private static final ModConfigSpec.DoubleValue FOLLOW_SPEED = SERVER_BUILDER
                .comment("Fraction of the remaining kick applied each tick. 1 = instant snap, 0.2 = slow drag upwards.",
                        "Какая доля оставшегося толчка применяется за тик. 1 — мгновенный рывок, 0.2 — медленное уползание вверх.")
                .defineInRange("recoil.followSpeed", 0.45, 0.05, 1.0);

        public static boolean residualEnabled;
        public static double pitchDegrees;
        public static double yawDegrees;
        public static double variance;
        public static double adsMultiplier;
        public static double followSpeed;

        private static void load() {
            residualEnabled = ENABLED.get();
            pitchDegrees = PITCH_DEGREES.get();
            yawDegrees = YAW_DEGREES.get();
            variance = VARIANCE.get();
            adsMultiplier = ADS_MULTIPLIER.get();
            followSpeed = FOLLOW_SPEED.get();
        }

        static void init() {
        }

        private Recoil() {
        }
    }

    public static final class Tinnitus {
        private static final ModConfigSpec.BooleanValue ENABLED = SERVER_BUILDER
                .comment("Ringing ears after firing an unsuppressed gun in an enclosed space: a whine plus muffled hearing.",
                        "Звон в ушах после выстрела без глушителя в замкнутом помещении: писк и глухота.")
                .define("tinnitus.enabled", true);
        private static final ModConfigSpec.DoubleValue ENCLOSURE_THRESHOLD = SERVER_BUILDER
                .comment("How enclosed the shooter must be, as the fraction of five probe rays (four sideways, one up) that hit a wall within 6 blocks.",
                        "0.6 = a room or a bunker; 0 = ring even in the open field; 1 = only a fully sealed box.",
                        "Насколько замкнутым должно быть место: доля из пяти лучей (четыре в стороны, один вверх), попавших в стену в пределах 6 блоков.",
                        "0.6 — комната или бункер; 0 — звенит даже в чистом поле; 1 — только полностью закрытая коробка.")
                .defineInRange("tinnitus.enclosureThreshold", 0.6, 0.0, 1.0);
        private static final ModConfigSpec.DoubleValue INTENSITY_PER_SHOT = SERVER_BUILDER
                .comment("Ringing added by one shot from a mid-range rifle, scaled by the gun's recoil (a .50 deafens harder than a pistol).",
                        "Сколько звона добавляет один выстрел средней винтовки, с поправкой на отдачу оружия (.50 глушит сильнее пистолета).")
                .defineInRange("tinnitus.intensityPerShot", 0.35, 0.0, 1.0);
        private static final ModConfigSpec.DoubleValue DECAY_SECONDS = SERVER_BUILDER
                .comment("Seconds for full-strength ringing to fade away completely.",
                        "За сколько секунд звон полной силы полностью затухает.")
                .defineInRange("tinnitus.decaySeconds", 6.0, 0.5, 60.0);
        private static final ModConfigSpec.DoubleValue MUFFLE_STRENGTH = SERVER_BUILDER
                .comment("How strongly everything else is low-passed and quietened at full ringing. 0 = whine only, hearing intact.",
                        "Насколько сильно всё остальное глушится фильтром при полном звоне. 0 — только писк, слух цел.")
                .defineInRange("tinnitus.muffleStrength", 0.8, 0.0, 1.0);
        private static final ModConfigSpec.DoubleValue VISUAL_STRENGTH = SERVER_BUILDER
                .comment("How much of the suppression blur/vignette shader the ringing drives. 0 = sound only, no visuals.",
                        "Насколько звон использует шейдер подавления (размытие/виньетка). 0 — только звук, без визуала.")
                .defineInRange("tinnitus.visualStrength", 0.35, 0.0, 1.0);

        public static boolean enabled;
        public static double enclosureThreshold;
        public static double intensityPerShot;
        public static double decaySeconds;
        public static double muffleStrength;
        public static double visualStrength;

        private static void load() {
            enabled = ENABLED.get();
            enclosureThreshold = ENCLOSURE_THRESHOLD.get();
            intensityPerShot = INTENSITY_PER_SHOT.get();
            decaySeconds = DECAY_SECONDS.get();
            muffleStrength = MUFFLE_STRENGTH.get();
            visualStrength = VISUAL_STRENGTH.get();
        }

        static void init() {
        }

        private Tinnitus() {
        }
    }

    public static final class MuzzleDust {
        private static final ModConfigSpec.BooleanValue ENABLED = SERVER_BUILDER
                .comment("Muzzle blast kicks dust off a surface right under the barrel, giving away a low shooter.",
                        "Дульная волна поднимает пыль с поверхности под стволом, демаскируя лежащего/присевшего стрелка.")
                .define("muzzleDust.enabled", true);
        private static final ModConfigSpec.DoubleValue MAX_SURFACE_DISTANCE = SERVER_BUILDER
                .comment("Maximum distance from the muzzle down to the surface for the blast to raise anything.",
                        "1.3 catches a crouching or prone shooter; a standing one (muzzle ~1.6 above the floor) raises nothing.",
                        "Максимальное расстояние от ствола вниз до поверхности, при котором волна что-то поднимает.",
                        "1.3 ловит присевшего или лежащего стрелка; стоящий (ствол ~1.6 над полом) не поднимает ничего.")
                .defineInRange("muzzleDust.maxSurfaceDistance", 1.3, 0.0, 4.0);
        private static final ModConfigSpec.IntValue PARTICLE_COUNT = SERVER_BUILDER
                .comment("Dust particles per shot (halved for a suppressed gun).",
                        "Частиц пыли за выстрел (для оружия с глушителем — вдвое меньше).")
                .defineInRange("muzzleDust.particleCount", 10, 1, 64);
        private static final ModConfigSpec.IntValue COOLDOWN_TICKS = SERVER_BUILDER
                .comment("Minimum ticks between two dust puffs from the same shooter, so full auto does not flood the screen.",
                        "Минимум тиков между двумя облачками пыли от одного стрелка, чтобы автоматический огонь не залил экран.")
                .defineInRange("muzzleDust.cooldownTicks", 3, 0, 100);

        public static boolean enabled;
        public static double maxSurfaceDistance;
        public static int particleCount;
        public static int cooldownTicks;

        private static void load() {
            enabled = ENABLED.get();
            maxSurfaceDistance = MAX_SURFACE_DISTANCE.get();
            particleCount = PARTICLE_COUNT.get();
            cooldownTicks = COOLDOWN_TICKS.get();
        }

        static void init() {
        }

        private MuzzleDust() {
        }
    }

    static {
        Tweaks.init();
        DistantFire.init();
        Whizz.init();
        Suppression.init();
        Ricochet.init();
        Pierce.init();
        FreeAim.init();
        Movement.init();
        ScopeFlare.init();
        GunLights.init();
        Hitbox.init();
        Recoil.init();
        Tinnitus.init();
        MuzzleDust.init();
    }

    public static final ModConfigSpec SERVER_SPEC = SERVER_BUILDER.build();

    @SubscribeEvent
    static void onLoad(final ModConfigEvent event) {
        if (event instanceof ModConfigEvent.Unloading) {
            return;
        }
        if (event.getConfig().getSpec() == SERVER_SPEC) {
            debug = DEBUG.get();
            Tweaks.load();
            DistantFire.load();
            Whizz.load();
            Ricochet.load();
            Pierce.load();
            Suppression.load();
            FreeAim.load();
            Movement.load();
            ScopeFlare.load();
            GunLights.load();
            Hitbox.load();
            Recoil.load();
            Tinnitus.load();
            MuzzleDust.load();
        }
    }
}
