package com.xiaofeiwu.cmdhelper.client.blueprint;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VoxelMergerTest {

    private static int[] grid(int sx, int sy, int sz, int fill) {
        int[] cells = new int[sx * sy * sz];
        Arrays.fill(cells, fill);
        return cells;
    }

    private static void set(int[] cells, int sx, int sz, int x, int y, int z, int v) {
        cells[VoxelMerger.index(x, y, z, sx, sz)] = v;
    }

    /** Every cell with a value is in exactly one box, of that value; cells without a value are in none. */
    private static void assertExactCover(int[] cells, int sx, int sy, int sz, List<Cuboid> boxes) {
        int[] count = new int[cells.length];
        for (Cuboid b : boxes) {
            for (int y = b.y0(); y <= b.y1(); y++) {
                for (int z = b.z0(); z <= b.z1(); z++) {
                    for (int x = b.x0(); x <= b.x1(); x++) {
                        int i = VoxelMerger.index(x, y, z, sx, sz);
                        count[i]++;
                        assertEquals(cells[i], b.paletteIndex(), "wrong value at " + x + "," + y + "," + z);
                    }
                }
            }
        }
        for (int i = 0; i < cells.length; i++) {
            assertEquals(cells[i] >= 0 ? 1 : 0, count[i], "cell " + i);
        }
    }

    @Test
    void aSolidBlockOfOneKind_isOneBox() {
        int[] cells = grid(10, 6, 12, 3);
        List<Cuboid> boxes = VoxelMerger.merge(cells, 10, 6, 12);
        assertEquals(List.of(new Cuboid(0, 0, 0, 9, 5, 11, 3)), boxes);
    }

    @Test
    void aRowIsOneBox() {
        int[] cells = grid(8, 1, 1, -1);
        for (int x = 2; x <= 6; x++) {
            set(cells, 8, 1, x, 0, 0, 1);
        }
        assertEquals(List.of(new Cuboid(2, 0, 0, 6, 0, 0, 1)), VoxelMerger.merge(cells, 8, 1, 1));
    }

    @Test
    void aSingleStrayBlock_isOneBlockBox() {
        int[] cells = grid(5, 5, 5, -1);
        set(cells, 5, 5, 2, 3, 4, 7);
        assertEquals(List.of(new Cuboid(2, 3, 4, 2, 3, 4, 7)), VoxelMerger.merge(cells, 5, 5, 5));
    }

    @Test
    void aWallIsOneBox_notOneBoxPerRow() {
        int[] cells = grid(10, 6, 1, -1);
        for (int y = 0; y < 6; y++) {
            for (int x = 0; x < 10; x++) {
                set(cells, 10, 1, x, y, 0, 2);
            }
        }
        assertEquals(1, VoxelMerger.merge(cells, 10, 6, 1).size());
    }

    @Test
    void aHollowBoxIsAFewBoxes_notOneBoxPerBlock() {
        int s = 6;
        int[] cells = grid(s, s, s, 0);          // air inside...
        for (int y = 0; y < s; y++) {
            for (int z = 0; z < s; z++) {
                for (int x = 0; x < s; x++) {
                    if (x == 0 || y == 0 || z == 0 || x == s - 1 || y == s - 1 || z == s - 1) {
                        set(cells, s, s, x, y, z, 1);   // ...stone shell
                    }
                }
            }
        }
        List<Cuboid> boxes = VoxelMerger.merge(cells, s, s, s);
        assertExactCover(cells, s, s, s, boxes);
        assertTrue(boxes.size() <= 8, "a hollow cube should be a handful of boxes, got " + boxes.size());
    }

    @Test
    void differentKindsAreNeverMerged() {
        int[] cells = grid(4, 1, 1, -1);
        set(cells, 4, 1, 0, 0, 0, 1);
        set(cells, 4, 1, 1, 0, 0, 1);
        set(cells, 4, 1, 2, 0, 0, 2);
        set(cells, 4, 1, 3, 0, 0, 2);
        assertEquals(2, VoxelMerger.merge(cells, 4, 1, 1).size());
    }

    @Test
    void emptyGridGivesNoBoxes() {
        assertTrue(VoxelMerger.merge(grid(3, 3, 3, -1), 3, 3, 3).isEmpty());
    }

    @Test
    void randomGrids_alwaysCoverExactly() {
        Random random = new Random(42);
        for (int round = 0; round < 40; round++) {
            int sx = 1 + random.nextInt(7), sy = 1 + random.nextInt(7), sz = 1 + random.nextInt(7);
            int[] cells = new int[sx * sy * sz];
            for (int i = 0; i < cells.length; i++) {
                cells[i] = random.nextInt(4) - 1; // -1 (nothing) and kinds 0..2
            }
            assertExactCover(cells, sx, sy, sz, VoxelMerger.merge(cells, sx, sy, sz));
        }
    }
}
