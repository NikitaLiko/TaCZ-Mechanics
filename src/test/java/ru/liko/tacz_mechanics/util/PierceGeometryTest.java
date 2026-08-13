package ru.liko.tacz_mechanics.util;

import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class PierceGeometryTest {

    private static final Vec3 EAST = new Vec3(1, 0, 0);

    private static List<AABB> fullCube() {
        return List.of(new AABB(0, 0, 0, 1, 1, 1));
    }

    /** Fence: 4/16 post plus arms reaching both block edges along Z. */
    private static List<AABB> fence() {
        return List.of(
            new AABB(0.375, 0, 0.375, 0.625, 1.5, 0.625),
            new AABB(0.4375, 0.375, 0, 0.5625, 0.5625, 0.375),
            new AABB(0.4375, 0.375, 0.625, 0.5625, 0.5625, 1));
    }

    @Test
    void fullBlockIsOneBlockThick() {
        assertEquals(1.0, PierceGeometry.materialThickness(fullCube(), new Vec3(0, 0.5, 0.5), EAST), 1e-6);
    }

    @Test
    void fencePostIsThin() {
        List<AABB> boxes = fence();
        Vec3 entry = new Vec3(0, 0.5, 0.5);
        assertEquals(0.25, PierceGeometry.materialThickness(boxes, entry, EAST), 1e-6);
        assertEquals(0.625, PierceGeometry.exitT(boxes, entry, EAST), 1e-6);
    }

    @Test
    void airGapBetweenBoxesIsNotCountedAsMaterial() {
        List<AABB> boxes = List.of(
            new AABB(0, 0, 0, 0.1, 1, 1),
            new AABB(0.9, 0, 0, 1, 1, 1));
        Vec3 entry = new Vec3(0, 0.5, 0.5);
        assertEquals(0.2, PierceGeometry.materialThickness(boxes, entry, EAST), 1e-6);
        assertEquals(1.0, PierceGeometry.exitT(boxes, entry, EAST), 1e-6);
    }

    @Test
    void missingRayHasNoThicknessAndNoExit() {
        List<AABB> boxes = fence();
        Vec3 aboveEverything = new Vec3(0, 2.0, 0.5);
        assertEquals(0.0, PierceGeometry.materialThickness(boxes, aboveEverything, EAST), 1e-6);
        assertEquals(-1.0, PierceGeometry.exitT(boxes, aboveEverything, EAST), 1e-6);
    }

    @Test
    void rayStartingInsideCountsFromItsOrigin() {
        assertEquals(0.25, PierceGeometry.materialThickness(fullCube(), new Vec3(0.75, 0.5, 0.5), EAST), 1e-6);
    }
}
