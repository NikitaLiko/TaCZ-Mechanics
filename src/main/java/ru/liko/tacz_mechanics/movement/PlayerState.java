package ru.liko.tacz_mechanics.movement;

import net.minecraft.world.entity.EntityDimensions;
import org.jetbrains.annotations.Nullable;
import ru.liko.tacz_mechanics.Config;

import java.util.concurrent.TimeUnit;

/**
 * Represents the movement state of a player.
 * Tracks sitting, crawling, and leaning (probe) states.
 */
public class PlayerState {
    private boolean sitting = false;
    private boolean crawling = false;
    private byte probe = 0;
    private float probeOffset = 0;
    private long lastSyncTime = System.currentTimeMillis();

    public boolean isSitting() { return sitting; }
    public boolean isCrawling() { return crawling; }
    public byte getProbe() { return probe; }
    public float getProbeOffset() { return probeOffset; }

    private long lastSit;
    private long lastCrawl;
    private long lastProbe;

    public void updateOffset() {
        double amplifier = (System.currentTimeMillis() - lastSyncTime) * (60 / 1000d);
        lastSyncTime = System.currentTimeMillis();
        
        // Slower speed for more realistic leaning (0.05 instead of 0.1)
        float speed = 0.05f;
        
        if (probe == -1) {
            if (probeOffset > -1) {
                probeOffset -= speed * amplifier;
            }
            if (probeOffset < -1) {
                probeOffset = -1;
            }
        }
        if (probe == 1) {
            if (probeOffset < 1) {
                probeOffset += speed * amplifier;
            }
            if (probeOffset > 1) {
                probeOffset = 1;
            }
        }

        if (probe == 0) {
            if (Math.abs(probeOffset) <= speed * amplifier) {
                probeOffset = 0;
            }
            if (probeOffset < 0) {
                probeOffset += speed * amplifier;
            }
            if (probeOffset > 0) {
                probeOffset -= speed * amplifier;
            }
        }
    }

    public boolean isStanding() {
        return !crawling && !sitting;
    }

    public boolean canSit() {
        return (System.currentTimeMillis() - this.lastSit > TimeUnit.MILLISECONDS.toMillis((long)(Config.Movement.sitCooldown * 1000)));
    }

    public boolean canCrawl() {
        return (System.currentTimeMillis() - this.lastCrawl > TimeUnit.MILLISECONDS.toMillis((long)(Config.Movement.crawlCooldown * 1000)));
    }

    public boolean canProbe() {
        return (System.currentTimeMillis() - this.lastProbe > TimeUnit.MILLISECONDS.toMillis((long)(Config.Movement.leanCooldown * 1000)));
    }

    public void enableSit() {
        sitting = true;
        disableCrawling();
        this.lastSit = System.currentTimeMillis();
    }

    public void disableSit() {
        sitting = false;
    }

    public void enableCrawling() {
        crawling = true;
        disableSit();
        this.lastCrawl = System.currentTimeMillis();
    }

    public void disableCrawling() {
        crawling = false;
    }

    public void resetProbe() {
        probe = 0;
    }

    public void leftProbe() {
        probe = -1;
        this.lastProbe = System.currentTimeMillis();
    }

    public void rightProbe() {
        probe = 1;
        this.lastProbe = System.currentTimeMillis();
    }

    public void readCode(int code) {
        probe = (byte) (code % 10 - 1);
        code /= 10;
        crawling = code % 10 != 0;
        code /= 10;
        sitting = code % 10 != 0;
    }

    public void reset() {
        readCode(1);
    }

    public int writeCode() {
        return (sitting ? 1 : 0) * 100 + (crawling ? 1 : 0) * 10 + probe + 1;
    }
    
    /**
     * Get the height of the player based on current state.
     * Values are read from Config.
     */
    public float getHeight() {
        if (crawling) return Config.Movement.crawlHeight;
        if (sitting) return Config.Movement.sitHeight;
        return 1.8f;
    }
    
    /**
     * Get the eye height of the player based on current state.
     * Values are read from Config.
     */
    public float getEyeHeight(float defaultHeight) {
        if (crawling) return Config.Movement.crawlEyeHeight;
        if (sitting) return Config.Movement.sitEyeHeight;
        return defaultHeight;
    }

    @Nullable
    public EntityDimensions getCustomDimensions() {
        if (crawling) {
            return EntityDimensions.scalable(Config.Movement.crawlWidth, Config.Movement.crawlHeight)
                .withEyeHeight(Config.Movement.crawlEyeHeight);
        }
        if (sitting) {
            return EntityDimensions.scalable(Config.Movement.sitWidth, Config.Movement.sitHeight)
                .withEyeHeight(Config.Movement.sitEyeHeight);
        }
        return null;
    }

    public float getCustomEyeHeight() {
        if (crawling) return Config.Movement.crawlEyeHeight;
        if (sitting) return Config.Movement.sitEyeHeight;
        return Float.NaN;
    }
}
