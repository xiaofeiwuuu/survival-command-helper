package com.xiaofeiwu.cmdhelper.client.blueprint;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TransformTest {

    private static Cuboid cell(int x, int y, int z) {
        return new Cuboid(x, y, z, x, y, z, 0);
    }

    @Test
    void identityChangesNothing() {
        Cuboid c = new Cuboid(1, 2, 3, 4, 5, 6, 9);
        assertEquals(c, Transform.NONE.apply(c, 10, 10));
    }

    @Test
    void fourClockwiseTurns_comeBackToTheStart() {
        Cuboid c = new Cuboid(1, 2, 3, 4, 5, 6, 9);
        int sx = 8, sz = 11;
        Transform t = Transform.NONE;
        for (int i = 0; i < 4; i++) {
            t = t.rotatedClockwise();
        }
        assertEquals(c, t.apply(c, sx, sz));
    }

    @Test
    void mirroringTwice_comesBackToTheStart() {
        Cuboid c = new Cuboid(1, 2, 3, 4, 5, 6, 9);
        assertEquals(c, Transform.NONE.mirrored().mirrored().apply(c, 8, 11));
    }

    @Test
    void aQuarterTurnClockwise_sendsTheNorthEdgeToTheEast() {
        // north is z = 0; after a clockwise turn it must be the east edge: the largest x of the new size.
        Transform t = Transform.NONE.rotatedClockwise();
        int sx = 4, sz = 7;
        int[] size = t.horizontalSize(sx, sz);
        assertEquals(7, size[0]);
        assertEquals(4, size[1]);
        Cuboid northEdgeCell = cell(1, 0, 0);
        assertEquals(size[0] - 1, t.apply(northEdgeCell, sx, sz).x0());
    }

    @Test
    void aQuarterTurnClockwise_sendsTheEastEdgeToTheSouth() {
        Transform t = Transform.NONE.rotatedClockwise();
        int sx = 4, sz = 7;
        int[] size = t.horizontalSize(sx, sz);
        Cuboid eastEdgeCell = cell(sx - 1, 0, 2);
        assertEquals(size[1] - 1, t.apply(eastEdgeCell, sx, sz).z0());
    }

    @Test
    void rotatedCuboidsStayInsideTheTransformedSize_andKeepTheirVolume() {
        int sx = 5, sy = 4, sz = 9;
        Cuboid c = new Cuboid(1, 0, 2, 3, 3, 7, 0);
        for (int turns = 0; turns < 4; turns++) {
            for (boolean mirror : new boolean[]{false, true}) {
                Transform t = new Transform(turns, mirror);
                Cuboid r = t.apply(c, sx, sz);
                int[] size = t.horizontalSize(sx, sz);
                assertEquals(c.volume(), r.volume());
                assertTrue(r.x0() >= 0 && r.x1() < size[0], t.toString());
                assertTrue(r.z0() >= 0 && r.z1() < size[1], t.toString());
                assertEquals(c.y0(), r.y0());
                assertEquals(c.y1(), r.y1());
            }
        }
    }

    @Test
    void mirrorFlipsX_beforeTheRotation() {
        Transform t = Transform.NONE.mirrored();
        assertEquals(cell(3, 0, 5), t.apply(cell(0, 0, 5), 4, 9));
        // mirror then one clockwise turn is not the same as one turn then mirror
        Transform mirrorThenRotate = new Transform(1, true);
        Cuboid c = cell(0, 0, 0);
        Cuboid a = mirrorThenRotate.apply(c, 4, 6);
        Cuboid b = new Transform(1, false).apply(c, 4, 6);
        assertTrue(a.x0() != b.x0() || a.z0() != b.z0());
    }

    @Test
    void quarterTurnsWrapAround() {
        assertEquals(new Transform(1, false), new Transform(5, false));
        assertEquals(new Transform(3, false), new Transform(-1, false));
    }

    @Test
    void everyCellMapsToADistinctCell_soNothingIsLostOrDuplicated() {
        int sx = 3, sz = 5;
        for (int turns = 0; turns < 4; turns++) {
            Transform t = new Transform(turns, turns % 2 == 0);
            java.util.Set<String> seen = new java.util.HashSet<>();
            for (int x = 0; x < sx; x++) {
                for (int z = 0; z < sz; z++) {
                    Cuboid r = t.apply(cell(x, 0, z), sx, sz);
                    assertTrue(seen.add(r.x0() + "," + r.z0()), "collision after " + t);
                }
            }
            assertEquals(sx * sz, seen.size());
        }
    }
}
