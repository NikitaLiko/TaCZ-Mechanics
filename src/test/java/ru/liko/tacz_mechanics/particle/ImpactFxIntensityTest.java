package ru.liko.tacz_mechanics.particle;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The burst intensity multiplies every layer's particle count on the client, so both ends of the
 * curve matter: too low and an impact shows nothing, too high and a magazine dump is a particle
 * storm on someone's machine.
 */
class ImpactFxIntensityTest {

    @Test
    void heavierRoundsAlwaysThrowMore() {
        float previous = 0.0f;
        for (float damage = 0.0f; damage <= 60.0f; damage += 0.5f) {
            float scale = ImpactFxSender.scaleForDamage(damage);
            assertTrue(scale >= previous, "scale dropped as damage rose, at " + damage);
            previous = scale;
        }
    }

    @Test
    void staysInsideItsClampForAnythingAConfigCanAskFor() {
        for (float damage : new float[] {-100.0f, -1.0f, 0.0f, 1.0f, 7.0f, 30.0f, 1000.0f}) {
            float scale = ImpactFxSender.scaleForDamage(damage);
            assertTrue(scale >= 0.5f && scale <= 1.8f, "scale " + scale + " out of clamp at " + damage);
        }
    }

    @Test
    void calibresStaySeparatedAcrossTheUsefulRange() {
        float pistol = ImpactFxSender.scaleForDamage(5.0f);
        float rifle = ImpactFxSender.scaleForDamage(9.0f);
        float antiMateriel = ImpactFxSender.scaleForDamage(28.0f);

        assertTrue(rifle - pistol > 0.1f, "pistol and rifle look the same");
        assertTrue(antiMateriel - rifle > 0.2f, "rifle and .50 look the same");
        // A hit is always visible, however weak the round.
        assertTrue(ImpactFxSender.scaleForDamage(0.5f) >= 0.5f);
    }

    @Test
    void everyEventTypeStaysBelowASolidHit() {
        assertTrue(ImpactFxSender.RICOCHET < ImpactFxSender.HIT);
        assertTrue(ImpactFxSender.PIERCE_ENTRY < ImpactFxSender.HIT);
        assertTrue(ImpactFxSender.PIERCE_EXIT < ImpactFxSender.PIERCE_ENTRY);
    }
}
