package com.xiaofeiwu.cmdhelper.client.blueprint;

import com.xiaofeiwu.cmdhelper.client.blueprint.GhostGrids.Shape;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class GhostGridsTest {

    private static long count(String[] grid) {
        return Arrays.stream(grid).filter(v -> v != null).count();
    }

    @Test
    void solid_fillsEveryCell() {
        String[] grid = new String[4 * 3 * 5];
        GhostGrids.fill(grid, 4, 3, 5, "stone", Shape.SOLID);
        assertEquals(60, count(grid));
    }

    @Test
    void shell_ofABigBox_isOnlyTheOutsideLayer() {
        String[] grid = new String[5 * 5 * 5];
        GhostGrids.fill(grid, 5, 5, 5, "stone", Shape.SHELL);
        assertEquals(5 * 5 * 5 - 3 * 3 * 3, count(grid)); // 125 - 27
    }

    @Test
    void shell_leavesTheInsideEmpty() {
        String[] grid = new String[3 * 3 * 3];
        GhostGrids.fill(grid, 3, 3, 3, "stone", Shape.SHELL);
        assertNull(grid[VoxelMerger.index(1, 1, 1, 3, 3)]);
        assertEquals(26, count(grid));
    }

    @Test
    void shell_ofAThinBox_isEverything_becauseThereIsNoInside() {
        String[] flat = new String[5 * 1 * 5];
        GhostGrids.fill(flat, 5, 1, 5, "stone", Shape.SHELL);
        assertEquals(25, count(flat));
        String[] two = new String[2 * 2 * 2];
        GhostGrids.fill(two, 2, 2, 2, "stone", Shape.SHELL);
        assertEquals(8, count(two));
        String[] wall = new String[1 * 4 * 4];
        GhostGrids.fill(wall, 1, 4, 4, "stone", Shape.SHELL);
        assertEquals(16, count(wall));
    }

    @Test
    void cellsOutsideTheShapeAreLeftAsTheyWere() {
        String[] grid = new String[3 * 3 * 3];
        grid[VoxelMerger.index(1, 1, 1, 3, 3)] = "already here";
        GhostGrids.fill(grid, 3, 3, 3, "stone", Shape.SHELL);
        assertEquals("already here", grid[VoxelMerger.index(1, 1, 1, 3, 3)]);
    }

    @Test
    void fillModes_mapToShapes() {
        assertEquals(Shape.SOLID, GhostGrids.shapeForFillMode(null));
        assertEquals(Shape.SOLID, GhostGrids.shapeForFillMode("replace"));
        assertEquals(Shape.SOLID, GhostGrids.shapeForFillMode("replace minecraft:stone"));
        assertEquals(Shape.SOLID, GhostGrids.shapeForFillMode("keep"));
        assertEquals(Shape.SOLID, GhostGrids.shapeForFillMode("destroy"));
        assertEquals(Shape.SHELL, GhostGrids.shapeForFillMode("hollow"));
        assertEquals(Shape.SHELL, GhostGrids.shapeForFillMode("outline"));
        assertEquals(Shape.SHELL, GhostGrids.shapeForFillMode("  hollow "));
    }
}
