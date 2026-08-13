package ru.liko.tacz_mechanics.recoil;

import org.junit.jupiter.api.Test;
import ru.liko.tacz_mechanics.client.recoil.RecoilDrift;

import static org.junit.jupiter.api.Assertions.*;

class RecoilDriftTest {

    @Test
    void freshBurstTakesTheRollAsIsDirection() {
        assertEquals(-0.8f, RecoilDrift.nextWander(0.9f, true, -0.8f), 1e-6);
    }

    @Test
    void insideABurstTheDirectionWalksAndStaysBounded() {
        float wander = 0f;
        // Worst case: every roll pushes the same way. The direction must saturate, not run away.
        for (int i = 0; i < 20; i++) {
            wander = RecoilDrift.nextWander(wander, false, 1f);
            assertTrue(wander >= -1f && wander <= 1f, "wander escaped [-1,1]: " + wander);
        }
        assertEquals(1f, wander, 1e-6);

        for (int i = 0; i < 20; i++) {
            wander = RecoilDrift.nextWander(wander, false, -1f);
            assertTrue(wander >= -1f && wander <= 1f, "wander escaped [-1,1]: " + wander);
        }
        assertEquals(-1f, wander, 1e-6);
    }

    @Test
    void aBurstDoesNotAlwaysClimbTheSameWay() {
        // Same starting point, opposite rolls: the two bursts must diverge, otherwise the pattern
        // is memorisable and the whole point of the drift is gone.
        assertNotEquals(RecoilDrift.nextWander(0f, false, 0.5f), RecoilDrift.nextWander(0f, false, -0.5f));
    }
}
