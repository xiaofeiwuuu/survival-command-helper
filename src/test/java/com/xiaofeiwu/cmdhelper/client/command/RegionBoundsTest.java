package com.xiaofeiwu.cmdhelper.client.command;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RegionBoundsTest {

    @Test
    void of_normalisesCornersOrder() {
        RegionBounds b = RegionBounds.of("5 70 -3", "-5 64 3");
        assertEquals(new RegionBounds(-5, 64, -3, 5, 70, 3), b);
    }

    @Test
    void volume_countsBothEndsInclusive() {
        RegionBounds b = RegionBounds.of("0 0 0", "9 2 4");
        assertEquals(10, b.sizeX());
        assertEquals(3, b.sizeY());
        assertEquals(5, b.sizeZ());
        assertEquals(150, b.volume());
    }

    @Test
    void singleBlockHasVolumeOne() {
        assertEquals(1, RegionBounds.of("7 7 7", "7 7 7").volume());
    }

    @Test
    void fillLimit_boundaryIsInclusive() {
        // 32 * 32 * 32 = 32768 exactly: allowed. One more layer: over.
        assertFalse(RegionBounds.of("0 0 0", "31 31 31").exceedsFillLimit());
        assertTrue(RegionBounds.of("0 0 0", "31 32 31").exceedsFillLimit());
    }

    @Test
    void volume_doesNotOverflowInt() {
        assertTrue(RegionBounds.of("0 0 0", "5000 5000 5000").exceedsFillLimit());
    }

    // ---- minus ------------------------------------------------------------------------------------

    /** Every block of `box` must be in exactly one piece iff it is not in `hole` (and in none otherwise). */
    private static void assertExactCover(RegionBounds box, RegionBounds hole) {
        java.util.List<RegionBounds> pieces = box.minus(hole);
        for (int x = box.minX(); x <= box.maxX(); x++) {
            for (int y = box.minY(); y <= box.maxY(); y++) {
                for (int z = box.minZ(); z <= box.maxZ(); z++) {
                    boolean inHole = x >= hole.minX() && x <= hole.maxX() && y >= hole.minY() && y <= hole.maxY()
                            && z >= hole.minZ() && z <= hole.maxZ();
                    int count = 0;
                    for (RegionBounds p : pieces) {
                        if (x >= p.minX() && x <= p.maxX() && y >= p.minY() && y <= p.maxY() && z >= p.minZ() && z <= p.maxZ()) {
                            count++;
                        }
                    }
                    assertEquals(inHole ? 0 : 1, count, "block " + x + "," + y + "," + z);
                }
            }
        }
    }

    @Test
    void minus_disjointBoxesLeaveTheBoxWhole() {
        RegionBounds box = new RegionBounds(0, 0, 0, 4, 4, 4);
        assertEquals(java.util.List.of(box), box.minus(new RegionBounds(10, 10, 10, 12, 12, 12)));
    }

    @Test
    void minus_holeCoveringTheWholeBoxLeavesNothing() {
        assertTrue(new RegionBounds(1, 1, 1, 3, 3, 3).minus(new RegionBounds(0, 0, 0, 9, 9, 9)).isEmpty());
    }

    @Test
    void minus_sharedBottomLayer_leavesOneSlab() {
        // The clone note's stacking case: copy occupies Y 78..83, the source is Y 73..78.
        RegionBounds copy = new RegionBounds(-1267, 78, -684, -1258, 83, -673);
        RegionBounds source = new RegionBounds(-1267, 73, -684, -1258, 78, -673);
        assertEquals(java.util.List.of(new RegionBounds(-1267, 79, -684, -1258, 83, -673)), copy.minus(source));
    }

    @Test
    void minus_coversExactlyTheRightBlocks_inManyShapes() {
        RegionBounds box = new RegionBounds(0, 0, 0, 5, 4, 6);
        assertExactCover(box, new RegionBounds(2, 1, 2, 3, 2, 3));   // hole strictly inside
        assertExactCover(box, new RegionBounds(-3, -3, -3, 2, 2, 2)); // corner overlap
        assertExactCover(box, new RegionBounds(0, 0, 0, 5, 4, 2));    // slab through the whole box
        assertExactCover(box, new RegionBounds(3, -5, 3, 9, 9, 9));   // overhanging on several sides
        assertExactCover(box, new RegionBounds(2, 2, 2, 2, 2, 2));    // a single block
    }

    @Test
    void minus_volumesAddUp() {
        RegionBounds box = new RegionBounds(0, 0, 0, 9, 9, 9);
        RegionBounds hole = new RegionBounds(3, 3, 3, 5, 5, 5);
        long total = box.minus(hole).stream().mapToLong(RegionBounds::volume).sum();
        assertEquals(1000 - 27, total);
    }

    @Test
    void of_rejectsMalformedCoords() {
        assertThrows(IllegalArgumentException.class, () -> RegionBounds.of("1 2", "3 4 5"));
    }
}
