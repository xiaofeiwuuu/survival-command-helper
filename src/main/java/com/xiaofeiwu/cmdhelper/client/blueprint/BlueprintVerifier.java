package com.xiaofeiwu.cmdhelper.client.blueprint;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * "Did it come out right?" — compares what should be in a region with what is, and returns boxes
 * covering exactly the blocks that are wrong, so only those need to be sent again. Doors that popped
 * off, sand that fell, blocks a command silently didn't place.
 *
 * It compares the kind of block, not every property: the game recomputes properties like fence
 * connections and stair shapes when blocks land next to each other, so demanding an exact state match
 * would "fix" those forever. Pure: the caller supplies the two grids as plain numbers.
 */
public final class BlueprintVerifier {

    private BlueprintVerifier() {
    }

    /**
     * The grid of what should be there: for each cell, the palette index placed there, or -1 where
     * nothing was placed (so it is not checked). Cells are indexed like {@link VoxelMerger#index}.
     *
     * @param placed world-coordinate boxes that were actually built (skipped ones excluded)
     */
    public static int[] expectedGrid(List<Cuboid> placed, int minX, int minY, int minZ, int sizeX, int sizeY, int sizeZ) {
        int[] grid = new int[sizeX * sizeY * sizeZ];
        Arrays.fill(grid, -1);
        for (Cuboid c : placed) {
            for (int y = c.y0(); y <= c.y1(); y++) {
                for (int z = c.z0(); z <= c.z1(); z++) {
                    for (int x = c.x0(); x <= c.x1(); x++) {
                        int lx = x - minX, ly = y - minY, lz = z - minZ;
                        if (lx >= 0 && lx < sizeX && ly >= 0 && ly < sizeY && lz >= 0 && lz < sizeZ) {
                            grid[VoxelMerger.index(lx, ly, lz, sizeX, sizeZ)] = c.paletteIndex();
                        }
                    }
                }
            }
        }
        return grid;
    }

    /**
     * @param expected       palette index per cell, or -1 for "don't check" (see {@link #expectedGrid})
     * @param actualBlock    the kind of block actually found in each cell, as any consistent number
     * @param blockOfPalette for each palette index, the same kind of number for the block it stands for
     * @return boxes, in the grid's local coordinates, of the cells that are wrong, each carrying the
     *         palette index that belongs there
     */
    public static List<Cuboid> mismatches(int[] expected, int[] actualBlock, int[] blockOfPalette,
                                          int sizeX, int sizeY, int sizeZ) {
        int[] wrong = new int[expected.length];
        boolean any = false;
        for (int i = 0; i < expected.length; i++) {
            int want = expected[i];
            if (want >= 0 && actualBlock[i] != blockOfPalette[want]) {
                wrong[i] = want;
                any = true;
            } else {
                wrong[i] = -1;
            }
        }
        return any ? VoxelMerger.merge(wrong, sizeX, sizeY, sizeZ) : new ArrayList<>();
    }
}
