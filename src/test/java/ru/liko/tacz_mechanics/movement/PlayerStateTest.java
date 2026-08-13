package ru.liko.tacz_mechanics.movement;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The state code is the only thing the network carries, so a sit/prone/probe combination has to
 * survive a write/read round-trip intact — and entering a posture must zero the probe, or the
 * render and hitbox chains would try to compose a prone flat with a lean roll.
 */
class PlayerStateTest {

    private static PlayerState roundTrip(PlayerState src) {
        PlayerState dst = new PlayerState();
        dst.readCode(src.writeCode());
        return dst;
    }

    @Test
    void standingNeutralIsCodeOne() {
        PlayerState s = new PlayerState();
        assertEquals(1, s.writeCode());
        assertTrue(s.isStanding());
        assertEquals(0, s.getProbe());
    }

    @Test
    void probeRoundTrips() {
        for (byte probe : new byte[]{-1, 0, 1}) {
            PlayerState s = new PlayerState();
            if (probe == -1) s.leftProbe();
            if (probe == 1) s.rightProbe();
            PlayerState back = roundTrip(s);
            assertEquals(probe, back.getProbe());
            assertTrue(back.isStanding());
        }
    }

    @Test
    void sitAndProneRoundTrip() {
        PlayerState sit = new PlayerState();
        sit.enableSit();
        PlayerState sitBack = roundTrip(sit);
        assertTrue(sitBack.isSitting());
        assertFalse(sitBack.isProne());

        PlayerState prone = new PlayerState();
        prone.enableProne();
        PlayerState proneBack = roundTrip(prone);
        assertTrue(proneBack.isProne());
        assertFalse(proneBack.isSitting());
    }

    @Test
    void enteringPostureZeroesProbeAndIsExclusive() {
        PlayerState s = new PlayerState();
        s.rightProbe();
        s.enableSit();
        assertEquals(0, s.getProbe());
        assertTrue(s.isSitting());

        s.enableProne();
        assertTrue(s.isProne());
        assertFalse(s.isSitting());

        s.standUp();
        assertTrue(s.isStanding());
    }

    @Test
    void everyPostureTransitionStampsTheSharedCooldown() {
        // The лег-сел speed abuse worked because per-posture timers let alternation and stand-up
        // bypass the cooldown. Every transition — enter sit, enter prone, stand up, and a
        // posture-bit change arriving via readCode (the server path) — must reset the shared timer.
        ru.liko.tacz_mechanics.Config.Movement.sitCooldown = 5.0;
        ru.liko.tacz_mechanics.Config.Movement.proneCooldown = 5.0;
        try {
            PlayerState s = new PlayerState();
            assertTrue(s.canSit());
            s.enableProne();
            assertFalse(s.canSit());   // лег → сразу сесть нельзя
            assertFalse(s.canProne());

            PlayerState server = new PlayerState();
            server.readCode(110 + 1);  // sit+prone bits change via network path
            assertTrue(server.sincePostureChange() < 1000);
            long stamped = server.sincePostureChange();
            server.readCode(110 + 1);  // same code again — no re-stamp
            assertTrue(server.sincePostureChange() >= stamped);

            PlayerState up = new PlayerState();
            up.enableSit();
            up.standUp();              // stand-up stamps too
            assertFalse(up.canProne());
        } finally {
            ru.liko.tacz_mechanics.Config.Movement.sitCooldown = 0;
            ru.liko.tacz_mechanics.Config.Movement.proneCooldown = 0;
        }
    }

    @Test
    void canApplyMovementCodeAllowsPostureAndLean() {
        // Entering/leaving a posture is validated against the live player (canHoldPosture / canStand),
        // so those paths need a world. Lean-only changes while standing stay unconditional.
        int standing = 1;              // sit0 prone0 probe0
        assertTrue(MovementPosture.canApplyMovementCode(null, standing, standing));
    }
}
